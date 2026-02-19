package com.tradelearn.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public MatchService(GameRepository gameRepository,
                        UserRepository userRepository,
                        MatchTradeService matchTradeService,
                        CandleService candleService,
                        MatchSchedulerService matchSchedulerService,
                        MatchStatsRepository matchStatsRepository,
                        GameBroadcaster broadcaster,
                        RoomManager roomManager) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.matchTradeService = matchTradeService;
        this.candleService = candleService;
        this.matchSchedulerService = matchSchedulerService;
        this.matchStatsRepository = matchStatsRepository;
        this.broadcaster = broadcaster;
        this.roomManager = roomManager;
    }

    // ==================== CREATE MATCH ====================

    @Transactional
    public Game createMatch(CreateMatchRequest request) {
        User creator = userRepository.findById(request.getCreatorId())
                .orElseThrow(() -> new IllegalArgumentException("Creator not found"));

        Game game = new Game();
        game.setCreator(creator);
        game.setStockSymbol(request.getStockSymbol());
        game.setDurationMinutes(request.getDurationMinutes());
        Double balance = request.getStartingBalance();
        game.setStartingBalance(balance != null ? balance : 1_000_000.0);
        game.setStatus("WAITING");

        Game saved = gameRepository.save(game);

        // Register in-memory room
        roomManager.createRoom(saved.getId(), creator.getId());

        GameLogger.logDiagnosticSnapshot(log, "Match Created", Map.of(
            "gameId", saved.getId(),
            "creatorId", creator.getId(),
            "creatorUsername", creator.getUsername(),
            "stockSymbol", game.getStockSymbol(),
            "startingBalance", game.getStartingBalance(),
            "status", "WAITING",
            "durationMinutes", request.getDurationMinutes()
        ));

        return saved;
    }

    // ==================== JOIN MATCH ====================
    // Uses a two-phase approach to prevent the double-join race:
    //   Phase 1: Atomic CAS UPDATE (status=WAITING → ACTIVE) — only one thread wins
    //   Phase 2: Pessimistic-locked re-read for business logic (candle load, scheduler)

    @Transactional
    public Game joinMatch(long gameId, long userId) {
        GameLogger.setGameContext(gameId);
        GameLogger.setUserContext(userId);
        
        try {
            User opponent = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // ── Fast in-memory guard: check room capacity before hitting DB ──
            RoomManager.Room room = roomManager.getRoom(gameId);
            if (room != null && room.isFull()) {
                GameLogger.logGameCannotStart(log, gameId, "Room is full", Map.of(
                    "currentPlayers", room.getPlayerCount(),
                    "maxPlayers", MAX_PLAYERS,
                    "attemptingUserId", userId
                ));
                throw new RoomFullException(gameId, room.getPlayerCount(), MAX_PLAYERS);
            }

            GameLogger.logDiagnosticSnapshot(log, "Attempting Join", Map.of(
                "gameId", gameId,
                "userId", userId,
                "username", opponent.getUsername(),
                "roomExists", room != null,
                "roomSize", room != null ? room.getPlayerCount() : 0,
                "roomPhase", room != null ? room.getPhase().name() : "NO_ROOM"
            ));

            // ── Phase 1: Atomic compare-and-swap ──
            // UPDATE games SET status='ACTIVE', opponent_id=? WHERE id=? AND status='WAITING'
            // Returns 0 if someone else already joined (status is no longer WAITING).
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
            
        } catch (Exception e) {
            GameLogger.logError(log, "joinMatch", gameId, e, Map.of(
                "userId", userId
            ));
            throw e;
        } finally {
            GameLogger.clearContext();
        }
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

            // Load candle data so the game has server-authoritative prices
            candleService.loadCandles(gameId);

            Game saved = gameRepository.save(game);

            // Begin automatic candle progression (every 5 seconds)
            matchSchedulerService.startProgression(gameId);

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
    // Uses PESSIMISTIC_WRITE lock to prevent race between
    // manual /end call and scheduler's autoFinishGame().

    @Transactional
    public MatchResult endMatch(long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"ACTIVE".equals(game.getStatus())) {
            throw new IllegalStateException("Game is not active");
        }

        // Server-authoritative final price from CandleService
        double currentStockPrice = candleService.getCurrentPrice(gameId);

        // Stop scheduled candle progression
        matchSchedulerService.stopProgression(gameId);

        // Calculate final positions (includes risk stats from trade replay)
        MatchTradeService.PlayerPosition creatorPos = matchTradeService.calculatePosition(
                gameId, game.getCreator().getId(), game.getStartingBalance()
        );
        MatchTradeService.PlayerPosition opponentPos = matchTradeService.calculatePosition(
                gameId, game.getOpponent().getId(), game.getStartingBalance()
        );

        // Final balances: cash + longs×price − shorts×price
        double creatorBalance = matchTradeService.calculateFinalBalance(
                gameId, game.getCreator().getId(), game.getStartingBalance(), currentStockPrice
        );
        double opponentBalance = matchTradeService.calculateFinalBalance(
                gameId, game.getOpponent().getId(), game.getStartingBalance(), currentStockPrice
        );

        // Update peak equity with the actual final equity
        if (creatorBalance > creatorPos.peakEquity) creatorPos.peakEquity = creatorBalance;
        if (opponentBalance > opponentPos.peakEquity) opponentPos.peakEquity = opponentBalance;

        // Calculate hybrid scores
        double creatorScore = ScoringUtil.calculate(
                creatorBalance, game.getStartingBalance(),
                creatorPos.maxDrawdown, creatorPos.totalTrades, creatorPos.profitableTrades);
        double opponentScore = ScoringUtil.calculate(
                opponentBalance, game.getStartingBalance(),
                opponentPos.maxDrawdown, opponentPos.totalTrades, opponentPos.profitableTrades);

        // Persist per-player stats (including score)
        persistStats(gameId, game.getCreator().getId(), creatorPos, creatorBalance, creatorScore);
        persistStats(gameId, game.getOpponent().getId(), opponentPos, opponentBalance, opponentScore);

        // Determine winner by hybrid score
        User winner = null;
        if (creatorScore > opponentScore) {
            winner = game.getCreator();
        } else if (opponentScore > creatorScore) {
            winner = game.getOpponent();
        }
        // null winner = draw

        // ---- ELO rating update ----
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

        // Update game
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

        // Free candle cache for this game
        candleService.evict(gameId);

        // Clean up in-memory room state
        roomManager.endGame(gameId, false);

        // Build result DTO
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

        // Populate risk stats
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
