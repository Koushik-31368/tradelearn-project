package com.tradelearn.server.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Centralised degradation state machine that coordinates all disaster
 * recovery components.
 *
 * <h3>System States</h3>
 * <pre>
 *   NORMAL         → everything healthy
 *   DEGRADED_REDIS → Redis unavailable, local fallback active
 *   DEGRADED_DB    → Database unavailable, trades suspended
 *   FROZEN         → Critical failure, all games paused
 *   RECOVERING     → Partial recovery in progress, reconciliation running
 * </pre>
 *
 * <h3>Transition rules</h3>
 * <ul>
 *   <li>Redis down alone → DEGRADED_REDIS (games continue with local state)</li>
 *   <li>DB down alone → DEGRADED_DB (existing games freeze, no new games)</li>
 *   <li>Both down → FROZEN (everything paused)</li>
 *   <li>Recovery detected → RECOVERING (reconciliation starts)</li>
 *   <li>Reconciliation complete → NORMAL</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * {@link AtomicReference} for the state, CAS transitions. Event
 * listeners are notified synchronously in the calling thread.
 */
@Service
public class GracefulDegradationManager {

    private static final Logger log = LoggerFactory.getLogger(GracefulDegradationManager.class);

    // ===================== STATE MACHINE =====================

    public enum SystemState {
        NORMAL,
        DEGRADED_REDIS,
        DEGRADED_DB,
        FROZEN,
        RECOVERING
    }

    private final AtomicReference<SystemState> state = new AtomicReference<>(SystemState.NORMAL);

    // Track individual component health
    private volatile boolean redisHealthy = true;
    private volatile boolean databaseHealthy = true;
    private volatile long stateChangedAtMs = System.currentTimeMillis();

    // Event listeners
    private final ConcurrentHashMap<String, DegradationListener> listeners = new ConcurrentHashMap<>();

    // ===================== DEPENDENCIES =====================

    private final DatabaseFailoverHandler dbFailover;
    private final ResilientRedisRoomStore resilientRedis;
    private volatile GameFreezeService freezeService;

    public GracefulDegradationManager(DatabaseFailoverHandler dbFailover,
                                      ResilientRedisRoomStore resilientRedis) {
        this.dbFailover = dbFailover;
        this.resilientRedis = resilientRedis;
    }

    @PostConstruct
    void init() {
        // Wire bidirectional reference (breaks circular dep)
        dbFailover.setDegradationManager(this);
        log.info("[Degradation] Manager initialized — state={}", state.get());
    }

    /** Set by GameFreezeService after its construction (breaks circular dep). */
    public void setFreezeService(GameFreezeService service) {
        this.freezeService = service;
    }

    // ===================== EVENT HANDLERS =====================

    /**
     * Called by {@link ResilientRedisRoomStore} / health monitor
     * when Redis becomes unreachable.
     */
    public void onRedisUnavailable() {
        redisHealthy = false;
        SystemState oldState = state.get();
        SystemState newState = computeState();

        if (oldState != newState && state.compareAndSet(oldState, newState)) {
            stateChangedAtMs = System.currentTimeMillis();
            log.warn("[Degradation] {} → {} (Redis down)", oldState, newState);
            notifyListeners(oldState, newState);

            if (newState == SystemState.FROZEN) {
                triggerFreezeAll();
            }
        }
    }

    /**
     * Called by {@link ResilientRedisRoomStore} / health monitor
     * when Redis recovers.
     */
    public void onRedisRecovered() {
        redisHealthy = true;
        SystemState oldState = state.get();
        SystemState newState = shouldRecover() ? SystemState.RECOVERING : computeState();

        if (oldState != newState && state.compareAndSet(oldState, newState)) {
            stateChangedAtMs = System.currentTimeMillis();
            log.info("[Degradation] {} → {} (Redis recovered)", oldState, newState);
            notifyListeners(oldState, newState);
        }
    }

    /**
     * Called by {@link DatabaseFailoverHandler} when DB becomes unreachable.
     */
    public void onDatabaseUnavailable() {
        databaseHealthy = false;
        SystemState oldState = state.get();
        SystemState newState = computeState();

        if (oldState != newState && state.compareAndSet(oldState, newState)) {
            stateChangedAtMs = System.currentTimeMillis();
            log.warn("[Degradation] {} → {} (Database down)", oldState, newState);
            notifyListeners(oldState, newState);

            if (newState == SystemState.FROZEN || newState == SystemState.DEGRADED_DB) {
                triggerFreezeAll();
            }
        }
    }

