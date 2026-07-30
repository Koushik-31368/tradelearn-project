package com.tradelearn.server.websocket;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.tradelearn.server.websocket.config.WebSocketEventListener;
import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.GameStatus;
import com.tradelearn.server.game.model.Trade;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.market.service.CandleService;
import com.tradelearn.server.auth.security.WebSocketChannelInterceptor;
import com.tradelearn.server.infrastructure.resilience.GameFreezeService;
import com.tradelearn.server.infrastructure.scheduling.GameMetricsService;
import com.tradelearn.server.infrastructure.resilience.GracefulDegradationManager;
import com.tradelearn.server.game.service.MatchTradeService;
import com.tradelearn.fairness.adapter.TradeLearnEpochAdapter;
import com.tradelearn.fairness.engine.EngineQueueResult;
import com.tradelearn.server.game.epoch.EpochTradeQueue;
import com.tradelearn.server.infrastructure.redis.store.PositionSnapshotStore;
import com.tradelearn.server.infrastructure.redis.room.RoomManager;
import com.tradelearn.server.infrastructure.ratelimit.TradeProcessingPipeline;
import com.tradelearn.server.infrastructure.ratelimit.TradeRateLimiter;
import com.tradelearn.server.common.util.GameLogger;

/**
 * WebSocket STOMP message handlers for in-game communication.
 *
 * User identity is ALWAYS extracted from the authenticated Principal
 * (set during WebSocket handshake JWT validation). The client's playerId
 * in the payload is ignored for security.
 */
@Controller
public class GameWebSocketHandler {

    // ===== PUBLIC DTOs =====

    public static class TradeAction {
        public String type;
        public int amount;
        public double price;
        public long playerId;       // kept for backward compat — IGNORED server-side
        public String symbol;
    }

    public static class PlayerStateSnapshot {
        public double cash;
        public Map<String, Integer> longShares;
        public Map<String, Integer> shortShares;

        public PlayerStateSnapshot(MatchTradeService.PlayerPosition pos) {
            this.cash = pos.cash;
            this.longShares = pos.shares;
            this.shortShares = pos.shortShares;
        }
    }

    public static class GameStateSnapshot {
        public PlayerStateSnapshot player1;
        public PlayerStateSnapshot player2;
    }

    // ===== DEPENDENCIES =====

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameBroadcaster broadcaster;
    private final GameRepository gameRepository;
    private final MatchTradeService matchTradeService;
    private final WebSocketEventListener wsEventListener;
    private final RoomManager roomManager;
    private final TradeRateLimiter rateLimiter;
    private final PositionSnapshotStore positionStore;
    private final GameMetricsService metrics;
    private final GracefulDegradationManager degradationManager;
    private final GameFreezeService freezeService;
    private final TradeProcessingPipeline tradePipeline;
    // ── Fairness mechanism: candle-epoch isolation (via EpochLockstepEngine) ──
    private final TradeLearnEpochAdapter epochGate;
    private final CandleService candleService;

    public GameWebSocketHandler(GameBroadcaster broadcaster,
                                GameRepository gameRepository,
                                MatchTradeService matchTradeService,
                                WebSocketEventListener wsEventListener,
                                RoomManager roomManager,
                                TradeRateLimiter rateLimiter,
                                PositionSnapshotStore positionStore,
                                GameMetricsService metrics,
                                GracefulDegradationManager degradationManager,
                                GameFreezeService freezeService,
                                TradeProcessingPipeline tradePipeline,
                                TradeLearnEpochAdapter epochGate,
                                CandleService candleService) {
        this.broadcaster = broadcaster;
        this.gameRepository = gameRepository;
        this.matchTradeService = matchTradeService;
        this.wsEventListener = wsEventListener;
        this.roomManager = roomManager;
        this.rateLimiter = rateLimiter;
        this.positionStore = positionStore;
        this.metrics = metrics;
        this.degradationManager = degradationManager;
        this.freezeService = freezeService;
        this.tradePipeline = tradePipeline;
        this.epochGate = epochGate;
        this.candleService = candleService;
    }

    // ===== HELPER: extract authenticated userId from Principal =====

    private long getAuthenticatedUserId(SimpMessageHeaderAccessor headerAccessor) {
        // 1. Try Principal (set by WebSocketChannelInterceptor)
        Principal principal = headerAccessor.getUser();
        if (principal instanceof WebSocketChannelInterceptor.StompPrincipal sp) {
            return sp.userId();
        }

        // 2. Fallback: session attributes (set by WebSocketAuthInterceptor)
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null) {
            Long userId = (Long) sessionAttrs.get("userId");
            if (userId != null) return userId;
        }

