package com.tradelearn.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.dto.MatchResult;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.MatchStats;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.MatchStatsRepository;
import com.tradelearn.server.repository.UserRepository;
import com.tradelearn.server.util.EloUtil;
import com.tradelearn.server.util.ScoringUtil;

@Service
public class MatchService {

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final MatchTradeService matchTradeService;
    private final CandleService candleService;
    private final MatchSchedulerService matchSchedulerService;
    private final MatchStatsRepository matchStatsRepository;

    public MatchService(GameRepository gameRepository,
                        UserRepository userRepository,
                        MatchTradeService matchTradeService,
                        CandleService candleService,
                        MatchSchedulerService matchSchedulerService,
                        MatchStatsRepository matchStatsRepository) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.matchTradeService = matchTradeService;
        this.candleService = candleService;
        this.matchSchedulerService = matchSchedulerService;
        this.matchStatsRepository = matchStatsRepository;
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

        return gameRepository.save(game);
    }

    // ==================== JOIN MATCH ====================

    @Transactional
    public Game joinMatch(long gameId, long userId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"WAITING".equals(game.getStatus())) {
            throw new IllegalStateException("Game is not open for joining");
        }

        User opponent = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (game.getCreator().getId().equals(opponent.getId())) {
            throw new IllegalArgumentException("Cannot join your own game");
        }

        game.setOpponent(opponent);
        game.setStatus("ACTIVE");
        game.setStartTime(LocalDateTime.now());

        return gameRepository.save(game);
    }

    // ==================== START MATCH ====================

    @Transactional
    public Game startMatch(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"ACTIVE".equals(game.getStatus())) {
            throw new IllegalStateException("Game must be ACTIVE to start");
        }

        if (game.getOpponent() == null) {
            throw new IllegalStateException("Game needs two players to start");
        }

        game.setStartTime(LocalDateTime.now());

        // Load candle data so the game has server-authoritative prices
        candleService.loadCandles(gameId);

        Game saved = gameRepository.save(game);

        // Begin automatic candle progression (every 5 seconds)
        matchSchedulerService.startProgression(gameId);

        return saved;
    }

    // ==================== END MATCH & CALCULATE WINNER ====================

    @Transactional
    public MatchResult endMatch(long gameId) {
        Game game = gameRepository.findById(gameId)
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
