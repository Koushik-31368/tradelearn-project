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
    private final PositionSnapshotStore positionStore;
    private final TradeRateLimiter rateLimiter;
    private final GameMetricsService metrics;
    private final GameFreezeService freezeService;

    /** ScheduledFuture per game — the ONLY in-memory per-game state */
    private final Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    public MatchSchedulerService(TaskScheduler taskScheduler,
                                 GameRepository gameRepository,
                                 TradeRepository tradeRepository,
                                 CandleService candleService,
                                 GameBroadcaster broadcaster,
                                 MatchStatsRepository matchStatsRepository,
                                 UserRepository userRepository,
                                 RoomManager roomManager,
                                 PositionSnapshotStore positionStore,
                                 TradeRateLimiter rateLimiter,
                                 GameMetricsService metrics,
                                 GameFreezeService freezeService) {
        this.taskScheduler = taskScheduler;
        this.gameRepository = gameRepository;
        this.tradeRepository = tradeRepository;
        this.candleService = candleService;
        this.broadcaster = broadcaster;
        this.matchStatsRepository = matchStatsRepository;
        this.userRepository = userRepository;
        this.roomManager = roomManager;
        this.positionStore = positionStore;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.freezeService = freezeService;
    }

    // ==================== START / STOP ====================

    /**
     * Begin candle progression for a game that just became ACTIVE.
     * Immediately broadcasts the first candle, then ticks every 5s.
     *
     * <p><b>Cluster-safe:</b> Uses Redis-based scheduler ownership
     * ({@link RoomManager#tryClaimScheduler(long)}) to guarantee that
     * exactly ONE instance runs the tick for a given game, even in a
     * multi-instance deployment behind a load balancer.</p>
     *
     * Uses ConcurrentHashMap.computeIfAbsent locally to prevent
     * duplicate schedulers on the same instance.
     */
    public void startProgression(long gameId) {
        // ── Distributed guard: only the claiming instance starts the scheduler ──
        if (!roomManager.tryClaimScheduler(gameId)) {
            log.info("[Scheduler] Another instance owns the scheduler for game {} — skipping", gameId);
            return;
        }

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
     * Also releases the distributed scheduler ownership key in Redis.
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

        // Release distributed scheduler ownership
        roomManager.releaseScheduler(gameId);
    }

    // ==================== TICK ====================

    @SuppressWarnings("null")
    private void tick(long gameId) {
        long startNanos = System.nanoTime();
        GameLogger.setGameContext(gameId);
        
        try {
            // ── Freeze check: skip tick if game is frozen (DR) ──
            if (freezeService.isFrozen(gameId)) {
                log.debug("[Tick] Game {} is frozen — skipping tick", gameId);
                return;
            }

            // ── Refresh distributed scheduler ownership TTL ──
            roomManager.refreshSchedulerOwnership(gameId);

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

            // Broadcast updated scoreboard with new mark price
            broadcastScoreboard(game, nextCandle.getClose());

            metrics.recordCandleTickTime(System.nanoTime() - startNanos);

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

            // Use position snapshots (O(1)) with fallback to trade replay
            positionStore.updateMarkPrice(gameId, game.getCreator().getId(), finalPrice);
            positionStore.updateMarkPrice(gameId, game.getOpponent().getId(), finalPrice);

            MatchTradeService.PlayerPosition creatorPos = getOrReplayPosition(
                    gameId, game.getCreator().getId(), game.getStartingBalance());
            MatchTradeService.PlayerPosition opponentPos = getOrReplayPosition(
                    gameId, game.getOpponent().getId(), game.getStartingBalance());

            double creatorBal = PositionSnapshotStore.snapshotEquity(creatorPos, finalPrice);
            double opponentBal = PositionSnapshotStore.snapshotEquity(opponentPos, finalPrice);

            // Calculate hybrid scores
            double creatorScore = ScoringUtil.calculate(
                    creatorBal, game.getStartingBalance(),
                    creatorPos.maxDrawdown, creatorPos.totalTrades, creatorPos.profitableTrades);
            double opponentScore = ScoringUtil.calculate(
                    opponentBal, game.getStartingBalance(),
                    opponentPos.maxDrawdown, opponentPos.totalTrades, opponentPos.profitableTrades);

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
            persistStatsFromPosition(gameId, game.getCreator().getId(), creatorPos, creatorBal, creatorScore);
            persistStatsFromPosition(gameId, game.getOpponent().getId(), opponentPos, opponentBal, opponentScore);

            candleService.evict(gameId);

            // Evict position snapshots and rate limiter buckets
            positionStore.evictGame(gameId);
            rateLimiter.evictGame(gameId);

            // Broadcast result with stats
            double startBal = game.getStartingBalance();
            Object winnerId = game.getWinner() != null ? game.getWinner().getId() : "draw";

            Map<String, Object> payload = new HashMap<>();
            payload.put("gameId", gameId);
            payload.put("status", "FINISHED");
            payload.put("creatorId", game.getCreator().getId());
            payload.put("creatorFinalBalance", creatorBal);
            payload.put("creatorProfit", creatorBal - startBal);
            payload.put("creatorPeakEquity", creatorPos.peakEquity);
            payload.put("creatorMaxDrawdown", creatorPos.maxDrawdown);
            payload.put("creatorTotalTrades", creatorPos.totalTrades);
            payload.put("creatorProfitableTrades", creatorPos.profitableTrades);
            payload.put("opponentId", game.getOpponent().getId());
            payload.put("opponentFinalBalance", opponentBal);
            payload.put("opponentProfit", opponentBal - startBal);
            payload.put("opponentPeakEquity", opponentPos.peakEquity);
            payload.put("opponentMaxDrawdown", opponentPos.maxDrawdown);
            payload.put("opponentTotalTrades", opponentPos.totalTrades);
            payload.put("opponentProfitableTrades", opponentPos.profitableTrades);
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

            metrics.recordMatchCompleted();

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

    /** Persist (or update) a MatchStats row for a player in a game (legacy ReplayResult) */
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

    /** Persist stats from a PlayerPosition snapshot */
    private void persistStatsFromPosition(long gameId, long userId,
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

    // ==================== SCOREBOARD BROADCAST ====================

    /**
     * Broadcast updated scoreboard to both players after a candle tick.
     * Uses the PositionSnapshotStore so no trade replays are needed.
     */
    private void broadcastScoreboard(Game game, double markPrice) {
        try {
            if (game.getOpponent() == null) return;
            long p1Id = game.getCreator().getId();
            long p2Id = game.getOpponent().getId();

            Map<String, Object> scoreboard = positionStore.buildScoreboardPayload(
                    game.getId(), p1Id, p2Id, markPrice);
            broadcaster.sendToGame(game.getId(), "scoreboard", scoreboard);
        } catch (Exception e) {
            log.warn("[Tick] Scoreboard broadcast failed for game {}: {}", game.getId(), e.getMessage());
        }
    }

    /**
     * Get position from snapshot store, falling back to trade replay.
     */
    private MatchTradeService.PlayerPosition getOrReplayPosition(
            long gameId, long userId, double startingBalance) {
        MatchTradeService.PlayerPosition pos = positionStore.getPosition(gameId, userId);
        if (pos != null) return pos;

        // Fallback: replay trades (server restart scenario)
        ReplayResult r = replayWithStats(gameId, userId, startingBalance,
                candleService.getCurrentPrice(gameId));

        MatchTradeService.PlayerPosition fallback = new MatchTradeService.PlayerPosition();
        fallback.cash = r.finalEquity; // approximate — includes mark-to-market
        fallback.peakEquity = r.peakEquity;
        fallback.maxDrawdown = r.maxDrawdown;
        fallback.totalTrades = r.totalTrades;
        fallback.profitableTrades = r.profitableTrades;
        return fallback;
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