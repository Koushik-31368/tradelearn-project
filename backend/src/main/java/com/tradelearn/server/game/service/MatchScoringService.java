package com.tradelearn.server.game.service;

import com.tradelearn.server.infrastructure.ratelimit.TradeRateLimiter;
import com.tradelearn.server.infrastructure.redis.room.RoomManager;
import com.tradelearn.server.infrastructure.redis.store.PositionSnapshotStore;
import com.tradelearn.server.infrastructure.scheduling.MatchSchedulerService;
import com.tradelearn.server.market.service.CandleService;
import com.tradelearn.server.quests.service.QuestService;
import com.tradelearn.server.common.util.EloUtil;
import com.tradelearn.server.common.util.GameLogger;
import com.tradelearn.server.common.util.ScoringUtil;
import com.tradelearn.server.dto.MatchResult;
import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.MatchStats;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.game.repository.MatchStatsRepository;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.websocket.GameBroadcaster;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Handles match scoring, ELO updates, abandoned-game finalization, and rematch consent.
 *
 * <p>This service is intentionally stateless — all state lives in the DB or Redis.
 * The {@link QuestService} dependency is {@code @Lazy} to break a circular dependency
 * chain: QuestService → MatchService (for game outcome data) → QuestService.
 *
 * <h3>Separated concerns</h3>
 * <ul>
 *   <li>{@link MatchLifecycleService} — create/join/start/cancel matches</li>
 *   <li>{@link MatchQueryService} — pure read-only queries</li>
 * </ul>
 */
@Service
public class MatchScoringService {

    private static final Logger log = LoggerFactory.getLogger(MatchScoringService.class);

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
    private final QuestService questService;

    /** Optional — null when redis.enabled is not set (MVP / no-Redis deployment). */
    @Autowired(required = false)
    private StringRedisTemplate redis;

    public MatchScoringService(GameRepository gameRepository,
                               UserRepository userRepository,
                               @Lazy MatchTradeService matchTradeService,
                               CandleService candleService,
                               MatchSchedulerService matchSchedulerService,
                               MatchStatsRepository matchStatsRepository,
                               GameBroadcaster broadcaster,
                               RoomManager roomManager,
                               PositionSnapshotStore positionStore,
                               TradeRateLimiter rateLimiter,
                               @Lazy QuestService questService) {
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
        this.questService = questService;
    }

    // ==================== END MATCH & CALCULATE WINNER ====================

    /**
     * Score and finalize an active match that has naturally ended.
     * Calculates composite scores, applies ELO updates, and persists everything.
     *
     * <p>Side effects (Redis evictions) are deferred to afterCommit to guarantee
     * consistency: we never evict Redis state if the DB transaction rolls back.
     *
     * @param gameId the game to end
     * @return full match result DTO (populated from entity data while still inside TX)
     */
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

        if (winner != null) {
            if (winner.getId().equals(creator.getId())) {
                creator.setWins(creator.getWins() + 1);
                opponent.setLosses(opponent.getLosses() + 1);
            } else {
                opponent.setWins(opponent.getWins() + 1);
                creator.setLosses(creator.getLosses() + 1);
            }
        }

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

        if (winner != null) {
            try {
                questService.updateChallengeProgress(winner.getId(), "WIN_MATCHES", 1);
            } catch (Exception e) {
                // Non-critical: quest progress failure must never break match finalization
                log.warn("[endMatch] Quest progress update failed for winner {}: {}", winner.getId(), e.getMessage());
            }
        }

        // Build result DTO while inside TX (entities are available)
        MatchResult result = buildMatchResult(game, creatorPos, opponentPos,
                creatorBalance, opponentBalance, creatorScore, opponentScore,
                creatorRatingDelta, opponentRatingDelta, creatorNewRating, opponentNewRating, winner);

        // Schedule Redis evictions AFTER DB commit to prevent stale state on rollback
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
                    log.error("[endMatch] afterCommit side effects failed for game {}: {}",
                            gameId, e.getMessage(), e);
                }
            }
        });

        return result;
    }

    // ==================== FORCE FINISH ON ABANDON ====================

    /**
     * Score and rate a game where a player disconnected past the grace period.
     * The remaining (connected) player wins. Full scoring + ELO update applied.
     *
     * <p>Called from {@code WebSocketEventListener.doAbandon()} — the transactional
     * boundary ensures all DB mutations are atomic.
     *
     * @param gameId             the game to finalize
     * @param disconnectedUserId the user who abandoned (always the loser)
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

        // Disconnected player always loses
        boolean disconnectedIsCreator = game.getCreator().getId().equals(disconnectedUserId);
        User winner = disconnectedIsCreator ? game.getOpponent() : game.getCreator();

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

        if (winner.getId().equals(creator.getId())) {
            creator.setWins(creator.getWins() + 1);
            opponent.setLosses(opponent.getLosses() + 1);
        } else {
            opponent.setWins(opponent.getWins() + 1);
            creator.setLosses(creator.getLosses() + 1);
        }

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

        try {
            questService.updateChallengeProgress(winner.getId(), "WIN_MATCHES", 1);
        } catch (Exception e) {
            log.warn("[forceFinishOnAbandon] Quest progress update failed for winner {}: {}", winner.getId(), e.getMessage());
        }

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

    // ==================== REMATCH ====================

    /**
     * Request a rematch for a finished/abandoned game.
     *
     * <p>Uses Redis SETNX + Lua for distributed race-free mutual consent:
     * <ul>
     *   <li>First caller → PENDING (rematch:{oldGameId} set with TTL 120s, opponent notified via WS)</li>
     *   <li>Second caller → ACCEPTED (key deleted atomically, new ACTIVE game created)</li>
     *   <li>Same user twice → idempotent PENDING</li>
     * </ul>
     */
    @SuppressWarnings("null")
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

        if (redis == null) {
            return Map.of("status", "UNAVAILABLE",
                    "message", "Rematch requires Redis — not available in this deployment");
        }

        String rematchKey = REMATCH_PREFIX + oldGameId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(REMATCH_CHECK_LUA, Long.class);
        Long result = redis.execute(script,
                Collections.singletonList(rematchKey),
                String.valueOf(userId),
                String.valueOf(REMATCH_TTL.getSeconds()));

        if (result == null || result == -1) {
            broadcaster.sendToUser(opponentId, "rematch-request", Map.of(
                "oldGameId", oldGameId,
                "requesterId", userId,
                "message", "Your opponent wants a rematch!"
            ));
            return Map.of("status", "PENDING");
        } else if (result == -2) {
            return Map.of("status", "PENDING");
        } else {
            // Mutual consent — create the new game
            String symbol = MatchLifecycleService.RANKED_SYMBOLS[
                ThreadLocalRandom.current().nextInt(MatchLifecycleService.RANKED_SYMBOLS.length)];

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

            // Import lifecycle dependencies to initialize room
            // (accessed via MatchLifecycleService — we have positionStore, candleService, etc.)
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

    // ==================== STATS PERSISTENCE ====================

    /**
     * Persist or update MatchStats for one player in a game.
     * If a record already exists (e.g. from a partial end attempt), it is updated.
     */
    void persistStats(long gameId, long userId,
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

    // ==================== PRIVATE HELPERS ====================

    private MatchResult buildMatchResult(Game game,
                                         MatchTradeService.PlayerPosition creatorPos,
                                         MatchTradeService.PlayerPosition opponentPos,
                                         double creatorBalance, double opponentBalance,
                                         double creatorScore, double opponentScore,
                                         int creatorRatingDelta, int opponentRatingDelta,
                                         int creatorNewRating, int opponentNewRating,
                                         User winner) {
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
}
