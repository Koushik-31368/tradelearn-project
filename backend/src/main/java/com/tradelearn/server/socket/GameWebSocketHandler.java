package com.tradelearn.server.socket;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.tradelearn.server.config.WebSocketEventListener;
import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.security.WebSocketChannelInterceptor;
import com.tradelearn.server.service.GameFreezeService;
import com.tradelearn.server.service.GameMetricsService;
import com.tradelearn.server.service.GracefulDegradationManager;
import com.tradelearn.server.service.MatchTradeService;
import com.tradelearn.server.service.PositionSnapshotStore;
import com.tradelearn.server.service.RoomManager;
import com.tradelearn.server.service.TradeProcessingPipeline;
import com.tradelearn.server.service.TradeRateLimiter;
import com.tradelearn.server.util.GameLogger;

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
                                TradeProcessingPipeline tradePipeline) {
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

    @SuppressWarnings("null")
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
            if (!"ACTIVE".equals(game.getStatus())) {
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

            // ── Submit trade to async pipeline (non-blocking) ──
            final long opponentId = game.getOpponent() != null ? game.getOpponent().getId() : -1;
            final double startingBalance = game.getStartingBalance();

            MatchTradeRequest req = new MatchTradeRequest();
            req.setGameId(gameId);
            req.setUserId(playerId);    // Server-validated userId
            req.setSymbol(symbol);
            req.setType(trade.type);
            req.setQuantity(trade.amount);

            TradeProcessingPipeline.SubmitResult result = tradePipeline.submitTrade(gameId, () -> {
                try {
                    Trade saved = matchTradeService.placeTrade(req);

                    // Broadcast trade event on the broadcast pool
                    tradePipeline.submitBroadcast(() -> {
                        broadcaster.sendToGame(gameId, "trade", saved);

                        // Broadcast updated scoreboard
                        if (opponentId > 0) {
                            Map<String, Object> scoreboard = positionStore.buildScoreboardPayload(
                                    gameId, req.getUserId(), opponentId, saved.getPrice());
                            broadcaster.sendToGame(gameId, "scoreboard", scoreboard);
                        }
                    });
                } catch (Exception e) {
                    GameLogger.logError(log, "handleTrade - placeTrade", gameId, e, Map.of(
                            "playerId", req.getUserId(),
                            "type", req.getType(),
                            "amount", req.getQuantity(),
                            "symbol", req.getSymbol()
                    ));
                    broadcaster.sendErrorToUser(req.getUserId(), gameId, e.getMessage());
                }
            });

            // Handle pipeline rejection
            switch (result) {
                case HEAP_PRESSURE -> {
                    log.warn("[WS] Trade rejected (heap pressure): player {} in game {}", playerId, gameId);
                    broadcaster.sendErrorToUser(playerId, gameId,
                            "Server under load — please retry in a moment");
                }
                case BACKPRESSURE -> {
                    log.warn("[WS] Trade rejected (backpressure): player {} in game {}", playerId, gameId);
                    broadcaster.sendErrorToUser(playerId, gameId,
                            "Too many pending trades for this game — please slow down");
                }
                case REJECTED -> {
                    log.warn("[WS] Trade rejected (queue full): player {} in game {}", playerId, gameId);
                    metrics.recordTradeRejectedRateLimit();
                    broadcaster.sendErrorToUser(playerId, gameId,
                            "Server at capacity — trade not processed");
                }
                case SHUTDOWN -> {
                    broadcaster.sendErrorToUser(playerId, gameId,
                            "Server is shutting down — trade not accepted");
                }
                case ACCEPTED -> {
                    // Trade queued successfully — will be processed async
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
        }
    }

    // ===== HELPERS =====

    private GameStateSnapshot buildGameState(Game game) {
        GameStateSnapshot snapshot = new GameStateSnapshot();

        MatchTradeService.PlayerPosition p1 = matchTradeService.getPlayerPosition(
                game.getId(), game.getCreator().getId(), game.getStartingBalance()
        );
        snapshot.player1 = new PlayerStateSnapshot(p1);

        if (game.getOpponent() != null) {
            MatchTradeService.PlayerPosition p2 = matchTradeService.getPlayerPosition(
                    game.getId(), game.getOpponent().getId(), game.getStartingBalance()
            );
            snapshot.player2 = new PlayerStateSnapshot(p2);
        }

        return snapshot;
    }

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String hello(String msg) {
        return "Hello from server";
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
