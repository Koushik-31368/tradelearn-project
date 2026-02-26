package com.tradelearn.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                        GracefulDegradationManager degradationManager) {
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
    }

    /**
     * Request a rematch for a finished game. Returns new Game if valid, else throws.
     */
    @Transactional
    public Game requestRematch(long gameId, long userId) {
        Game oldGame = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"FINISHED".equals(oldGame.getStatus())) {
            throw new IllegalStateException("Game not finished");
        }

        Game newGame = new Game();
        newGame.setCreator(oldGame.getCreator());
        newGame.setOpponent(oldGame.getOpponent());
        newGame.setStockSymbol(oldGame.getStockSymbol());
        newGame.setDurationMinutes(oldGame.getDurationMinutes());
        newGame.setStartingBalance(oldGame.getStartingBalance());
        newGame.setStatus("WAITING");

        return gameRepository.save(newGame);
    }

    // ==================== JOIN MATCH ====================
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
        int updated = gameRepository.atomicJoin(gameId, opponent);
        if (updated == 0) {
            GameLogger.logGameCannotStart(log, gameId, "Game is not open for joining", Map.of(
                "reason", "atomicJoin returned 0 - either already taken or not found",
                "userId", userId
            ));
            throw new InvalidGameStateException(gameId, "UNKNOWN", "WAITING", 
                "Game is not open for joining (already taken or not found)");
        }

        // ── Phase 2: Re-read with pessimistic lock for validation + side-effects ──
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId, "Game not found after join"));

        // Guard: can't join your own game
        if (game.getCreator().getId().equals(opponent.getId())) {
            GameLogger.logGameCannotStart(log, gameId, "Cannot join your own game", Map.of(
                "creatorId", game.getCreator().getId(),
                "attemptingUserId", userId
            ));
            // Roll back — reset to WAITING
            game.setStatus("WAITING");
            game.setOpponent(null);
            game.setStartTime(null);
            gameRepository.save(game);
            throw new IllegalArgumentException("Cannot join your own game");
        }

        // ── Register opponent in RoomManager ──
        roomManager.joinRoom(gameId, userId);

        GameLogger.logDiagnosticSnapshot(log, "Before Auto-Start", Map.of(
            "gameId", gameId,
            "creatorId", game.getCreator().getId(),
            "opponentId", opponent.getId(),
            "status", game.getStatus(),
            "stockSymbol", game.getStockSymbol(),
            "totalCandles", game.getTotalCandles()
        ));

        // ── Auto-start: load candles + begin scheduler immediately ──
        candleService.loadCandles(gameId);
        matchSchedulerService.startProgression(gameId);

        // ── Initialize position snapshots for both players (O(1) reads henceforth) ──
        positionStore.initializePosition(gameId, game.getCreator().getId(), game.getStartingBalance());
        positionStore.initializePosition(gameId, userId, game.getStartingBalance());

        GameLogger.logGameStarted(log, gameId, game.getCreator().getId(), opponent.getId());

        // Notify the creator (already on GamePage) that the match has started
        broadcaster.sendToGame(gameId, "started",
                Map.of(
                        "gameId", gameId,
                        "status", "ACTIVE",
                        "opponentId", opponent.getId(),
                        "opponentUsername", opponent.getUsername()
                )
        );

        GameLogger.logDiagnosticSnapshot(log, "Join Complete", Map.of(
            "gameId", gameId,
            "creatorId", game.getCreator().getId(),
            "opponentId", opponent.getId(),
            "status", "ACTIVE",
            "candlesLoaded", true,
            "schedulerStarted", true
        ));

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
        long gameId = saved.getId();

        roomManager.createRoom(gameId, player1.getId());
        roomManager.joinRoom(gameId, player2.getId());

        candleService.loadCandles(gameId);
        matchSchedulerService.startProgression(gameId);

        positionStore.initializePosition(gameId, player1.getId(), saved.getStartingBalance());
        positionStore.initializePosition(gameId, player2.getId(), saved.getStartingBalance());

        GameLogger.logDiagnosticSnapshot(log, "Auto-Match Created", Map.of(
            "gameId", gameId,
            "player1", player1.getUsername() + " (" + player1.getRating() + ")",
            "player2", player2.getUsername() + " (" + player2.getRating() + ")",
            "stockSymbol", symbol,
            "status", "ACTIVE"
        ));

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
            candleService.loadCandles(gameId);
            Game saved = gameRepository.save(game);
            matchSchedulerService.startProgression(gameId);
            positionStore.initializePosition(gameId, creator.getId(), game.getStartingBalance());
            positionStore.initializePosition(gameId, opponent.getId(), game.getStartingBalance());
            GameLogger.logGameStarted(log, gameId, creator.getId(), opponent.getId());
            GameLogger.logDiagnosticSnapshot(log, "Match Started", Map.of(
                "gameId", gameId,
                "creatorId", creator.getId(),
                "opponentId", opponent.getId(),
                "status", "ACTIVE",
                "startTime", game.getStartTime().toString(),
                "candlesLoaded", true
            ));
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
        candleService.evict(gameId);
        positionStore.evictGame(gameId);
        rateLimiter.evictGame(gameId);
        roomManager.endGame(gameId, false);
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
        return result;
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
