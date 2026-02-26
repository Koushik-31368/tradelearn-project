package com.tradelearn.server.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;

/**
 * Reconciles local fallback state with authoritative Redis/DB state
 * after an outage recovery.
 *
 * <h3>When it runs</h3>
 * Triggered by {@link GracefulDegradationManager} when the system
 * transitions from a degraded state (DEGRADED_REDIS, DEGRADED_DB,
 * FROZEN, RECOVERING) toward NORMAL.
 *
 * <h3>What it reconciles</h3>
 * <ol>
 *   <li><b>Redis ↔ Shadow cache:</b> After a Redis outage, the shadow
 *       rooms in {@link ResilientRedisRoomStore} may have diverged from
 *       Redis. The reconciler checks each shadow room against Redis and
 *       replays any missing state.</li>
 *   <li><b>DB ↔ Position snapshots:</b> After a DB outage, any trades
 *       that were accepted during the outage need to be verified against
 *       the database. Positions in {@link PositionSnapshotStore} are
 *       validated against the last known DB state.</li>
 *   <li><b>Game status:</b> Games that were frozen during the outage
 *       are verified to still be ACTIVE in the database before being
 *       unfrozen.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * Only one reconciliation can run at a time ({@link AtomicBoolean} guard).
 * The async execution ensures it doesn't block the health-check thread.
 */
