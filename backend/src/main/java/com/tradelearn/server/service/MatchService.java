package com.tradelearn.server.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.dto.MatchResult;
import com.tradelearn.server.exception.GameNotFoundException;
import com.tradelearn.server.exception.InvalidGameStateException;
import com.tradelearn.server.exception.RoomFullException;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.MatchStats;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.MatchStatsRepository;
import com.tradelearn.server.repository.UserRepository;
import com.tradelearn.server.socket.GameBroadcaster;
import com.tradelearn.server.util.EloUtil;
import com.tradelearn.server.util.GameLogger;
import com.tradelearn.server.util.ScoringUtil;

@Service
public class MatchService {

    // ── Lua: atomic rematch consent check ──
    // Returns: -1 = new (first requester), -2 = same user, >=0 = mutual consent (first requester userId)
    private static final String REMATCH_CHECK_LUA = """
            local val = redis.call('GET', KEYS[1])
            if val == false then
              redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
              return -1
            elseif val == ARGV[1] then
              return -2
            else
              redis.call('DEL', KEYS[1])
              return tonumber(val)
            end
            """;
    private static final String REMATCH_PREFIX = "rematch:";
    private static final Duration REMATCH_TTL = Duration.ofSeconds(120);

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

        // Create in-memory room
        roomManager.createRoom(saved.getId(), creator.getId());

