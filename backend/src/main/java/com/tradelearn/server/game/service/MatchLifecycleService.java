package com.tradelearn.server.game.service;

import com.tradelearn.server.infrastructure.scheduling.GameMetricsService;
import com.tradelearn.server.infrastructure.redis.room.RoomManager;
import com.tradelearn.server.infrastructure.redis.store.PositionSnapshotStore;
import com.tradelearn.server.infrastructure.scheduling.MatchSchedulerService;
import com.tradelearn.server.market.service.CandleService;
import com.tradelearn.server.common.exception.GameNotFoundException;
import com.tradelearn.server.common.exception.InvalidGameStateException;
import com.tradelearn.server.common.exception.RoomFullException;
import com.tradelearn.server.common.util.GameLogger;
import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.GameStatus;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.websocket.GameBroadcaster;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Handles the lifecycle of a match: creation, joining, auto-matching, starting,
 * and host-cancellation.
 *
 * <p>All state-changing operations in this class use
 * {@link TransactionSynchronizationManager#registerSynchronization} to defer
 * Redis/WebSocket side effects to {@code afterCommit()}. This guarantees that
 * Redis is only mutated when we know the DB transaction has succeeded — preventing
 * stale Redis state on DB rollback (deadlock, constraint violation, etc.).
 *
 * <h3>Reconciliation (Issue 2)</h3>
 * <ul>
 *   <li><b>Inline retry:</b> Every afterCommit block uses
 *       {@link #afterCommitWithRetry} — 3 attempts with 200/500/1000 ms
 *       exponential backoff.  On exhaustion the game is transitioned to
 *       {@link GameStatus#FAILED} and both players are notified via
 *       {@code match-failed} so the frontend doesn't hang.</li>
 *   <li><b>Sweep job:</b> {@link #sweepOrphanedActiveGames()} runs every 60 s.
 *       It finds games that are ACTIVE in the DB but have no corresponding Redis
 *       room (i.e. the afterCommit never fired, e.g. after a crash), marks them
 *       FAILED, and broadcasts to both players.  This is the safety net for the
 *       gap between DB commit and the afterCommit callback.</li>
 * </ul>
 *
 * <h3>Separated concerns</h3>
 * <ul>
 *   <li>{@link MatchScoringService} — end-game scoring, ELO, abandon handling</li>
 *   <li>{@link MatchQueryService} — pure read-only queries (no side effects)</li>
 * </ul>
 */
@Service
public class MatchLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(MatchLifecycleService.class);
    private static final int MAX_PLAYERS = 2;

    /** Backoff delays (ms) for afterCommit retries: 200 ms, 500 ms, 1 000 ms. */
    private static final long[] RETRY_DELAYS_MS = {200L, 500L, 1_000L};

    /**
     * Games younger than this threshold are not swept — give the afterCommit
     * callback time to fire naturally before the sweep declares them orphaned.
     */
    private static final int SWEEP_MIN_AGE_MINUTES = 2;

    /**
     * Stock symbols used for ranked/auto-match game creation.
     * Kept here (lifecycle concern: who picks the symbol when creating a game).
     */
    static final String[] RANKED_SYMBOLS = {
        "TCS", "INFY", "RELIANCE", "HDFCBANK", "ICICIBANK",
        "WIPRO", "SBIN", "BHARTIARTL", "ITC", "KOTAKBANK",
        "LT", "AXISBANK", "HINDUNILVR", "MARUTI", "TATASTEEL"
    };

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final CandleService candleService;
    private final MatchSchedulerService matchSchedulerService;
    private final GameBroadcaster broadcaster;
    private final RoomManager roomManager;
    private final PositionSnapshotStore positionStore;
    private final GameMetricsService metrics;

    public MatchLifecycleService(GameRepository gameRepository,
                                 UserRepository userRepository,
                                 CandleService candleService,
                                 MatchSchedulerService matchSchedulerService,
                                 GameBroadcaster broadcaster,
                                 RoomManager roomManager,
                                 PositionSnapshotStore positionStore,
                                 GameMetricsService metrics) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.candleService = candleService;
        this.matchSchedulerService = matchSchedulerService;
        this.broadcaster = broadcaster;
        this.roomManager = roomManager;
        this.positionStore = positionStore;
        this.metrics = metrics;
    }

    // ==================== RETRY HELPER ====================

    /**
     * Execute {@code action} from inside an afterCommit callback, with bounded
     * exponential-backoff retries.
     *
     * <p>On success: does nothing more.
     * <p>On exhaustion: transitions the game to {@link GameStatus#FAILED},
     * notifies both players with a {@code match-failed} WebSocket event,
     * and logs an error.
     *
     * @param context  human-readable label for log lines (e.g. "joinMatch")
     * @param gameId   game being started — used for notifications and DB update
     * @param creatorId  creator's user-id — used for the failure broadcast
     * @param opponentId opponent's user-id — used for the failure broadcast
     * @param action   the Redis+candle+scheduler side-effect block to attempt
     */
    void afterCommitWithRetry(String context, long gameId,
                                      long creatorId, long opponentId,
                                      Runnable action) {
        Exception lastException = null;
        for (int attempt = 0; attempt < RETRY_DELAYS_MS.length; attempt++) {
            try {
                action.run();
                if (attempt > 0) {
                    log.info("[{}] afterCommit succeeded on attempt {} for game {}",
                            context, attempt + 1, gameId);
                }
                return;   // success
            } catch (Exception e) {
                lastException = e;
                log.warn("[{}] afterCommit attempt {}/{} failed for game {}: {}",
                        context, attempt + 1, RETRY_DELAYS_MS.length, gameId, e.getMessage());
                if (attempt < RETRY_DELAYS_MS.length - 1) {
                    try {
                        Thread.sleep(RETRY_DELAYS_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        // All retries exhausted — transition to FAILED and notify players
        GameLogger.logError(log, context + ".afterCommit", gameId, lastException, Map.of(
                "creatorId", creatorId,
                "opponentId", opponentId,
                "impact", "DB committed ACTIVE but all side-effect retries failed — marking FAILED"
        ));
        markGameFailed(gameId, creatorId, opponentId, context);
    }

    /**
     * Transition a game to FAILED in the DB and broadcast {@code match-failed}
     * to both players.  Runs in a NEW transaction so it is independent of any
     * surrounding context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGameFailed(long gameId, long creatorId, long opponentId, String context) {
        try {
            gameRepository.findById(gameId).ifPresent(g -> {
                if (GameStatus.ACTIVE.equals(g.getStatus())) {
                    g.setStatus(GameStatus.FAILED);
                    gameRepository.save(g);
                    log.error("[{}] Game {} marked FAILED after afterCommit exhaustion", context, gameId);
                }
            });
        } catch (Exception e) {
            log.error("[{}] Could not mark game {} FAILED in DB: {}", context, gameId, e.getMessage());
        }

        // Notify players regardless of whether the DB update succeeded
        try {
            Map<String, Object> payload = Map.of(
                    "gameId", gameId,
                    "status", "FAILED",
                    "reason", "Server failed to initialize the match after multiple attempts. Please try again."
            );
            broadcaster.sendToGame(gameId, "match-failed", payload);
        } catch (Exception e) {
            log.error("[{}] Could not broadcast match-failed for game {}: {}", context, gameId, e.getMessage());
        }
    }

    // ==================== CREATE CUSTOM MATCH ====================

    /**
     * Create a new custom lobby game. The game starts in WAITING status
     * until a second player joins via {@link #joinMatch}.
     *
     * @param request contains creator ID, stock symbol, duration, starting balance
     * @return the newly created game (WAITING status)
     */
    @SuppressWarnings("null")
    @Transactional
    public Game createMatch(CreateMatchRequest request) {
        User creator = userRepository.findById(request.getCreatorId())
                .orElseThrow(() -> new IllegalArgumentException("Creator not found"));

        Game game = new Game();
        game.setCreator(creator);
        game.setStockSymbol(request.getStockSymbol());
        game.setDurationMinutes(request.getDurationMinutes());
        game.setStartingBalance(request.getStartingBalance());
        game.setStatus(GameStatus.WAITING);
        game.setCreatedAt(java.sql.Timestamp.valueOf(LocalDateTime.now()));

        Game saved = gameRepository.save(game);

        // Create in-memory room (no Redis side effects — WAITING games don't need scheduler)
        roomManager.createRoom(saved.getId(), creator.getId());

        return saved;
    }

    // ==================== JOIN MATCH ====================

    /**
     * Join an open match. Performs an atomic CAS transition WAITING → ACTIVE
     * in the DB, then schedules all side effects (Redis room, candles,
     * scheduler, WebSocket broadcast) to run AFTER the DB transaction commits.
     *
     * <h3>Why afterCommit?</h3>
     * Redis/WebSocket operations are not part of the DB transaction. If they
     * ran inside the TX boundary and the DB commit failed (deadlock, lock
     * timeout), Redis would already be mutated — creating an inconsistency
     * that is very hard to detect and repair. By deferring side effects to
     * afterCommit, we guarantee that Redis/WebSocket only fire when we KNOW
     * the DB change is durable.
     *
     * <h3>Resilience (Issue 2)</h3>
     * The afterCommit block uses {@link #afterCommitWithRetry} with 3 attempts
     * and exponential backoff.  On exhaustion the game is marked FAILED and
     * both players are notified via {@code match-failed}.
     */
    @SuppressWarnings("null")
    @Transactional
    public Game joinMatch(long gameId, Long userId) {
        User opponent = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // ── Fast in-memory guard: check room capacity before hitting DB ──
        if (roomManager.hasRoom(gameId) && roomManager.isRoomFull(gameId)) {
            int count = roomManager.getPlayerCount(gameId);
            GameLogger.logGameCannotStart(log, gameId, "Room is full", Map.of(
                "currentPlayers", count,
                "maxPlayers", MAX_PLAYERS,
                "attemptingUserId", userId
            ));
            throw new RoomFullException(gameId, count, MAX_PLAYERS);
        }

        boolean roomExists = roomManager.hasRoom(gameId);
        int roomSize = roomExists ? roomManager.getPlayerCount(gameId) : 0;
        RoomManager.RoomPhase roomPhase = roomExists ? roomManager.getPhase(gameId) : null;

        GameLogger.logDiagnosticSnapshot(log, "Attempting Join", Map.of(
            "gameId", gameId,
            "userId", userId,
            "username", opponent.getUsername(),
            "roomExists", roomExists,
            "roomSize", roomSize,
            "roomPhase", roomPhase != null ? roomPhase.name() : "NO_ROOM"
        ));

        // ── Phase 1: Atomic compare-and-swap WAITING → ACTIVE ──
        int updated = gameRepository.atomicJoin(gameId, opponent);
        if (updated == 0) {
            GameLogger.logGameCannotStart(log, gameId, "Game is not open for joining", Map.of(
                "reason", "atomicJoin returned 0 - either already taken or not found",
                "userId", userId
            ));
            throw new InvalidGameStateException(gameId, "UNKNOWN", "WAITING",
                "Game is not open for joining (already taken or not found)");
        }

        // ── Phase 2: Re-read with pessimistic lock for validation ──
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId, "Game not found after join"));

        // Guard: can't join your own game — TX rolls back atomicJoin automatically
        if (game.getCreator().getId().equals(opponent.getId())) {
            GameLogger.logGameCannotStart(log, gameId, "Cannot join your own game", Map.of(
                "creatorId", game.getCreator().getId(),
                "attemptingUserId", userId
            ));
            throw new IllegalArgumentException("Cannot join your own game");
        }

        GameLogger.logDiagnosticSnapshot(log, "Before Auto-Start", Map.of(
            "gameId", gameId,
            "creatorId", game.getCreator().getId(),
            "opponentId", opponent.getId(),
            "status", game.getStatus(),
            "stockSymbol", game.getStockSymbol(),
            "totalCandles", game.getTotalCandles()
        ));

        // ── Phase 3: Register afterCommit side effects (with retry) ──
        final long creatorId = game.getCreator().getId();
        final double startingBalance = game.getStartingBalance();
        final Long opponentId = opponent.getId();
        final String opponentUsername = opponent.getUsername();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommitWithRetry("joinMatch", gameId, creatorId, opponentId, () -> {
                    roomManager.joinRoom(gameId, opponentId);
                    candleService.loadCandles(gameId);
                    matchSchedulerService.startProgression(gameId);
                    positionStore.initializePosition(gameId, creatorId, startingBalance);
                    positionStore.initializePosition(gameId, opponentId, startingBalance);

                    GameLogger.logGameStarted(log, gameId, creatorId, opponentId);

                    broadcaster.sendToGame(gameId, "started",
                            Map.of(
                                    "gameId", gameId,
                                    "status", "ACTIVE",
                                    "opponentId", opponentId,
                                    "opponentUsername", opponentUsername
                            )
                    );

                    GameLogger.logDiagnosticSnapshot(log, "Join Complete (afterCommit)", Map.of(
                        "gameId", gameId,
                        "creatorId", creatorId,
                        "opponentId", opponentId,
                        "status", "ACTIVE",
                        "candlesLoaded", true,
                        "schedulerStarted", true
                    ));
                });
            }
        });

        return game;
    }

    // ==================== AUTO MATCH (Matchmaking) ====================

    /**
     * Create a ranked auto-match between two players already paired by
     * the matchmaking engine. The game is immediately ACTIVE with a random
     * stock symbol, 5-minute duration, and ₹10,00,000 starting balance.
     *
     * <p>Called only by {@link com.tradelearn.server.matchmaking.service.MatchmakingService}
     * after distributed lock + Lua pair-removal succeeds.
     *
     * @param userId1 first player's ID (lower of the two)
     * @param userId2 second player's ID
     * @return the created ACTIVE game
     */
    @Transactional
    public Game createAutoMatch(long userId1, long userId2) {
        User player1 = userRepository.findById(userId1)
                .orElseThrow(() -> new IllegalArgumentException("Player 1 not found: " + userId1));
        User player2 = userRepository.findById(userId2)
                .orElseThrow(() -> new IllegalArgumentException("Player 2 not found: " + userId2));

        String symbol = RANKED_SYMBOLS[ThreadLocalRandom.current().nextInt(RANKED_SYMBOLS.length)];

        Game game = new Game();
        game.setCreator(player1);
        game.setOpponent(player2);
        game.setStockSymbol(symbol);
        game.setDurationMinutes(5);
        game.setStartingBalance(1_000_000.0);
        game.setStatus(GameStatus.ACTIVE);
        game.setStartTime(LocalDateTime.now());

        Game saved = gameRepository.save(game);
        final long gameId = saved.getId();
        final long p1Id = player1.getId();
        final long p2Id = player2.getId();
        final String p1Name = player1.getUsername();
        final String p2Name = player2.getUsername();
        final int p1Rating = player1.getRating();
        final int p2Rating = player2.getRating();
        final double startBal = saved.getStartingBalance();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommitWithRetry("createAutoMatch", gameId, p1Id, p2Id, () -> {
                    roomManager.createRoom(gameId, p1Id);
                    roomManager.joinRoom(gameId, p2Id);

                    candleService.loadCandles(gameId);
                    matchSchedulerService.startProgression(gameId);

                    positionStore.initializePosition(gameId, p1Id, startBal);
                    positionStore.initializePosition(gameId, p2Id, startBal);

                    GameLogger.logDiagnosticSnapshot(log, "Auto-Match Created", Map.of(
                        "gameId", gameId,
                        "player1", p1Name + " (" + p1Rating + ")",
                        "player2", p2Name + " (" + p2Rating + ")",
                        "stockSymbol", symbol,
                        "status", "ACTIVE"
                    ));
                });
            }
        });

        metrics.recordMatchCreated();

        return saved;
    }

    // ==================== START MATCH ====================

    /**
     * Explicitly start a match that is already ACTIVE but whose candle
     * progression hasn't been initialised yet (edge case: host-side startMatch call).
     *
     * <p>In the normal flow, candles are started in {@link #joinMatch}'s afterCommit.
     * This method handles the case where the scheduler needs to be explicitly
     * triggered again (e.g. after crash recovery).
     */
    @Transactional
    public Game startMatch(long gameId) {
        GameLogger.setGameContext(gameId);
        try {
            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new GameNotFoundException(gameId));

            User opponent = game.getOpponent();
            User creator = game.getCreator();
            long creatorIdForLog = creator != null ? creator.getId() : -1L;
            GameLogger.logGameStartAttempt(log, gameId,
                creatorIdForLog,
                opponent != null ? opponent.getId() : null,
                game.getStatus());

            if (!GameStatus.ACTIVE.equals(game.getStatus())) {
                GameLogger.logGameCannotStart(log, gameId, "Game is not ACTIVE", Map.of(
                    "currentStatus", game.getStatus(),
                    "expectedStatus", "ACTIVE"
                ));
                throw new InvalidGameStateException(gameId, game.getStatus().name(), "ACTIVE");
            }

            if (opponent == null) {
                GameLogger.logGameCannotStart(log, gameId, "No opponent", Map.of(
                    "creatorId", creator != null ? creator.getId() : Long.valueOf(-1L),
                    "hasOpponent", false
                ));
                throw new IllegalStateException("Game needs two players to start");
            }

            if (creator == null) {
                throw new IllegalStateException("Game has no creator");
            }

            game.setStartTime(LocalDateTime.now());
            Game saved = gameRepository.save(game);

            final long cId = creator.getId();
            final long oId = opponent.getId();
            final double startBal = game.getStartingBalance();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommitWithRetry("startMatch", gameId, cId, oId, () -> {
                        candleService.loadCandles(gameId);
                        matchSchedulerService.startProgression(gameId);
                        positionStore.initializePosition(gameId, cId, startBal);
                        positionStore.initializePosition(gameId, oId, startBal);
                        GameLogger.logGameStarted(log, gameId, cId, oId);
                        GameLogger.logDiagnosticSnapshot(log, "Match Started", Map.of(
                            "gameId", gameId,
                            "creatorId", cId,
                            "opponentId", oId,
                            "status", "ACTIVE",
                            "candlesLoaded", true
                        ));
                    });
                }
            });

            return saved;
        } catch (RuntimeException e) {
            GameLogger.logError(log, "startMatch", gameId, e, null);
            throw e;
        } finally {
            GameLogger.clearContext();
        }
    }

    // ==================== SWEEP JOB (safety net) ====================

    /**
     * Scheduled sweep that finds ACTIVE games with no corresponding Redis room
     * and marks them FAILED.
     *
     * <h3>What it catches</h3>
     * The afterCommit callback can be silently skipped if the JVM crashes between
     * the DB commit and the callback.  In that case the game is durable in DB as
     * ACTIVE but Redis has no room and no scheduler — the frontend hangs waiting
     * for candles that will never arrive.
     *
     * <h3>Safety threshold</h3>
     * Only games older than {@value #SWEEP_MIN_AGE_MINUTES} minutes are considered
     * orphaned — this gives legitimate afterCommit callbacks time to run.
     */
    @Scheduled(fixedDelayString = "${tradelearn.sweep.interval-ms:60000}",
               initialDelayString = "${tradelearn.sweep.initial-delay-ms:30000}")
    public void sweepOrphanedActiveGames() {
        List<Game> activeGames = gameRepository.findByStatus(GameStatus.ACTIVE);
        if (activeGames.isEmpty()) return;

        LocalDateTime ageThreshold = LocalDateTime.now().minusMinutes(SWEEP_MIN_AGE_MINUTES);
        int swept = 0;

        for (Game game : activeGames) {
            long gId = game.getId();
            try {
                // Skip games that are too young — give afterCommit time to run
                if (game.getStartTime() != null && game.getStartTime().isAfter(ageThreshold)) {
                    continue;
                }
                // Skip if createdAt is too recent (startTime may be null for edge cases)
                if (game.getStartTime() == null && game.getCreatedAt() != null) {
                    LocalDateTime created = game.getCreatedAt().toLocalDateTime();
                    if (created.isAfter(ageThreshold)) continue;
                }

                // If the Redis room exists, the game is running normally — skip
                if (roomManager.hasRoom(gId)) continue;

                long creatorId  = game.getCreator()  != null ? game.getCreator().getId()  : -1L;
                long opponentId = game.getOpponent() != null ? game.getOpponent().getId() : -1L;

                log.warn("[Sweep] Game {} is ACTIVE in DB but has no Redis room — marking FAILED " +
                         "(creator={}, opponent={})", gId, creatorId, opponentId);

                markGameFailed(gId, creatorId, opponentId, "sweep");
                swept++;

            } catch (Exception e) {
                log.error("[Sweep] Error processing game {}: {}", gId, e.getMessage());
            }
        }

        if (swept > 0) {
            log.warn("[Sweep] Marked {} orphaned ACTIVE game(s) as FAILED", swept);
        } else {
            log.debug("[Sweep] No orphaned games found ({} ACTIVE games checked)", activeGames.size());
        }
    }

    // ==================== DELETE GAME (host cancel) ====================

    /**
     * Cancel / delete a WAITING game created by the given user.
     *
     * <p>Rules enforced:
     * <ul>
     *   <li>Game must exist.</li>
     *   <li>Only the creator (host) can delete.</li>
     *   <li>Game must be in WAITING status — cannot cancel an active match.</li>
     * </ul>
     *
     * <p>After the DB delete commits, broadcasts a {@code /topic/lobby/refresh}
     * WebSocket event so every browser tab removes the cancelled game immediately.
     *
     * @param gameId      the match to delete
     * @param requesterId the authenticated user requesting the delete
     */
    @Transactional
    public void deleteGame(long gameId, long requesterId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));

        if (game.getCreator() == null || !game.getCreator().getId().equals(requesterId)) {
            throw new IllegalStateException("Only the host can cancel this game");
        }

        if (!GameStatus.WAITING.equals(game.getStatus())) {
            throw new IllegalStateException("Cannot cancel a game that is already " + game.getStatus());
        }

        gameRepository.deleteById(gameId);

        try {
            roomManager.endGame(gameId, false);
        } catch (Exception e) {
            log.warn("[deleteGame] Room cleanup for game {} had a non-fatal error: {}", gameId, e.getMessage());
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broadcaster.broadcastLobbyUpdate();
                log.info("[deleteGame] Game {} cancelled by user {} — lobby notified", gameId, requesterId);
            }
        });
    }
}