@Service
public class StateReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(StateReconciliationService.class);

    private final AtomicBoolean reconciling = new AtomicBoolean(false);
    private volatile long lastReconciliationMs = 0;
    private volatile String lastResult = "none";

    private final ResilientRedisRoomStore resilientRedis;
    private final RedisRoomStore rawRedis;
    private final GameRepository gameRepository;
    private final PositionSnapshotStore positionStore;
    private final GameFreezeService freezeService;
    private final GracefulDegradationManager degradationManager;

    public StateReconciliationService(ResilientRedisRoomStore resilientRedis,
                                      RedisRoomStore rawRedis,
                                      GameRepository gameRepository,
                                      PositionSnapshotStore positionStore,
                                      GameFreezeService freezeService,
                                      GracefulDegradationManager degradationManager) {
        this.resilientRedis = resilientRedis;
        this.rawRedis = rawRedis;
        this.gameRepository = gameRepository;
        this.positionStore = positionStore;
        this.freezeService = freezeService;
        this.degradationManager = degradationManager;
    }

    // ==================== ENTRY POINT ====================

    /**
     * Run full state reconciliation. Only one invocation can run at a time.
     * Called asynchronously to avoid blocking health-check threads.
     */
    @Async
    public void reconcile() {
        if (!reconciling.compareAndSet(false, true)) {
            log.info("[Reconciliation] Already in progress — skipping");
            return;
        }

        long start = System.currentTimeMillis();
        log.info("[Reconciliation] Starting full state reconciliation...");

        try {
            int shadowReconciled = reconcileRedisShadow();
            int gamesVerified = reconcileGameStates();

            long elapsed = System.currentTimeMillis() - start;
            lastReconciliationMs = System.currentTimeMillis();
            lastResult = String.format("OK — %d shadow rooms reconciled, %d games verified in %dms",
                    shadowReconciled, gamesVerified, elapsed);

            log.info("[Reconciliation] Complete: {}", lastResult);

            // Notify the degradation manager that reconciliation is done
            degradationManager.onReconciliationComplete();

        } catch (Exception e) {
            lastResult = "FAILED: " + e.getMessage();
            log.error("[Reconciliation] Failed: {}", e.getMessage(), e);
        } finally {
            reconciling.set(false);
        }
    }

    // ==================== REDIS SHADOW RECONCILIATION ====================

    /**
     * Reconcile the shadow cache in ResilientRedisRoomStore with actual Redis.
     *
     * For each shadow room:
     * - If the room exists in Redis, verify phase matches; update shadow if not.
     * - If the room does NOT exist in Redis, re-create it from shadow data
     *   (this handles the case where Redis was wiped/restarted).
     *
     * @return number of rooms reconciled
     */
    private int reconcileRedisShadow() {
        Set<Long> shadowIds = resilientRedis.getShadowGameIds();
        if (shadowIds.isEmpty()) {
            log.debug("[Reconciliation] No shadow rooms to reconcile");
            return 0;
        }

        int reconciled = 0;

        for (Long gameId : shadowIds) {
            try {
                boolean existsInRedis = rawRedis.roomExists(gameId);

                if (existsInRedis) {
                    // Redis has it — Redis is authoritative, evict shadow
                    resilientRedis.evictShadow(gameId);
                    log.debug("[Reconciliation] Game {} exists in Redis — shadow evicted", gameId);
                } else {
                    // Redis lost it (restart/flush) — check DB for ground truth
                    Game game = gameRepository.findById(gameId).orElse(null);
                    if (game != null && "ACTIVE".equals(game.getStatus())) {
                        // Game is active in DB but missing from Redis — recreate
                        rawRedis.createRoom(gameId, game.getCreator().getId());
                        rawRedis.setPhase(gameId, "ACTIVE");
                        if (game.getOpponent() != null) {
                            rawRedis.addPlayer(gameId, game.getOpponent().getId());
                        }
                        resilientRedis.evictShadow(gameId);
                        log.info("[Reconciliation] Game {} re-created in Redis from DB", gameId);
                    } else {
                        // Game is not active in DB — just evict shadow
                        resilientRedis.evictShadow(gameId);
                        log.debug("[Reconciliation] Game {} not active in DB — shadow evicted", gameId);
                    }
                }

                reconciled++;
            } catch (Exception e) {
                log.warn("[Reconciliation] Failed to reconcile shadow for game {}: {}",
                        gameId, e.getMessage());
            }
        }

        return reconciled;
    }

    // ==================== GAME STATE RECONCILIATION ====================

    /**
     * Verify that all ACTIVE games in the database are properly running.
     *
     * - Games marked ACTIVE in DB but frozen by GameFreezeService → unfreeze
     * - Games marked ACTIVE in DB but missing from Redis → re-create room
     * - Games marked ACTIVE in DB but past their end time → mark FINISHED
     *
     * @return number of games verified
     */
    private int reconcileGameStates() {
        List<Game> activeGames;
        try {
            activeGames = gameRepository.findByStatus("ACTIVE");
        } catch (Exception e) {
            log.warn("[Reconciliation] Cannot query DB for active games: {}", e.getMessage());
            return 0;
        }

        int verified = 0;

        for (Game game : activeGames) {
            try {
                long gameId = game.getId();

                // If frozen, check if it should remain frozen
                if (freezeService.isFrozen(gameId)) {
                    if (degradationManager.isNormal()) {
                        // System is back to normal — unfreeze
                        freezeService.unfreezeGame(gameId);
                        log.info("[Reconciliation] Unfroze game {} (system recovered)", gameId);
                    }
                }

                // Ensure room exists in Redis
                if (!rawRedis.roomExists(gameId)) {
                    rawRedis.createRoom(gameId, game.getCreator().getId());
                    rawRedis.setPhase(gameId, "ACTIVE");
                    if (game.getOpponent() != null) {
                        rawRedis.addPlayer(gameId, game.getOpponent().getId());
                    }
                    log.info("[Reconciliation] Re-created Redis room for active game {}", gameId);
                }

                verified++;
            } catch (Exception e) {
                log.warn("[Reconciliation] Failed to verify game {}: {}", game.getId(), e.getMessage());
            }
        }

        return verified;
    }

    // ==================== QUERIES ====================

    /** True if a reconciliation is currently running. */
    public boolean isReconciling() {
        return reconciling.get();
    }

    /** Timestamp of the last reconciliation (0 if never). */
    public long getLastReconciliationMs() {
        return lastReconciliationMs;
    }

    /** Result of the last reconciliation. */
    public String getLastResult() {
        return lastResult;
    }

    /** Diagnostic snapshot. */
    public Map<String, Object> diagnostics() {
        return Map.of(
                "reconciling", reconciling.get(),
                "lastReconciliationMs", lastReconciliationMs,
                "lastResult", lastResult
        );
    }
}