    /**
     * Called by {@link DatabaseFailoverHandler} when DB recovers.
     */
    public void onDatabaseRecovered() {
        databaseHealthy = true;
        SystemState oldState = state.get();
        SystemState newState = shouldRecover() ? SystemState.RECOVERING : computeState();

        if (oldState != newState && state.compareAndSet(oldState, newState)) {
            stateChangedAtMs = System.currentTimeMillis();
            log.info("[Degradation] {} → {} (Database recovered)", oldState, newState);
            notifyListeners(oldState, newState);
        }
    }

    /**
     * Called when reconciliation completes successfully.
     */
    public void onReconciliationComplete() {
        SystemState oldState = state.get();
        SystemState newState = computeState();

        if (state.compareAndSet(oldState, newState)) {
            stateChangedAtMs = System.currentTimeMillis();
            log.info("[Degradation] {} → {} (reconciliation complete)", oldState, newState);
            notifyListeners(oldState, newState);

            if (newState == SystemState.NORMAL) {
                triggerUnfreezeAll();
            }
        }
    }

    // ===================== STATE COMPUTATION =====================

    private SystemState computeState() {
        if (!redisHealthy && !databaseHealthy) return SystemState.FROZEN;
        if (!databaseHealthy) return SystemState.DEGRADED_DB;
        if (!redisHealthy) return SystemState.DEGRADED_REDIS;
        return SystemState.NORMAL;
    }

    private boolean shouldRecover() {
        SystemState current = state.get();
        return current == SystemState.FROZEN
                || current == SystemState.DEGRADED_DB
                || current == SystemState.DEGRADED_REDIS;
    }

    private void triggerFreezeAll() {
        GameFreezeService fs = this.freezeService;
        if (fs != null) {
            fs.freezeAllGames("System degradation: " + state.get());
        }
    }

    private void triggerUnfreezeAll() {
        GameFreezeService fs = this.freezeService;
        if (fs != null) {
            fs.unfreezeAllGames();
        }
    }

    // ===================== LISTENERS =====================

    /**
     * Listener interface for state transitions.
     */
    @FunctionalInterface
    public interface DegradationListener {
        void onStateChange(SystemState oldState, SystemState newState);
    }

    public void addListener(String name, DegradationListener listener) {
        listeners.put(name, listener);
    }

    public void removeListener(String name) {
        listeners.remove(name);
    }

    private void notifyListeners(SystemState oldState, SystemState newState) {
        listeners.forEach((name, listener) -> {
            try {
                listener.onStateChange(oldState, newState);
            } catch (Exception e) {
                log.warn("[Degradation] Listener '{}' failed: {}", name, e.getMessage());
            }
        });
    }

    // ===================== QUERIES =====================

    /** Current system state. */
    public SystemState getState() {
        return state.get();
    }

    /** True if the system is fully operational. */
    public boolean isNormal() {
        return state.get() == SystemState.NORMAL;
    }

    /** True if any degradation is active. */
    public boolean isDegraded() {
        return state.get() != SystemState.NORMAL;
    }

    /** True if trades should be blocked (DB down or FROZEN). */
    public boolean areTradesBlocked() {
        SystemState s = state.get();
        return s == SystemState.DEGRADED_DB || s == SystemState.FROZEN;
    }

    /** True if new game creation should be blocked. */
    public boolean isGameCreationBlocked() {
        SystemState s = state.get();
        return s == SystemState.DEGRADED_DB || s == SystemState.FROZEN;
    }

    /** True if the system is frozen (all games paused). */
    public boolean isFrozen() {
        return state.get() == SystemState.FROZEN;
    }

    /** Milliseconds since the last state transition. */
    public long msSinceStateChange() {
        return System.currentTimeMillis() - stateChangedAtMs;
    }

    /** Diagnostic snapshot. */
    public Map<String, Object> diagnostics() {
        return Map.of(
                "state", state.get().name(),
                "redisHealthy", redisHealthy,
                "databaseHealthy", databaseHealthy,
                "stateChangedAtMs", stateChangedAtMs,
                "msSinceStateChange", msSinceStateChange(),
                "tradesBlocked", areTradesBlocked(),
                "gameCreationBlocked", isGameCreationBlocked(),
                "listenerCount", listeners.size()
        );
    }
}