        return saved;
    }

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);
    private static final int MAX_PLAYERS = 2;

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final MatchTradeService matchTradeService;
    private final CandleService candleService;
    private final MatchSchedulerService matchSchedulerService;
    private final MatchStatsRepository matchStatsRepository;
    private final GameBroadcaster broadcaster;
    private final RoomManager roomManager;
    private final PositionSnapshotStore positionStore;
    private final TradeRateLimiter rateLimiter;
    private final GameMetricsService metrics;
    private final GracefulDegradationManager degradationManager;
    private final StringRedisTemplate redis;

    public MatchService(GameRepository gameRepository,
                        UserRepository userRepository,
                        @Lazy MatchTradeService matchTradeService,
                        CandleService candleService,
                        MatchSchedulerService matchSchedulerService,
                        MatchStatsRepository matchStatsRepository,
                        GameBroadcaster broadcaster,
                        RoomManager roomManager,
                        PositionSnapshotStore positionStore,
                        TradeRateLimiter rateLimiter,
                        GameMetricsService metrics,
                        GracefulDegradationManager degradationManager,
                        StringRedisTemplate redis) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.matchTradeService = matchTradeService;
        this.candleService = candleService;
        this.matchSchedulerService = matchSchedulerService;
        this.matchStatsRepository = matchStatsRepository;
        this.broadcaster = broadcaster;
        this.roomManager = roomManager;
        this.positionStore = positionStore;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.degradationManager = degradationManager;
        this.redis = redis;
    }

    /**
     * Request a rematch for a finished/abandoned game.
     *
     * Uses Redis SETNX + Lua for distributed race-free mutual consent:
     *   - First caller → PENDING (rematch:{oldGameId} set with TTL 120s, opponent notified via WS)
     *   - Second caller → ACCEPTED (key deleted atomically, new ACTIVE game created)
     *   - Same user twice → idempotent PENDING
     *
     * Redis keys:
     *   rematch:{oldGameId}  →  String (requesterUserId)  TTL 120s
     *
     * Cluster-safe: Lua script ensures atomic check + delete for mutual consent.
     * Only one instance sees the consent result; all others see key-deleted.
     */
    @Transactional
    public Map<String, Object> requestRematch(long oldGameId, long userId) {
        Game oldGame = gameRepository.findById(oldGameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"FINISHED".equals(oldGame.getStatus()) && !"ABANDONED".equals(oldGame.getStatus())) {
            throw new IllegalStateException("Game is not finished or abandoned");
        }

        boolean isCreator = oldGame.getCreator() != null && oldGame.getCreator().getId().equals(userId);
        boolean isOpponent = oldGame.getOpponent() != null && oldGame.getOpponent().getId().equals(userId);
        if (!isCreator && !isOpponent) {
            throw new IllegalArgumentException("You are not a participant in this game");
        }

        long opponentId = isCreator ? oldGame.getOpponent().getId() : oldGame.getCreator().getId();

        // Atomic distributed consent check via Lua
        String rematchKey = REMATCH_PREFIX + oldGameId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(REMATCH_CHECK_LUA, Long.class);
        Long result = redis.execute(script,
                java.util.Collections.singletonList(rematchKey),
                String.valueOf(userId),
                String.valueOf(REMATCH_TTL.getSeconds()));

        if (result == null || result == -1) {
            // We're the first requester — notify opponent via WebSocket
            broadcaster.sendToUser(opponentId, "rematch-request", Map.of(
                "oldGameId", oldGameId,
                "requesterId", userId,
                "message", "Your opponent wants a rematch!"
            ));
            return Map.of("status", "PENDING");
        } else if (result == -2) {
            // Same player requesting again — idempotent
            return Map.of("status", "PENDING");
        } else {
            // Mutual consent! result = first requester's userId

            String symbol = RANKED_SYMBOLS[ThreadLocalRandom.current().nextInt(RANKED_SYMBOLS.length)];

            Game newGame = new Game();
            newGame.setCreator(oldGame.getCreator());
            newGame.setOpponent(oldGame.getOpponent());
            newGame.setStockSymbol(symbol);
            newGame.setDurationMinutes(oldGame.getDurationMinutes());
            newGame.setStartingBalance(oldGame.getStartingBalance());
            newGame.setStatus("ACTIVE");
            newGame.setStartTime(LocalDateTime.now());
            newGame.setCreatedAt(java.sql.Timestamp.valueOf(LocalDateTime.now()));

            Game saved = gameRepository.save(newGame);
            final long newGameId = saved.getId();
            final long creatorId = saved.getCreator().getId();
            final long oppId = saved.getOpponent().getId();
            final double startBal = saved.getStartingBalance();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        roomManager.createRoom(newGameId, creatorId);
                        roomManager.joinRoom(newGameId, oppId);

                        candleService.loadCandles(newGameId);
                        matchSchedulerService.startProgression(newGameId);

                        positionStore.initializePosition(newGameId, creatorId, startBal);
                        positionStore.initializePosition(newGameId, oppId, startBal);

                        Map<String, Object> payload = Map.of(
                            "newGameId", newGameId,
                            "stockSymbol", symbol,
                            "message", "Rematch starting!"
                        );
                        broadcaster.sendToUser(creatorId, "rematch-started", payload);
                        broadcaster.sendToUser(oppId, "rematch-started", payload);

                        log.info("[Rematch] New game {} created from old game {}", newGameId, oldGameId);
                    } catch (Exception e) {
                        GameLogger.logError(log, "requestRematch.afterCommit", newGameId, e, Map.of(
                            "oldGameId", oldGameId,
                            "impact", "DB committed ACTIVE but side effects failed"
                        ));
                    }
                }
            });

            return Map.of("status", "ACCEPTED", "newGameId", newGameId);
        }
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

        // ── Phase 1: Atomic compare-and-swap ──
        // clearAutomatically = true on @Modifying ensures the L1 cache
        // is evicted, so findByIdForUpdate reads fresh state.
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
            // No need for manual rollback — throwing RuntimeException rolls back
            // the entire @Transactional, including the atomicJoin UPDATE.
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

        // ── Phase 3: Schedule all side effects AFTER the DB transaction commits ──
        // This guarantees Redis/WebSocket/scheduler only fire when the
        // WAITING → ACTIVE transition is durably persisted.
        final long creatorId = game.getCreator().getId();
        final double startingBalance = game.getStartingBalance();
        final Long opponentId = opponent.getId();
        final String opponentUsername = opponent.getUsername();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // Redis: register opponent in room
                    roomManager.joinRoom(gameId, opponentId);

                    // Load historical candles for chart
                    candleService.loadCandles(gameId);

                    // Start the 5s candle tick scheduler (distributed via Redis SETNX)
                    matchSchedulerService.startProgression(gameId);

                    // Initialize position snapshots for both players (O(1) reads henceforth)
                    positionStore.initializePosition(gameId, creatorId, startingBalance);
                    positionStore.initializePosition(gameId, opponentId, startingBalance);

                    GameLogger.logGameStarted(log, gameId, creatorId, opponentId);

                    // Notify both players (including creator on GamePage) that match started
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
                    // DB is already committed — log loudly for ops visibility.
                    // A background reconciliation job should detect orphaned ACTIVE
                    // games with no Redis room and repair them.
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
    private static final String[] RANKED_SYMBOLS = {
        "TCS", "INFY", "RELIANCE", "HDFCBANK", "ICICIBANK",
        "WIPRO", "SBIN", "BHARTIARTL", "ITC", "KOTAKBANK",
        "LT", "AXISBANK", "HINDUNILVR", "MARUTI", "TATASTEEL"
    };

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
    @Transactional
    public Game startMatch(long gameId) {
        GameLogger.setGameContext(gameId);
        try {
            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new GameNotFoundException(gameId));

            User opponent = game.getOpponent();
            User creator = game.getCreator();
            GameLogger.logGameStartAttempt(log, gameId, 
                creator != null ? creator.getId() : -1,
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
                    "creatorId", creator.getId(),
                    "hasOpponent", false
                ));
                throw new IllegalStateException("Game needs two players to start");
            }

            game.setStartTime(LocalDateTime.now());
            Game saved = gameRepository.save(game);

            final long localGameId = gameId;
            final long cId = creator.getId();
            final long oId = opponent.getId();
            final double startBal = game.getStartingBalance();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        candleService.loadCandles(localGameId);
                        matchSchedulerService.startProgression(localGameId);
                        positionStore.initializePosition(localGameId, cId, startBal);
                        positionStore.initializePosition(localGameId, oId, startBal);
                        GameLogger.logGameStarted(log, localGameId, cId, oId);
                        GameLogger.logDiagnosticSnapshot(log, "Match Started", Map.of(
                            "gameId", localGameId,
                            "creatorId", cId,
                            "opponentId", oId,
                            "status", "ACTIVE",
                            "candlesLoaded", true
                        ));
                    } catch (Exception e) {
                        GameLogger.logError(log, "startMatch.afterCommit", localGameId, e, Map.of(
                            "impact", "DB committed but side effects failed — needs reconciliation"
                        ));
                    }
                }
            });

            return saved;
        } catch (Exception e) {
            GameLogger.logError(log, "startMatch", gameId, e, null);
            throw e;
        } finally {
            GameLogger.clearContext();
        }
    }

    // ==================== END MATCH & CALCULATE WINNER ====================
    @Transactional
    public MatchResult endMatch(long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"ACTIVE".equals(game.getStatus())) {
            throw new IllegalStateException("Game is not active");
        }

        double currentStockPrice = candleService.getCurrentPrice(gameId);
        matchSchedulerService.stopProgression(gameId);
        positionStore.updateMarkPrice(gameId, game.getCreator().getId(), currentStockPrice);
        positionStore.updateMarkPrice(gameId, game.getOpponent().getId(), currentStockPrice);
        MatchTradeService.PlayerPosition creatorPos = matchTradeService.getPlayerPosition(
                gameId, game.getCreator().getId(), game.getStartingBalance()
        );
        MatchTradeService.PlayerPosition opponentPos = matchTradeService.getPlayerPosition(
                gameId, game.getOpponent().getId(), game.getStartingBalance()
        );
        double creatorBalance = PositionSnapshotStore.snapshotEquity(creatorPos, currentStockPrice);
        double opponentBalance = PositionSnapshotStore.snapshotEquity(opponentPos, currentStockPrice);
        if (creatorBalance > creatorPos.peakEquity) creatorPos.peakEquity = creatorBalance;
        if (opponentBalance > opponentPos.peakEquity) opponentPos.peakEquity = opponentBalance;
        double creatorScore = ScoringUtil.calculate(
                creatorBalance, game.getStartingBalance(),
                creatorPos.maxDrawdown, creatorPos.totalTrades, creatorPos.profitableTrades);
        double opponentScore = ScoringUtil.calculate(
                opponentBalance, game.getStartingBalance(),
                opponentPos.maxDrawdown, opponentPos.totalTrades, opponentPos.profitableTrades);
        persistStats(gameId, game.getCreator().getId(), creatorPos, creatorBalance, creatorScore);
        persistStats(gameId, game.getOpponent().getId(), opponentPos, opponentBalance, opponentScore);
        User winner = null;
        if (creatorScore > opponentScore) {
            winner = game.getCreator();
        } else if (opponentScore > creatorScore) {
            winner = game.getOpponent();
        }
        User creator = game.getCreator();
        User opponent = game.getOpponent();
        int creatorRatingBefore = creator.getRating();
        int opponentRatingBefore = opponent.getRating();
        double creatorActual = winner == null ? 0.5 : winner.getId().equals(creator.getId()) ? 1.0 : 0.0;
        double opponentActual = winner == null ? 0.5 : winner.getId().equals(opponent.getId()) ? 1.0 : 0.0;
        int creatorNewRating = EloUtil.calculateNewRating(creatorRatingBefore, opponentRatingBefore, creatorActual);
        int opponentNewRating = EloUtil.calculateNewRating(opponentRatingBefore, creatorRatingBefore, opponentActual);
        int creatorRatingDelta = creatorNewRating - creatorRatingBefore;
        int opponentRatingDelta = opponentNewRating - opponentRatingBefore;
        creator.setRating(creatorNewRating);
        opponent.setRating(opponentNewRating);
        userRepository.save(creator);
        userRepository.save(opponent);
        game.setStatus("FINISHED");
        game.setEndTime(LocalDateTime.now());
        game.setCreatorFinalBalance(creatorBalance);
        game.setOpponentFinalBalance(opponentBalance);
        game.setCreatorFinalScore(creatorScore);
        game.setOpponentFinalScore(opponentScore);
        game.setCreatorRatingDelta(creatorRatingDelta);
        game.setOpponentRatingDelta(opponentRatingDelta);
        game.setWinner(winner);
        gameRepository.save(game);

        // Build result while inside TX (entity data is available)
        double startingBalance = game.getStartingBalance();
        MatchResult result = new MatchResult();
        result.setGameId(game.getId());
        result.setStatus("FINISHED");
        result.setStockSymbol(game.getStockSymbol());
        result.setCreatorId(game.getCreator().getId());
        result.setCreatorUsername(game.getCreator().getUsername());
        result.setCreatorFinalBalance(creatorBalance);
        result.setCreatorProfit(creatorBalance - startingBalance);
        result.setOpponentId(game.getOpponent().getId());
        result.setOpponentUsername(game.getOpponent().getUsername());
        result.setOpponentFinalBalance(opponentBalance);
        result.setOpponentProfit(opponentBalance - startingBalance);
        if (winner != null) {
            result.setWinnerId(winner.getId());
            result.setWinnerUsername(winner.getUsername());
        }
        result.setCreatorPeakEquity(creatorPos.peakEquity);
        result.setCreatorMaxDrawdown(creatorPos.maxDrawdown);
        result.setCreatorTotalTrades(creatorPos.totalTrades);
        result.setCreatorProfitableTrades(creatorPos.profitableTrades);
        result.setOpponentPeakEquity(opponentPos.peakEquity);
        result.setOpponentMaxDrawdown(opponentPos.maxDrawdown);
        result.setOpponentTotalTrades(opponentPos.totalTrades);
        result.setOpponentProfitableTrades(opponentPos.profitableTrades);
        result.setCreatorFinalScore(creatorScore);
        result.setOpponentFinalScore(opponentScore);
        result.setCreatorRatingDelta(creatorRatingDelta);
        result.setOpponentRatingDelta(opponentRatingDelta);
        result.setCreatorNewRating(creatorNewRating);
        result.setOpponentNewRating(opponentNewRating);

        // Schedule side effects AFTER DB commit — prevents Redis/WS mutation on TX rollback
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    matchSchedulerService.stopProgression(gameId);
                    candleService.evict(gameId);
                    positionStore.evictGame(gameId);
                    rateLimiter.evictGame(gameId);
                    roomManager.endGame(gameId, false);
                } catch (Exception e) {
                    log.error("[EndMatch] afterCommit side effects failed for game {}: {}",
                            gameId, e.getMessage(), e);
                }
            }
        });

        return result;
    }

    // ==================== FORCE FINISH ON ABANDON (ELO penalty) ====================

    /**
     * Score and rate a game where a player disconnected past the grace period.
     * The remaining (connected) player wins. Full scoring + ELO update applied.
     * Called from WebSocketEventListener.doAbandon() — transactional boundary
     * ensures all DB mutations are atomic.
     */
    @Transactional
    public void forceFinishOnAbandon(long gameId, long disconnectedUserId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"ACTIVE".equals(game.getStatus())) {
            log.info("[Abandon] Game {} already {} — skipping scoring", gameId, game.getStatus());
            return;
        }

        if (game.getOpponent() == null) {
            log.warn("[Abandon] Game {} has no opponent — marking ABANDONED without scoring", gameId);
            game.setStatus("ABANDONED");
            game.setEndTime(LocalDateTime.now());
            gameRepository.save(game);
            return;
        }

        double currentPrice = candleService.getCurrentPrice(gameId);

        // Update mark prices before reading snapshots
        positionStore.updateMarkPrice(gameId, game.getCreator().getId(), currentPrice);
        positionStore.updateMarkPrice(gameId, game.getOpponent().getId(), currentPrice);

        MatchTradeService.PlayerPosition creatorPos = matchTradeService.getPlayerPosition(
                gameId, game.getCreator().getId(), game.getStartingBalance());
        MatchTradeService.PlayerPosition opponentPos = matchTradeService.getPlayerPosition(
                gameId, game.getOpponent().getId(), game.getStartingBalance());

        double creatorBalance = PositionSnapshotStore.snapshotEquity(creatorPos, currentPrice);
        double opponentBalance = PositionSnapshotStore.snapshotEquity(opponentPos, currentPrice);

        if (creatorBalance > creatorPos.peakEquity) creatorPos.peakEquity = creatorBalance;
        if (opponentBalance > opponentPos.peakEquity) opponentPos.peakEquity = opponentBalance;

        double creatorScore = ScoringUtil.calculate(
                creatorBalance, game.getStartingBalance(),
                creatorPos.maxDrawdown, creatorPos.totalTrades, creatorPos.profitableTrades);
        double opponentScore = ScoringUtil.calculate(
                opponentBalance, game.getStartingBalance(),
                opponentPos.maxDrawdown, opponentPos.totalTrades, opponentPos.profitableTrades);

        persistStats(gameId, game.getCreator().getId(), creatorPos, creatorBalance, creatorScore);
        persistStats(gameId, game.getOpponent().getId(), opponentPos, opponentBalance, opponentScore);

        // Winner = remaining player (disconnected player always loses)
        boolean disconnectedIsCreator = game.getCreator().getId().equals(disconnectedUserId);
        User winner = disconnectedIsCreator ? game.getOpponent() : game.getCreator();

        // ELO update — disconnected player is the loser
        User creator = game.getCreator();
        User opponent = game.getOpponent();
        int creatorRatingBefore = creator.getRating();
        int opponentRatingBefore = opponent.getRating();

        double creatorActual = winner.getId().equals(creator.getId()) ? 1.0 : 0.0;
        double opponentActual = winner.getId().equals(opponent.getId()) ? 1.0 : 0.0;

        int creatorNewRating = EloUtil.calculateNewRating(creatorRatingBefore, opponentRatingBefore, creatorActual);
        int opponentNewRating = EloUtil.calculateNewRating(opponentRatingBefore, creatorRatingBefore, opponentActual);

        int creatorRatingDelta = creatorNewRating - creatorRatingBefore;
        int opponentRatingDelta = opponentNewRating - opponentRatingBefore;

        creator.setRating(creatorNewRating);
        opponent.setRating(opponentNewRating);
        userRepository.save(creator);
        userRepository.save(opponent);

        game.setStatus("ABANDONED");
        game.setEndTime(LocalDateTime.now());
        game.setCreatorFinalBalance(creatorBalance);
        game.setOpponentFinalBalance(opponentBalance);
        game.setCreatorFinalScore(creatorScore);
        game.setOpponentFinalScore(opponentScore);
        game.setCreatorRatingDelta(creatorRatingDelta);
        game.setOpponentRatingDelta(opponentRatingDelta);
        game.setWinner(winner);
        gameRepository.save(game);

        // Schedule cache evictions AFTER DB commit to prevent stale state on rollback
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                candleService.evict(gameId);
                positionStore.evictGame(gameId);
                rateLimiter.evictGame(gameId);
            }
        });

        log.info("[Abandon] Game {} scored and rated. Winner: {} ({}). Creator: {} → {}, Opponent: {} → {}",
                gameId, winner.getUsername(), winner.getId(),
                creatorRatingBefore, creatorNewRating,
                opponentRatingBefore, opponentNewRating);
    }

    // ==================== STATS PERSISTENCE ====================
    private void persistStats(long gameId, long userId,
                              MatchTradeService.PlayerPosition pos,
                              double finalEquity, double finalScore) {
        MatchStats stats = matchStatsRepository
                .findByGameIdAndUserId(gameId, userId)
                .orElse(new MatchStats());
        stats.setGameId(gameId);
        stats.setUserId(userId);
        stats.setPeakEquity(pos.peakEquity);
        stats.setMaxDrawdown(pos.maxDrawdown);
        stats.setTotalTrades(pos.totalTrades);
        stats.setProfitableTrades(pos.profitableTrades);
        stats.setFinalEquity(finalEquity);
        stats.setFinalScore(finalScore);
        matchStatsRepository.save(stats);
    }

    // ==================== QUERIES ====================
    public Optional<Game> getMatch(long gameId) {
        return gameRepository.findById(gameId);
    }
    public List<Game> getOpenMatches() {
        return gameRepository.findByStatus("WAITING");
    }
    public List<Game> getActiveMatches() {
        return gameRepository.findByStatus("ACTIVE");
    }
    public List<Game> getFinishedMatches() {
        return gameRepository.findByStatus("FINISHED");
    }
    public List<Game> getUserMatches(long userId) {
        return gameRepository.findByCreatorIdOrOpponentId(userId, userId);
    }
}
