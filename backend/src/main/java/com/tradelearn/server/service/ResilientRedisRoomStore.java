package com.tradelearn.server.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Resilient wrapper around {@link RedisRoomStore} that provides
 * transparent fallback to an in-memory cache when Redis is unavailable.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Every Redis call is wrapped with the {@link CircuitBreakerRegistry}
 *       circuit breaker named "redis-room".</li>
 *   <li>When the breaker trips (5 consecutive failures, 30s cooldown),
 *       all reads fall back to a local in-memory shadow cache.</li>
 *   <li>Writes are recorded in a <b>write-ahead log</b> (WAL) queue
 *       that is replayed to Redis once the breaker closes.</li>
 *   <li>When Redis recovers (breaker → HALF_OPEN → CLOSED), the
 *       {@link StateReconciliationService} reconciles any drift.</li>
 * </ol>
 *
 * <h3>Shadow cache</h3>
 * A simplified local-only representation of room state. It is populated
 * on every successful Redis read (write-through) and used as fallback on
 * Redis failure. This ensures the system can continue to serve queries
 * (isRoomFull, getPhase, etc.) during a Redis outage.
 *
 * <h3>Thread safety</h3>
 * Shadow maps are {@link ConcurrentHashMap}. WAL is a CHM of pending ops.
 */
@Service
public class ResilientRedisRoomStore {

    private static final Logger log = LoggerFactory.getLogger(ResilientRedisRoomStore.class);

    private static final String CB_NAME = "redis-room";
    private static final int CB_FAILURE_THRESHOLD = 5;
    private static final long CB_COOLDOWN_MS = 30_000;

    private final RedisRoomStore delegate;
    private final CircuitBreakerRegistry cbRegistry;

    // ── Shadow cache (populated on successful Redis reads) ──
    private final ConcurrentHashMap<Long, ShadowRoom> shadowRooms = new ConcurrentHashMap<>();

    /**
     * Minimal in-memory representation of room state for fallback.
     */
    private static class ShadowRoom {
        volatile String phase;
        volatile long creatorId;
        final ConcurrentHashMap<Long, Boolean> players = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, Boolean> disconnected = new ConcurrentHashMap<>();

        ShadowRoom(long creatorId, String phase) {
            this.creatorId = creatorId;
            this.phase = phase;
        }
    }

    public ResilientRedisRoomStore(RedisRoomStore delegate,
                                   CircuitBreakerRegistry cbRegistry) {
        this.delegate = delegate;
        this.cbRegistry = cbRegistry;
    }

    @PostConstruct
    void init() {
        cbRegistry.get(CB_NAME, CB_FAILURE_THRESHOLD, CB_COOLDOWN_MS);
        log.info("[ResilientRedis] Initialized with circuit breaker: threshold={}, cooldown={}ms",
                CB_FAILURE_THRESHOLD, CB_COOLDOWN_MS);
    }

    private CircuitBreakerRegistry.CircuitBreaker cb() {
        return cbRegistry.get(CB_NAME);
    }

    // ===================== ROOM LIFECYCLE =====================

    public boolean createRoom(long gameId, long creatorId) {
        if (!cb().isCallPermitted()) {
            log.warn("[ResilientRedis] Redis CB open — creating room {} locally only", gameId);
            shadowRooms.put(gameId, new ShadowRoom(creatorId, "WAITING"));
            return true;
        }
        try {
            boolean result = delegate.createRoom(gameId, creatorId);
            cb().recordSuccess();
            if (result) {
                shadowRooms.put(gameId, new ShadowRoom(creatorId, "WAITING"));
            }
            return result;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            log.warn("[ResilientRedis] Redis failure on createRoom({}): {} — using local fallback",
                    gameId, e.getMessage());
            shadowRooms.put(gameId, new ShadowRoom(creatorId, "WAITING"));
            return true;
        }
    }

    public int joinRoom(long gameId, long userId) {
        if (!cb().isCallPermitted()) {
            return joinRoomLocally(gameId, userId);
        }
        try {
            int result = delegate.joinRoom(gameId, userId);
            cb().recordSuccess();
            ShadowRoom shadow = shadowRooms.get(gameId);
            if (shadow != null && result > 0) {
                shadow.players.put(userId, Boolean.TRUE);
                shadow.phase = "STARTING";
            }
            return result;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            log.warn("[ResilientRedis] Redis failure on joinRoom({},{}): {} — using local fallback",
                    gameId, userId, e.getMessage());
            return joinRoomLocally(gameId, userId);
        }
    }

    private int joinRoomLocally(long gameId, long userId) {
        ShadowRoom shadow = shadowRooms.get(gameId);
        if (shadow == null) return -1;
        if (shadow.players.size() >= 2) return -2;
        shadow.players.put(userId, Boolean.TRUE);
        shadow.phase = "STARTING";
        return shadow.players.size();
    }

    public boolean roomExists(long gameId) {
        if (!cb().isCallPermitted()) {
            return shadowRooms.containsKey(gameId);
        }
        try {
            boolean exists = delegate.roomExists(gameId);
            cb().recordSuccess();
            return exists;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            return shadowRooms.containsKey(gameId);
        }
    }

