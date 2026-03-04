package com.tradelearn.server.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.TradeRepository;

/**
 * Crash recovery: runs on application startup to restore interrupted games.
 *
 * <h3>Problem</h3>
 * If the server crashes or restarts while games are ACTIVE, the in-memory
 * state (schedulers, position snapshots, rate limiter buckets, room sessions)
 * is lost. The database is the authoritative record:
 * <ul>
 *   <li>Games with status=ACTIVE need their schedulers restarted.</li>
 *   <li>Position snapshots must be rebuilt from the trade log.</li>
 *   <li>Candle state must be verified against the DB candle index.</li>
 * </ul>
 *
 * <h3>What it does on startup</h3>
 * <ol>
 *   <li>Queries DB for all games with status=ACTIVE.</li>
 *   <li>For each active game, replays all trades to rebuild position snapshots.</li>
 *   <li>Re-creates room state in RoomManager (if not already present in Redis).</li>
 *   <li>Restarts the candle scheduler via MatchSchedulerService.</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * Multiple instances starting simultaneously is safe:
 * <ul>
 *   <li>Position snapshots use put() which is idempotent.</li>
 *   <li>Room creation uses Redis SETNX (only-once guarantee).</li>
 *   <li>Scheduler start uses tryClaimScheduler (only-one-instance guarantee).</li>
 * </ul>
 *
 * <h3>Ordering</h3>
 * Runs after full Spring context initialization via {@code @EventListener(ApplicationReadyEvent)}.
 */