        throw new SecurityException("No authenticated user on WebSocket session");
    }

    // ===== READY HANDLER =====

    @MessageMapping("/game/{gameId}/ready")
    public void playerReady(@DestinationVariable long gameId) {
        boolean allReady = roomManager.markReady(gameId);
        if (allReady) {
            broadcaster.sendToGame(gameId, "nextRound", "NEXT_ROUND");
        }
    }

    // ===== TRADE HANDLER =====

    @MessageMapping("/game/{gameId}/trade")
    public void handleTrade(
            @DestinationVariable long gameId,
            @Payload TradeAction trade,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        // Extract authenticated user — NEVER trust client's playerId
        long playerId;
        try {
            playerId = getAuthenticatedUserId(headerAccessor);
        } catch (SecurityException e) {
            log.warn("[WS] Unauthenticated trade attempt on game {}", gameId);
            return;
        }

        GameLogger.setGameContext(gameId);
        GameLogger.setUserContext(playerId);

        try {
            // ── Trade payload validation (prevent malformed/malicious input) ──
            String validationError = validateTradePayload(trade);
            if (validationError != null) {
                log.warn("[WS] Invalid trade payload from player {} in game {}: {}",
                        playerId, gameId, validationError);
                broadcaster.sendErrorToUser(playerId, gameId, validationError);
                return;
            }

            // ── Disaster recovery: block trades during system degradation ──
            if (degradationManager.areTradesBlocked()) {
                broadcaster.sendErrorToUser(playerId, gameId,
                        "Trading temporarily suspended — system maintenance in progress");
                return;
            }

            // ── Game freeze check ──
            if (freezeService.isFrozen(gameId)) {
                broadcaster.sendErrorToUser(playerId, gameId,
                        "Game is temporarily paused. Your positions are safe.");
                return;
            }

            // Register session for disconnect tracking
            String sessionId = headerAccessor.getSessionId();
            if (sessionId != null) {
                wsEventListener.registerSession(sessionId, playerId, gameId);
            }

            // ── Rate limit check (5 trades/sec per player per game) ──
            if (!rateLimiter.tryConsume(gameId, playerId)) {
                log.debug("[WS] Rate limited: player {} in game {}", playerId, gameId);
                metrics.recordTradeRejectedRateLimit();
                broadcaster.sendErrorToUser(playerId, gameId, "Rate limited — max 5 trades per second");
                return;
            }

            Optional<Game> gameOpt = gameRepository.findById(gameId);
            if (gameOpt.isEmpty()) {
                GameLogger.logError(log, "handleTrade", gameId,
                        new IllegalArgumentException("Game not found"),
                        Map.of("playerId", playerId));
                return;
            }

            Game game = gameOpt.get();
            if (!GameStatus.ACTIVE.equals(game.getStatus())) {
                GameLogger.logTradeRejected(log, gameId, playerId,
                        trade.type, trade.amount, "Game is not ACTIVE");
                return;
            }

            // Verify participant
            if (!game.getCreator().getId().equals(playerId) &&
                    (game.getOpponent() == null || !game.getOpponent().getId().equals(playerId))) {
                GameLogger.logTradeRejected(log, gameId, playerId,
                        trade.type, trade.amount, "Player is not a participant");
                return;
            }

            String symbol = (trade.symbol != null && !trade.symbol.isBlank())
                    ? trade.symbol
                    : game.getStockSymbol();

            long opponentId = -1L;
            if (game.getOpponent() != null) {
                opponentId = game.getOpponent().getId();
            }

            // ── FAIRNESS MECHANISM: Candle-Epoch Trade Isolation ──────────────
            // Read the current candle epoch HERE, at WebSocket-receipt time,
            // before any thread hand-off or DB round-trip. This is the epoch
            // the player was watching when they decided to trade — regardless
            // of network latency differences between players.
            //
            // The trade is queued in the CandleEpochGate. It will be settled
            // at end-of-epoch (just before the candle advances), at the same
            // price for all players who traded in this window. No player can
            // gain an advantage by having lower latency to the server.
            // ─────────────────────────────────────────────────────────────────
            if (epochGate.getCurrentEpoch(gameId) >= 0) {
                // Epoch gate is active for this game → use epoch-isolation path
                int epoch = game.getCurrentCandleIndex();
                long receivedNanos = System.nanoTime(); // capture before any async work

                EpochTradeQueue qt = new EpochTradeQueue(
                        gameId, playerId, symbol,
                        trade.type.toUpperCase(), trade.amount,
                        epoch, receivedNanos);

                EngineQueueResult queueResult = epochGate.queueTrade(qt);
                switch (queueResult) {
                    case ACCEPTED -> {
                        // Acknowledge to the client that the order is queued for this candle
                        broadcaster.sendToGame(gameId, "order-queued", Map.of(
                                "userId", playerId,
                                "epoch", epoch,
                                "type", trade.type.toUpperCase(),
                                "quantity", trade.amount,
                                "symbol", symbol,
                                "message", "Order queued — will settle at candle close"
                        ));
                        metrics.recordTrade();
                    }
                    case STALE_EPOCH -> broadcaster.sendErrorToUser(playerId, gameId,
                            "Order rejected: candle has already advanced. Please resubmit.");
                    case QUEUE_FULL -> broadcaster.sendErrorToUser(playerId, gameId,
                            "Too many orders queued for this candle — please slow down.");
                    case SESSION_NOT_FOUND -> broadcaster.sendErrorToUser(playerId, gameId,
                            "Game not active in fairness gate — please rejoin.");
                }
            } else {
                // Fallback: epoch gate not initialized (e.g. server restart mid-game)
                // Use legacy direct-execution path to avoid losing the trade.
                final long finalOpponentId = opponentId;
                MatchTradeRequest req = new MatchTradeRequest();
                req.setGameId(gameId);
                req.setUserId(playerId);
                req.setSymbol(symbol);
                req.setType(trade.type);
                req.setQuantity(trade.amount);

                TradeProcessingPipeline.SubmitResult result = tradePipeline.submitTrade(gameId, () -> {
                    try {
                        Trade saved = matchTradeService.placeTrade(req);
                        tradePipeline.submitBroadcast(() -> {
                            broadcaster.sendToGame(gameId, "trade", saved);
                            if (finalOpponentId > 0) {
                                Map<String, Object> scoreboard = positionStore.buildScoreboardPayload(
                                        gameId, req.getUserId(), finalOpponentId, saved.getPrice());
                                broadcaster.sendToGame(gameId, "scoreboard", scoreboard);
                            }
                        });
                    } catch (Exception e) {
                        GameLogger.logError(log, "handleTrade - placeTrade (legacy)", gameId, e, Map.of(
                                "playerId", req.getUserId(), "type", req.getType(),
                                "amount", req.getQuantity(), "symbol", req.getSymbol()));
                        broadcaster.sendErrorToUser(req.getUserId(), gameId, e.getMessage());
                    }
                });
                if (result == TradeProcessingPipeline.SubmitResult.REJECTED ||
                        result == TradeProcessingPipeline.SubmitResult.SHUTDOWN) {
                    broadcaster.sendErrorToUser(playerId, gameId, "Server at capacity — trade not processed");
                }
            }

        } finally {
            GameLogger.clearContext();
        }
    }

    // ===== POSITION QUERY =====

    @MessageMapping("/game/{gameId}/position")
    public void getPosition(
            @DestinationVariable long gameId,
            @Payload Map<String, Long> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        // Use authenticated userId, ignore payload
        long userId;
        try {
            userId = getAuthenticatedUserId(headerAccessor);
        } catch (SecurityException e) {
            return;
        }

        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        MatchTradeService.PlayerPosition position =
                matchTradeService.getPlayerPosition(gameId, userId, game.getStartingBalance());

        broadcaster.sendLocal(
                "/topic/game/" + gameId + "/position/" + userId,
                new PlayerStateSnapshot(position)
        );
    }

    // ===== REJOIN HANDLER (reconnection grace period) =====

    @MessageMapping("/game/{gameId}/rejoin")
    public void handleRejoin(
            @DestinationVariable long gameId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        long userId;
        try {
            userId = getAuthenticatedUserId(headerAccessor);
        } catch (SecurityException e) {
            return;
        }

        // Register the new session
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null) {
            wsEventListener.registerSession(sessionId, userId, gameId);
        }

        // Check if this is a reconnection from grace period
        if (roomManager.hasRoom(gameId) && roomManager.isDisconnected(gameId, userId)) {
            roomManager.clearDisconnected(gameId, userId);
            java.util.concurrent.ScheduledFuture<?> timer = roomManager.removeReconnectTimer(gameId, userId);
            if (timer != null) timer.cancel(false);

            log.info("[WS] Player {} reconnected to game {} (grace period)", userId, gameId);

            metrics.recordReconnectSuccess();

            broadcaster.sendToGame(gameId, "player-reconnected",
                    Map.of(
                            "gameId", gameId,
                            "reconnectedUserId", userId,
                            "message", "Player reconnected!"
                    )
            );
        } else {
            log.debug("[WS] Player {} joined game {} (normal join, not reconnection)", userId, gameId);

            // If the game is already ACTIVE, the player may have missed the initial
            // "started" broadcast (e.g. brief WS disconnect during the waiting phase).
            // Re-broadcast to the game topic so they transition out of WAITING.
            Game game = gameRepository.findById(gameId).orElse(null);
            if (game != null && GameStatus.ACTIVE.equals(game.getStatus())) {
                boolean isCreator  = game.getCreator() != null && game.getCreator().getId().equals(userId);
                boolean isOpponent = game.getOpponent() != null && game.getOpponent().getId().equals(userId);
                if (isCreator || isOpponent) {
                    Long   opponentId       = isCreator
                            ? (game.getOpponent() != null ? game.getOpponent().getId() : null)
                            : game.getCreator().getId();
                    String opponentUsername = isCreator
                            ? (game.getOpponent() != null ? game.getOpponent().getUsername() : "")
                            : (game.getCreator() != null  ? game.getCreator().getUsername()  : "");
                    log.info("[WS] Game {} is already ACTIVE — resending 'started' event for player {}",
                            gameId, userId);
                    broadcaster.sendToGame(gameId, "started", Map.of(
                            "gameId",           gameId,
                            "status",           "ACTIVE",
                            "opponentId",       opponentId != null ? opponentId : -1L,
                            "opponentUsername", opponentUsername
                    ));
                }
            }
        }
    }

    // ===== TRADE PAYLOAD VALIDATION =====

    /** Allowed trade types */
    private static final java.util.Set<String> VALID_TRADE_TYPES =
            java.util.Set.of("BUY", "SELL", "SHORT", "COVER");

    /** Max symbol length to prevent oversized payloads */
    private static final int MAX_SYMBOL_LENGTH = 20;

    /** Max trade quantity per single order */
    private static final int MAX_TRADE_QUANTITY = 100_000;

    /**
     * Validate trade payload fields for safety and correctness.
     * Returns null if valid, or an error message string if invalid.
     *
     * <p>Checks performed:</p>
     * <ul>
     *   <li>Trade type must be one of: BUY, SELL, SHORT, COVER</li>
     *   <li>Amount must be positive and within bounds</li>
     *   <li>Symbol (if provided) must be alphanumeric, no special chars, bounded length</li>
     *   <li>No SQL injection or script patterns in string fields</li>
     * </ul>
     */
    private static String validateTradePayload(TradeAction trade) {
        if (trade == null) {
            return "Trade payload is required";
        }

        // Type validation
        if (trade.type == null || trade.type.isBlank()) {
            return "Trade type is required";
        }
        if (!VALID_TRADE_TYPES.contains(trade.type.toUpperCase())) {
            return "Invalid trade type: " + sanitize(trade.type)
                    + " (must be BUY, SELL, SHORT, or COVER)";
        }

        // Amount validation
        if (trade.amount <= 0) {
            return "Trade amount must be positive";
        }
        if (trade.amount > MAX_TRADE_QUANTITY) {
            return "Trade amount exceeds maximum (" + MAX_TRADE_QUANTITY + ")";
        }

        // Symbol validation (optional — handler uses game's symbol as fallback)
        if (trade.symbol != null && !trade.symbol.isBlank()) {
            if (trade.symbol.length() > MAX_SYMBOL_LENGTH) {
                return "Symbol too long (max " + MAX_SYMBOL_LENGTH + " characters)";
            }
            // Only allow alphanumeric, dots, hyphens (e.g., "RELIANCE.NS", "TCS")
            if (!trade.symbol.matches("^[A-Za-z0-9._-]+$")) {
                return "Symbol contains invalid characters";
            }
        }

        return null; // Valid
    }

    /**
     * Sanitize a string for safe logging (prevent log injection).
     */
    private static String sanitize(String input) {
        if (input == null) return "null";
        return input.replaceAll("[^A-Za-z0-9_-]", "?").substring(0, Math.min(input.length(), 20));
    }
}