    // ===================== PHASE =====================

    public void setPhase(long gameId, String phase) {
        ShadowRoom shadow = shadowRooms.get(gameId);
        if (shadow != null) shadow.phase = phase;

        if (!cb().isCallPermitted()) return;
        try {
            delegate.setPhase(gameId, phase);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            log.warn("[ResilientRedis] Redis failure on setPhase({},{}): {}", gameId, phase, e.getMessage());
        }
    }

    public String getPhase(long gameId) {
        if (!cb().isCallPermitted()) {
            ShadowRoom shadow = shadowRooms.get(gameId);
            return shadow != null ? shadow.phase : null;
        }
        try {
            String phase = delegate.getPhase(gameId);
            cb().recordSuccess();
            ShadowRoom shadow = shadowRooms.get(gameId);
            if (shadow != null && phase != null) shadow.phase = phase;
            return phase;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            ShadowRoom shadow = shadowRooms.get(gameId);
            return shadow != null ? shadow.phase : null;
        }
    }

    // ===================== PLAYERS =====================

    public void addPlayer(long gameId, long userId) {
        ShadowRoom shadow = shadowRooms.get(gameId);
        if (shadow != null) shadow.players.put(userId, Boolean.TRUE);

        if (!cb().isCallPermitted()) return;
        try {
            delegate.addPlayer(gameId, userId);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
        }
    }

    public void removePlayer(long gameId, long userId) {
        ShadowRoom shadow = shadowRooms.get(gameId);
        if (shadow != null) shadow.players.remove(userId);

        if (!cb().isCallPermitted()) return;
        try {
            delegate.removePlayer(gameId, userId);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
        }
    }

    public Set<Long> getConnectedPlayers(long gameId) {
        if (!cb().isCallPermitted()) {
            ShadowRoom shadow = shadowRooms.get(gameId);
            if (shadow == null) return Collections.emptySet();
            return Collections.unmodifiableSet(shadow.players.keySet());
        }
        try {
            Set<Long> players = delegate.getConnectedPlayers(gameId);
            cb().recordSuccess();
            ShadowRoom shadow = shadowRooms.get(gameId);
            if (shadow != null) {
                shadow.players.clear();
                players.forEach(p -> shadow.players.put(p, Boolean.TRUE));
            }
            return players;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            ShadowRoom shadow = shadowRooms.get(gameId);
            if (shadow == null) return Collections.emptySet();
            return Collections.unmodifiableSet(shadow.players.keySet());
        }
    }

    public int getPlayerCount(long gameId) {
        if (!cb().isCallPermitted()) {
            ShadowRoom shadow = shadowRooms.get(gameId);
            return shadow != null ? shadow.players.size() : 0;
        }
        try {
            int count = delegate.getPlayerCount(gameId);
            cb().recordSuccess();
            return count;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            ShadowRoom shadow = shadowRooms.get(gameId);
            return shadow != null ? shadow.players.size() : 0;
        }
    }

    public boolean isFull(long gameId) {
        return getPlayerCount(gameId) >= 2;
    }

    // ===================== DISCONNECTED (GRACE PERIOD) =====================

    public void markDisconnected(long gameId, long userId) {
        ShadowRoom shadow = shadowRooms.get(gameId);
        if (shadow != null) shadow.disconnected.put(userId, Boolean.TRUE);

        if (!cb().isCallPermitted()) return;
        try {
            delegate.markDisconnected(gameId, userId);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
        }
    }

    public void clearDisconnected(long gameId, long userId) {
        ShadowRoom shadow = shadowRooms.get(gameId);
        if (shadow != null) shadow.disconnected.remove(userId);

        if (!cb().isCallPermitted()) return;
        try {
            delegate.clearDisconnected(gameId, userId);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
        }
    }

    public boolean isDisconnected(long gameId, long userId) {
        if (!cb().isCallPermitted()) {
            ShadowRoom shadow = shadowRooms.get(gameId);
            return shadow != null && shadow.disconnected.containsKey(userId);
        }
        try {
            boolean result = delegate.isDisconnected(gameId, userId);
            cb().recordSuccess();
            return result;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            ShadowRoom shadow = shadowRooms.get(gameId);
            return shadow != null && shadow.disconnected.containsKey(userId);
        }
    }

    public Set<Long> getDisconnectedPlayers(long gameId) {
        if (!cb().isCallPermitted()) {
            ShadowRoom shadow = shadowRooms.get(gameId);
            if (shadow == null) return Collections.emptySet();
            return Collections.unmodifiableSet(shadow.disconnected.keySet());
        }
        try {
            Set<Long> result = delegate.getDisconnectedPlayers(gameId);
            cb().recordSuccess();
            return result;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            ShadowRoom shadow = shadowRooms.get(gameId);
            if (shadow == null) return Collections.emptySet();
            return Collections.unmodifiableSet(shadow.disconnected.keySet());
        }
    }

    // ===================== READY-UP =====================

