package com.tradelearn.server.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.MatchStats;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.MatchStatsRepository;
import com.tradelearn.server.repository.TradeRepository;
import com.tradelearn.server.repository.UserRepository;
import com.tradelearn.server.socket.GameBroadcaster;
import com.tradelearn.server.util.EloUtil;
import com.tradelearn.server.util.GameLogger;
import com.tradelearn.server.util.ScoringUtil;

/**
 * Manages per-game scheduled candle progression.
 *
 * Each active match gets its own repeating task (every 5 s) that:
 *   1. Reads currentCandleIndex from the DB (no stale in-memory state).
 *   2. Advances to the next candle (DB write via CandleService).
 *   3. Broadcasts the new candle via WebSocket.
 *   4. Auto-finishes the game when candles are exhausted.
 *
 * The only in-memory map tracks ScheduledFuture handles so tasks
 * can be cancelled when a game ends.
 */
@Service
public class MatchSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(MatchSchedulerService.class);
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(5);

    private final TaskScheduler taskScheduler;
    private final GameRepository gameRepository;
    private final TradeRepository tradeRepository;
    private final CandleService candleService;
    private final GameBroadcaster broadcaster;
    private final MatchStatsRepository matchStatsRepository;
    private final UserRepository userRepository;
    private final RoomManager roomManager;

    /** ScheduledFuture per game — the ONLY in-memory per-game state */
    private final Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    public MatchSchedulerService(TaskScheduler taskScheduler,
                                 GameRepository gameRepository,
                                 TradeRepository tradeRepository,
                                 CandleService candleService,
                                 GameBroadcaster broadcaster,
                                 MatchStatsRepository matchStatsRepository,
                                 UserRepository userRepository,
                                 RoomManager roomManager) {
        this.taskScheduler = taskScheduler;
        this.gameRepository = gameRepository;
        this.tradeRepository = tradeRepository;
        this.candleService = candleService;
        this.broadcaster = broadcaster;
        this.matchStatsRepository = matchStatsRepository;
        this.userRepository = userRepository;
        this.roomManager = roomManager;
    }

    // ==================== START / STOP ====================

    /**
     * Begin candle progression for a game that just became ACTIVE.
     * Immediately broadcasts the first candle, then ticks every 5s.
     *
     * Uses ConcurrentHashMap.computeIfAbsent to guarantee that at most
     * ONE scheduler is ever created per game — even if two threads call
     * this method simultaneously (e.g. from a double-join race).
     */
    public void startProgression(long gameId) {
        runningTasks.computeIfAbsent(gameId, id -> {
            GameLogger.logIntervalCreated(log, id, (int) TICK_INTERVAL.getSeconds());
            
            GameLogger.logDiagnosticSnapshot(log, "Starting Progression", Map.of(
                "gameId", id,
                "intervalSeconds", TICK_INTERVAL.getSeconds(),
                "activeGames", activeGameCount()
            ));

            // Broadcast the first candle immediately so players don't wait 5s
            broadcastCurrentCandle(id);

            ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                    () -> tick(id),
                    TICK_INTERVAL
            );

            // Register the scheduler handle in RoomManager
            roomManager.startGame(id, future);

            return future;
        });
    }

    /**
     * Broadcast the current candle (index 0 at match start) so both
     * players see data immediately without waiting for the first tick.
     */
    private void broadcastCurrentCandle(long gameId) {
        try {
            Game game = gameRepository.findById(gameId).orElse(null);
            if (game == null) {
                GameLogger.logError(log, "broadcastCurrentCandle", gameId, 
                    new IllegalStateException("Game not found"), null);
                return;
            }

            CandleService.Candle candle = candleService.getCurrentCandle(gameId);
            int index = game.getCurrentCandleIndex();
            int remaining = game.getTotalCandles() - index - 1;

            broadcaster.sendToGame(gameId, "candle",
                    Map.of(
                            "candle", candle,
                            "index", index,
                            "remaining", remaining,
                            "price", candle.getClose()
                    )
            );
            
            GameLogger.logIntervalTick(log, gameId, index, game.getTotalCandles(), candle.getClose());
            
        } catch (Exception e) {
            GameLogger.logError(log, "broadcastCurrentCandle", gameId, e, null);
        }
    }

    /**
     * Cancel the scheduled task for a game (manual end or candle exhaustion).
     */
    public void stopProgression(long gameId) {
        ScheduledFuture<?> future = runningTasks.remove(gameId);
        if (future != null) {
            future.cancel(false);
            GameLogger.logIntervalDeleted(log, gameId, "stop_progression_called");
            
            GameLogger.logDiagnosticSnapshot(log, "Progression Stopped", Map.of(
                "gameId", gameId,
                "remainingActiveGames", activeGameCount()
            ));
        }
    }

    // ==================== TICK ====================

    @SuppressWarnings("null")
    private void tick(long gameId) {
        GameLogger.setGameContext(gameId);
        
        try {
            // Note: tick() is NOT @Transactional (called from scheduler thread).
            // advanceCandle() opens its own transaction with a pessimistic lock,
            // so overlapping ticks are safely serialised at the DB level.
            Game game = gameRepository.findById(gameId).orElse(null);
            if (game == null) {
                GameLogger.logError(log, "tick", gameId, 
                    new IllegalStateException("Game not found during tick"), null);
                stopProgression(gameId);
                return;
            }
            
            if (!"ACTIVE".equals(game.getStatus())) {
                GameLogger.logDiagnosticSnapshot(log, "Stopping Tick - Not Active", Map.of(
                    "gameId", gameId,
                    "status", game.getStatus()
                ));
                stopProgression(gameId);
                return;
            }

            // Advance to next candle
            CandleService.Candle nextCandle = candleService.advanceCandle(gameId);

            if (nextCandle == null) {
                // All candles consumed → auto-finish
                GameLogger.logDiagnosticSnapshot(log, "Candles Exhausted", Map.of(
                    "gameId", gameId,
                    "action", "auto_finishing"
                ));
                autoFinishGame(gameId);
                stopProgression(gameId);
                return;
            }

            // Re-read for updated index
            game = gameRepository.findById(gameId).orElse(null);
            if (game == null) return;

            int index = game.getCurrentCandleIndex();
            int remaining = game.getTotalCandles() - index - 1;

            // Broadcast new candle to subscribers
            broadcaster.sendToGame(gameId, "candle",
                    Map.of(
                            "candle", nextCandle,
                            "index", index,
                            "remaining", remaining,
                            "price", nextCandle.getClose()
                    )
            );

            GameLogger.logIntervalTick(log, gameId, index, game.getTotalCandles(), nextCandle.getClose());

        } catch (Exception e) {
            GameLogger.logError(log, "tick", gameId, e, null);
        } finally {
            GameLogger.clearContext();
        }
    }

    // ==================== AUTO-FINISH ====================

    @Transactional
    public void autoFinishGame(long gameId) {
        try {
            // Pessimistic lock prevents race with MatchService.endMatch()
            @SuppressWarnings("null")
            Game game = gameRepository.findByIdForUpdate(gameId).orElse(null);
            if (game == null || !"ACTIVE".equals(game.getStatus())) return;

            double finalPrice = candleService.getCurrentPrice(gameId);

            // Replay trades with risk stats
            ReplayResult creatorReplay = replayWithStats(
                    gameId, game.getCreator().getId(), game.getStartingBalance(), finalPrice);
            ReplayResult opponentReplay = replayWithStats(
                    gameId, game.getOpponent().getId(), game.getStartingBalance(), finalPrice);

            double creatorBal = creatorReplay.finalEquity;
            double opponentBal = opponentReplay.finalEquity;

            // Calculate hybrid scores
            double creatorScore = ScoringUtil.calculate(
                    creatorBal, game.getStartingBalance(),
                    creatorReplay.maxDrawdown, creatorReplay.totalTrades, creatorReplay.profitableTrades);
            double opponentScore = ScoringUtil.calculate(
                    opponentBal, game.getStartingBalance(),
                    opponentReplay.maxDrawdown, opponentReplay.totalTrades, opponentReplay.profitableTrades);

            game.setStatus("FINISHED");
            game.setEndTime(LocalDateTime.now());
            game.setCreatorFinalBalance(creatorBal);
            game.setOpponentFinalBalance(opponentBal);
            game.setCreatorFinalScore(creatorScore);
            game.setOpponentFinalScore(opponentScore);

            // Winner determined by hybrid score
            if (creatorScore > opponentScore) {
                game.setWinner(game.getCreator());
            } else if (opponentScore > creatorScore) {
                game.setWinner(game.getOpponent());
            }

            gameRepository.save(game);

            // ---- ELO rating update ----
            User creator = game.getCreator();
            User opponent = game.getOpponent();
            int creatorRatingBefore = creator.getRating();
            int opponentRatingBefore = opponent.getRating();

            double creatorActual = game.getWinner() == null ? 0.5
                    : game.getWinner().getId().equals(creator.getId()) ? 1.0 : 0.0;
            double opponentActual = game.getWinner() == null ? 0.5
                    : game.getWinner().getId().equals(opponent.getId()) ? 1.0 : 0.0;

            int creatorNewRating = EloUtil.calculateNewRating(creatorRatingBefore, opponentRatingBefore, creatorActual);
            int opponentNewRating = EloUtil.calculateNewRating(opponentRatingBefore, creatorRatingBefore, opponentActual);

            int creatorRatingDelta = creatorNewRating - creatorRatingBefore;
            int opponentRatingDelta = opponentNewRating - opponentRatingBefore;

            creator.setRating(creatorNewRating);
            opponent.setRating(opponentNewRating);
            userRepository.save(creator);
            userRepository.save(opponent);

            game.setCreatorRatingDelta(creatorRatingDelta);
            game.setOpponentRatingDelta(opponentRatingDelta);
            gameRepository.save(game);

            // Persist stats with scores
            persistStats(gameId, game.getCreator().getId(), creatorReplay, creatorScore);
            persistStats(gameId, game.getOpponent().getId(), opponentReplay, opponentScore);

            candleService.evict(gameId);

            // Broadcast result with stats
            double startBal = game.getStartingBalance();
            Object winnerId = game.getWinner() != null ? game.getWinner().getId() : "draw";

            Map<String, Object> payload = new HashMap<>();
            payload.put("gameId", gameId);
            payload.put("status", "FINISHED");
            payload.put("creatorId", game.getCreator().getId());
            payload.put("creatorFinalBalance", creatorBal);
            payload.put("creatorProfit", creatorBal - startBal);
            payload.put("creatorPeakEquity", creatorReplay.peakEquity);
            payload.put("creatorMaxDrawdown", creatorReplay.maxDrawdown);
            payload.put("creatorTotalTrades", creatorReplay.totalTrades);
            payload.put("creatorProfitableTrades", creatorReplay.profitableTrades);
            payload.put("opponentId", game.getOpponent().getId());
            payload.put("opponentFinalBalance", opponentBal);
            payload.put("opponentProfit", opponentBal - startBal);
            payload.put("opponentPeakEquity", opponentReplay.peakEquity);
            payload.put("opponentMaxDrawdown", opponentReplay.maxDrawdown);
            payload.put("opponentTotalTrades", opponentReplay.totalTrades);
            payload.put("opponentProfitableTrades", opponentReplay.profitableTrades);
            payload.put("creatorFinalScore", creatorScore);
            payload.put("opponentFinalScore", opponentScore);
            payload.put("creatorRatingDelta", creatorRatingDelta);
            payload.put("opponentRatingDelta", opponentRatingDelta);
            payload.put("creatorNewRating", creatorNewRating);
            payload.put("opponentNewRating", opponentNewRating);
            payload.put("winnerId", winnerId);

            broadcaster.sendToGame(gameId, "finished", payload);

            // Clean up in-memory room state
            roomManager.endGame(gameId, false);

            log.info("Game {} auto-finished. Creator: {}, Opponent: {}",
                    gameId, String.format("%.2f", creatorBal), String.format("%.2f", opponentBal));

        } catch (Exception e) {
            log.error("Failed to auto-finish game {}: {}", gameId, e.getMessage(), e);
        }
    }

    // ==================== TRADE REPLAY WITH STATS (self-contained) ====================

    /** Lightweight container for replay results */
    private static class ReplayResult {
        double finalEquity;
        double peakEquity;
        double maxDrawdown;
        int totalTrades;
        int profitableTrades;
        double finalScore;
    }

    /**
     * Replays all trades for a player to calculate final balance AND risk stats.
     * Self-contained to avoid circular dependency with MatchTradeService.
     */
    @SuppressWarnings("null")
    private ReplayResult replayWithStats(long gameId, long userId,
                                          double startingBalance, double currentPrice) {
        List<Trade> trades = tradeRepository.findByGameIdAndUserId(gameId, userId);

        double cash = startingBalance;
        Map<String, Integer> longs = new HashMap<>();
        Map<String, Integer> shorts = new HashMap<>();
        Map<String, Double> avgShortPrice = new HashMap<>();

        double peakEquity = startingBalance;
        double maxDrawdown = 0.0;
        int totalTrades = 0;
        int profitableTrades = 0;

        for (Trade t : trades) {
            double cost = t.getQuantity() * t.getPrice();
            String sym = t.getSymbol();
            totalTrades++;

            switch (t.getType().toUpperCase()) {
                case "BUY"   -> { cash -= cost; longs.merge(sym, t.getQuantity(), Integer::sum); }
                case "SELL"  -> { cash += cost; longs.merge(sym, -t.getQuantity(), Integer::sum); }
                case "SHORT" -> {
                    cash += cost;
                    int prev = shorts.getOrDefault(sym, 0);
                    double prevAvg = avgShortPrice.getOrDefault(sym, 0.0);
                    int newQty = prev + t.getQuantity();
                    avgShortPrice.put(sym, (prevAvg * prev + cost) / newQty);
                    shorts.merge(sym, t.getQuantity(), Integer::sum);
                }
                case "COVER" -> {
                    cash -= cost;
                    double avgEntry = avgShortPrice.getOrDefault(sym, t.getPrice());
                    if (t.getPrice() < avgEntry) profitableTrades++;
                    int newQty = shorts.getOrDefault(sym, 0) - t.getQuantity();
                    if (newQty <= 0) { shorts.remove(sym); avgShortPrice.remove(sym); }
                    else shorts.put(sym, newQty);
                }
            }

            // Snapshot equity after each trade
            double equity = cash;
            for (int q : longs.values())  equity += q * t.getPrice();
            for (int q : shorts.values()) equity -= q * t.getPrice();

            if ("SELL".equalsIgnoreCase(t.getType()) && equity > peakEquity) {
                profitableTrades++;
            }
            if (equity > peakEquity) peakEquity = equity;
            if (peakEquity > 0) {
                double dd = (peakEquity - equity) / peakEquity;
                if (dd > maxDrawdown) maxDrawdown = dd;
            }
        }

        // Final equity at the closing price
        double total = cash;
        for (int qty : longs.values())  total += qty * currentPrice;
        for (int qty : shorts.values()) total -= qty * currentPrice;

        if (total > peakEquity) peakEquity = total;

        ReplayResult r = new ReplayResult();
        r.finalEquity = total;
        r.peakEquity = peakEquity;
        r.maxDrawdown = maxDrawdown;
        r.totalTrades = totalTrades;
        r.profitableTrades = profitableTrades;
        return r;
    }

    /** Persist (or update) a MatchStats row for a player in a game */
    private void persistStats(long gameId, long userId, ReplayResult r, double finalScore) {
        MatchStats stats = matchStatsRepository
                .findByGameIdAndUserId(gameId, userId)
                .orElse(new MatchStats());

        stats.setGameId(gameId);
        stats.setUserId(userId);
        stats.setPeakEquity(r.peakEquity);
        stats.setMaxDrawdown(r.maxDrawdown);
        stats.setTotalTrades(r.totalTrades);
        stats.setProfitableTrades(r.profitableTrades);
        stats.setFinalEquity(r.finalEquity);
        stats.setFinalScore(finalScore);

        matchStatsRepository.save(stats);
    }

    // ==================== QUERIES ====================

    public boolean isRunning(long gameId) {
        ScheduledFuture<?> f = runningTasks.get(gameId);
        return f != null && !f.isCancelled() && !f.isDone();
    }

    public int activeGameCount() {
        return (int) runningTasks.values().stream()
                .filter(f -> !f.isCancelled() && !f.isDone())
                .count();
    }
}