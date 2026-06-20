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
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.websocket.GameBroadcaster;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
        game.setStatus("WAITING");
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

        // ── Phase 3: Register afterCommit side effects ──
        final long creatorId = game.getCreator().getId();
        final double startingBalance = game.getStartingBalance();
        final Long opponentId = opponent.getId();
        final String opponentUsername = opponent.getUsername();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
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
                } catch (Exception e) {
                    GameLogger.logError(log, "joinMatch.afterCommit", gameId, e, Map.of(
                        "creatorId", creatorId,
                        "opponentId", opponentId,
                        "impact", "DB committed ACTIVE but side effects failed — needs reconciliation"
                    ));
                }
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
        game.setStatus("ACTIVE");
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
                try {
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
                } catch (Exception e) {
                    GameLogger.logError(log, "createAutoMatch.afterCommit", gameId, e, Map.of(
                        "p1Id", p1Id,
                        "p2Id", p2Id,
                        "impact", "DB committed ACTIVE but side effects failed — needs reconciliation"
                    ));
                }
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

            if (!"ACTIVE".equals(game.getStatus())) {
                GameLogger.logGameCannotStart(log, gameId, "Game is not ACTIVE", Map.of(
                    "currentStatus", game.getStatus(),
                    "expectedStatus", "ACTIVE"
                ));
                throw new InvalidGameStateException(gameId, game.getStatus(), "ACTIVE");
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
                    try {
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
                    } catch (Exception e) {
                        GameLogger.logError(log, "startMatch.afterCommit", gameId, e, Map.of(
                            "impact", "DB committed but side effects failed — needs reconciliation"
                        ));
                    }
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

        if (!"WAITING".equals(game.getStatus())) {
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