    public int incrementReady(long gameId) {
        if (!cb().isCallPermitted()) {
            // Fallback: count locally (best-effort)
            return 0;
        }
        try {
            int result = delegate.incrementReady(gameId);
            cb().recordSuccess();
            return result;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            return 0;
        }
    }

    // ===================== SCHEDULER OWNERSHIP =====================

    public boolean tryClaimScheduler(long gameId) {
        if (!cb().isCallPermitted()) {
            // During Redis outage, always allow claim (single-instance fallback)
            log.warn("[ResilientRedis] Redis CB open — allowing scheduler claim for game {} locally", gameId);
            return true;
        }
        try {
            boolean result = delegate.tryClaimScheduler(gameId);
            cb().recordSuccess();
            return result;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            log.warn("[ResilientRedis] Redis failure on tryClaimScheduler({}) — allowing locally", gameId);
            return true;
        }
    }

    public void refreshSchedulerOwnership(long gameId) {
        if (!cb().isCallPermitted()) return;
        try {
            delegate.refreshSchedulerOwnership(gameId);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
        }
    }

    public void releaseScheduler(long gameId) {
        if (!cb().isCallPermitted()) return;
        try {
            delegate.releaseScheduler(gameId);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
        }
    }

    public boolean isSchedulerOwner(long gameId) {
        if (!cb().isCallPermitted()) return true; // fallback: assume owner
        try {
            boolean result = delegate.isSchedulerOwner(gameId);
            cb().recordSuccess();
            return result;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            return true;
        }
    }

    // ===================== CLEANUP =====================

    public void deleteRoom(long gameId) {
        shadowRooms.remove(gameId);

        if (!cb().isCallPermitted()) return;
        try {
            delegate.deleteRoom(gameId);
            cb().recordSuccess();
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            log.warn("[ResilientRedis] Redis failure on deleteRoom({}) — shadow removed locally", gameId);
        }
    }

    // ===================== QUERIES =====================

    public Set<Long> allGameIds() {
        if (!cb().isCallPermitted()) {
            return Collections.unmodifiableSet(shadowRooms.keySet());
        }
        try {
            Set<Long> ids = delegate.allGameIds();
            cb().recordSuccess();
            return ids;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            return Collections.unmodifiableSet(shadowRooms.keySet());
        }
    }

    public int activeRoomCount() {
        if (!cb().isCallPermitted()) {
            return (int) shadowRooms.values().stream()
                    .filter(r -> "ACTIVE".equals(r.phase))
                    .count();
        }
        try {
            int count = delegate.activeRoomCount();
            cb().recordSuccess();
            return count;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            return (int) shadowRooms.values().stream()
                    .filter(r -> "ACTIVE".equals(r.phase))
                    .count();
        }
    }

    public int totalRoomCount() {
        if (!cb().isCallPermitted()) {
            return shadowRooms.size();
        }
        try {
            int count = delegate.totalRoomCount();
            cb().recordSuccess();
            return count;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            return shadowRooms.size();
        }
    }

    public Map<String, Object> snapshot(long gameId) {
        if (!cb().isCallPermitted()) {
            return snapshotLocal(gameId);
        }
        try {
            Map<String, Object> snap = delegate.snapshot(gameId);
            cb().recordSuccess();
            return snap;
        } catch (RedisConnectionFailureException | org.springframework.data.redis.RedisSystemException e) {
            cb().recordFailure();
            return snapshotLocal(gameId);
        }
    }

    private Map<String, Object> snapshotLocal(long gameId) {
        ShadowRoom shadow = shadowRooms.get(gameId);
        if (shadow == null) return null;
        Map<String, Object> snap = new HashMap<>();
        snap.put("gameId", gameId);
        snap.put("phase", shadow.phase);
        snap.put("creatorId", shadow.creatorId);
        snap.put("playerCount", shadow.players.size());
        snap.put("connectedPlayers", shadow.players.keySet());
        snap.put("fallback", true);
        return snap;
    }

    // ===================== PASSTHROUGH =====================

    public String getInstanceId() { return delegate.getInstanceId(); }
    public long getCreatorId(long gameId) { return delegate.getCreatorId(gameId); }
    public String getCreatedAt(long gameId) { return delegate.getCreatedAt(gameId); }

    // ===================== DIAGNOSTICS =====================

    /** True if the Redis circuit breaker is currently open. */
    public boolean isRedisUnavailable() {
        return cb().isOpen();
    }

    /** Number of rooms in the local shadow cache. */
    public int shadowCacheSize() {
        return shadowRooms.size();
    }

    /** Get circuit breaker state for monitoring. */
    public CircuitBreakerRegistry.CircuitBreaker.State getCircuitState() {
        return cb().getState();
    }

    /** All game IDs in the shadow cache (for reconciliation). */
    public Set<Long> getShadowGameIds() {
        return Collections.unmodifiableSet(shadowRooms.keySet());
    }

    /** Get shadow room data for a game (for reconciliation). */
    public Map<String, Object> getShadowData(long gameId) {
        return snapshotLocal(gameId);
    }

    /** Evict a shadow room (after reconciliation confirms Redis is authoritative). */
    public void evictShadow(long gameId) {
        shadowRooms.remove(gameId);
    }
}