@Service
public class CrashRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(CrashRecoveryService.class);

    private final GameRepository gameRepository;
    private final TradeRepository tradeRepository;
    private final PositionSnapshotStore positionStore;
    private final RoomManager roomManager;
    private final MatchSchedulerService schedulerService;
    private final CandleService candleService;
    private final StringRedisTemplate redis;
    private final GracefulDegradationManager degradationManager;

    private volatile int recoveredGames = 0;
    private volatile int recoveredPositions = 0;
    private volatile long recoveryDurationMs = 0;
    private volatile String recoveryResult = "pending";

    /** Guards periodic methods from running before startup recovery completes. */
    private volatile boolean startupComplete = false;

    /** Tracks Redis availability for Phase 4 restart detection. */
    private volatile boolean redisWasDown = false;

    public CrashRecoveryService(GameRepository gameRepository,
                                TradeRepository tradeRepository,
                                PositionSnapshotStore positionStore,
                                RoomManager roomManager,
                                MatchSchedulerService schedulerService,
                                CandleService candleService,
                                StringRedisTemplate redis,
                                GracefulDegradationManager degradationManager) {
        this.gameRepository = gameRepository;
        this.tradeRepository = tradeRepository;
        this.positionStore = positionStore;
        this.roomManager = roomManager;
        this.schedulerService = schedulerService;
        this.candleService = candleService;
        this.redis = redis;
        this.degradationManager = degradationManager;
    }

    // ==================== STARTUP RECOVERY ====================

    /**
     * Runs automatically after the Spring context is fully initialized.
     * Scans for ACTIVE games and restores their runtime state.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        long start = System.currentTimeMillis();
        log.info("[CrashRecovery] Scanning for interrupted ACTIVE games...");

        try {
            List<Game> activeGames = gameRepository.findByStatus("ACTIVE");

            if (activeGames.isEmpty()) {
                recoveryResult = "OK — no active games found";
                log.info("[CrashRecovery] No active games to recover");
                return;
            }

            log.info("[CrashRecovery] Found {} ACTIVE games to recover", activeGames.size());

            for (Game game : activeGames) {
                try {
                    recoverGame(game);
                    recoveredGames++;
                } catch (Exception e) {
                    log.error("[CrashRecovery] Failed to recover game {}: {}",
                            game.getId(), e.getMessage(), e);
                }
            }

            recoveryDurationMs = System.currentTimeMillis() - start;
            recoveryResult = String.format("OK — %d games recovered, %d positions rebuilt in %dms",
                    recoveredGames, recoveredPositions, recoveryDurationMs);
            log.info("[CrashRecovery] Complete: {}", recoveryResult);

        } catch (Exception e) {
            recoveryDurationMs = System.currentTimeMillis() - start;
            recoveryResult = "FAILED: " + e.getMessage();
            log.error("[CrashRecovery] Failed: {}", e.getMessage(), e);
        } finally {
            startupComplete = true;
        }
    }

    // ==================== PHASE 3: PERIODIC SCHEDULER RECOVERY ====================

    /**
     * Every 10 seconds, scan for ACTIVE games whose scheduler has no owner.
     * This catches games orphaned by a crashed instance whose Redis ownership TTL
     * has expired but no other instance has claimed the scheduler yet.
     *
     * <p>Safe across N instances: {@code tryClaimScheduler} is Redis SETNX —
     * only one instance wins the claim. Others see the key and skip.</p>
     */
    @Scheduled(fixedDelay = 10_000)
    public void periodicSchedulerRecovery() {
        if (!startupComplete) return;

        try {
            List<Game> activeGames = gameRepository.findByStatus("ACTIVE");
            int recovered = 0;

            for (Game game : activeGames) {
                long gameId = game.getId();

                // Skip if this instance already runs the scheduler
                if (schedulerService.isRunning(gameId)) continue;

                // Skip if another instance owns the scheduler (Redis key exists)
                if (roomManager.hasSchedulerOwner(gameId)) continue;

                // Orphaned game — attempt full recovery
                try {
                    recoverGame(game);
                    recovered++;
                    log.info("[Recovery] Recovered orphaned game {} (scheduler had no owner)", gameId);
                } catch (Exception e) {
                    log.warn("[Recovery] Failed to recover game {}: {}", gameId, e.getMessage());
                }
            }

            if (recovered > 0) {
                log.info("[Recovery] Periodic scan recovered {} orphaned games", recovered);
            }
        } catch (Exception e) {
            log.debug("[Recovery] Periodic scan error: {}", e.getMessage());
        }
    }

    // ==================== PHASE 4: REDIS RESTART HARDENING ====================

    /**
     * Probes Redis every 5 seconds. When Redis transitions from DOWN → UP,
     * triggers a full recovery: room state rebuild + scheduler restart for all
     * ACTIVE games. Also notifies {@link GracefulDegradationManager} so the
     * system can transition back to NORMAL mode from DEGRADED.
     *
     * <p>On Redis failure, notifies the degradation manager to enter DEGRADED
     * mode and activate local fallback paths.</p>
     */
    @Scheduled(fixedDelay = 5_000)
    public void probeRedis() {
        if (!startupComplete) return;

        try {
            redis.opsForValue().get("tl:health:probe");

            // Redis is healthy
            if (redisWasDown) {
                redisWasDown = false;
                log.info("[Recovery] Redis connection restored — triggering full recovery");
                degradationManager.onRedisRecovered();
                onRedisRestored();
            }
        } catch (Exception e) {
            if (!redisWasDown) {
                redisWasDown = true;
                log.warn("[Recovery] Redis unavailable: {}", e.getMessage());
                degradationManager.onRedisUnavailable();
            }
        }
    }

    /**
     * Called when Redis recovers after an outage. Rebuilds room state and
     * restarts schedulers for ALL active games since all Redis keys were
     * likely lost during the restart.
     */
    private void onRedisRestored() {
        try {
            List<Game> activeGames = gameRepository.findByStatus("ACTIVE");
            int recovered = 0;

            for (Game game : activeGames) {
                try {
                    recoverGame(game);
                    recovered++;
                } catch (Exception e) {
                    log.warn("[Recovery] Redis restore — failed to recover game {}: {}",
                            game.getId(), e.getMessage());
                }
            }

            log.info("[Recovery] Redis restore complete: recovered {}/{} active games",
                    recovered, activeGames.size());
        } catch (Exception e) {
            log.error("[Recovery] Redis restore failed: {}", e.getMessage(), e);
        }
    }

    // ==================== PER-GAME RECOVERY ====================

    private void recoverGame(Game game) {
        long gameId = game.getId();
        log.info("[CrashRecovery] Recovering game {} (candle {}/{})",
                gameId, game.getCurrentCandleIndex(), game.getTotalCandles());

        // ── 1. Ensure room exists ──
        if (!roomManager.hasRoom(gameId)) {
            roomManager.createRoom(gameId, game.getCreator().getId());
            if (game.getOpponent() != null) {
                try {
                    roomManager.joinRoom(gameId, game.getOpponent().getId());
                } catch (IllegalStateException e) {
                    // Room might already be full from Redis (another instance recovered it)
                    log.debug("[CrashRecovery] Room {} join skipped: {}", gameId, e.getMessage());
                }
            }
        }

        // ── 2. Rebuild position snapshots from trade log ──
        rebuildPositions(game);

        // ── 3. Ensure candle data is loaded ──
        try {
            candleService.getCurrentCandle(gameId);
        } catch (Exception e) {
            log.warn("[CrashRecovery] Candle data not available for game {}: {}", gameId, e.getMessage());
        }

        // ── 4. Restart candle scheduler ──
        // tryClaimScheduler inside startProgression ensures only one instance does this.
        if (!schedulerService.isRunning(gameId)) {
            schedulerService.startProgression(gameId);
            log.info("[CrashRecovery] Scheduler restarted for game {}", gameId);
        } else {
            log.debug("[CrashRecovery] Scheduler already running for game {}", gameId);
        }
    }

    // ==================== POSITION REBUILD ====================

    /**
     * Replay all trades for both players to rebuild position snapshots.
     * This is the same logic as MatchTradeService.calculatePosition but
     * uses the PositionSnapshotStore directly to avoid circular deps.
     */
    private void rebuildPositions(Game game) {
        long gameId = game.getId();
        double startingBalance = game.getStartingBalance();

        // Creator
        rebuildPlayerPosition(gameId, game.getCreator().getId(), startingBalance);

        // Opponent
        if (game.getOpponent() != null) {
            rebuildPlayerPosition(gameId, game.getOpponent().getId(), startingBalance);
        }
    }

    private void rebuildPlayerPosition(long gameId, long userId, double startingBalance) {
        if (positionStore.hasPosition(gameId, userId)) {
            log.debug("[CrashRecovery] Position already exists for game={} user={}", gameId, userId);
            return;
        }

        // Initialize with starting balance
        positionStore.initializePosition(gameId, userId, startingBalance);

        // Replay all trades
        List<Trade> trades = tradeRepository.findByGameIdAndUserId(gameId, userId);
        if (trades.isEmpty()) {
            log.debug("[CrashRecovery] No trades to replay for game={} user={}", gameId, userId);
            recoveredPositions++;
            return;
        }

        for (Trade trade : trades) {
            positionStore.applyTrade(
                    gameId, userId,
                    trade.getType(),
                    trade.getSymbol(),
                    trade.getQuantity(),
                    trade.getPrice()
            );
        }

        recoveredPositions++;
        log.info("[CrashRecovery] Rebuilt position for game={} user={} ({} trades replayed)",
                gameId, userId, trades.size());
    }

    // ==================== QUERIES ====================

    /** Number of games recovered on this startup. */
    public int getRecoveredGames() { return recoveredGames; }

    /** Number of position snapshots rebuilt. */
    public int getRecoveredPositions() { return recoveredPositions; }

    /** Recovery duration in milliseconds. */
    public long getRecoveryDurationMs() { return recoveryDurationMs; }

    /** Diagnostic snapshot. */
    public Map<String, Object> diagnostics() {
        return Map.of(
                "recoveredGames", recoveredGames,
                "recoveredPositions", recoveredPositions,
                "recoveryDurationMs", recoveryDurationMs,
                "result", recoveryResult
        );
    }
}
