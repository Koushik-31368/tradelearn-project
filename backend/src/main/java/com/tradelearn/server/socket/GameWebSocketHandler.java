package com.tradelearn.server.socket;

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
import com.tradelearn.server.service.MatchTradeService;
import com.tradelearn.server.service.RoomManager;
import com.tradelearn.server.util.GameLogger;

/**
 * WebSocket STOMP message handlers for in-game communication.
 *
 * Handles:
 *   - Player ready-up (pre-round synchronization)
 *   - Trade placement (BUY/SELL/SHORT/COVER via WebSocket)
 *   - Position queries (real-time portfolio state)
 *
 * All trade execution delegates to MatchTradeService for validation
 * and persistence. Broadcasts use GameBroadcaster for cross-instance
 * delivery in multi-server deployments.
 */
@Controller
public class GameWebSocketHandler {

    // ===== PUBLIC DTOs =====

    public static class TradeAction {
        public String type;
        public int amount;
        public double price;
        public long playerId;
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

    public GameWebSocketHandler(GameBroadcaster broadcaster,
                                GameRepository gameRepository,
                                MatchTradeService matchTradeService,
                                WebSocketEventListener wsEventListener,
                                RoomManager roomManager) {
        this.broadcaster = broadcaster;
        this.gameRepository = gameRepository;
        this.matchTradeService = matchTradeService;
        this.wsEventListener = wsEventListener;
        this.roomManager = roomManager;
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
        GameLogger.setGameContext(gameId);
        GameLogger.setUserContext(trade.playerId);

        try {
            // Register session for disconnect tracking
            String sessionId = headerAccessor.getSessionId();
            if (sessionId != null && trade.playerId > 0) {
                wsEventListener.registerSession(sessionId, trade.playerId, gameId);
            }

            Optional<Game> gameOpt = gameRepository.findById(gameId);
            if (gameOpt.isEmpty()) {
                GameLogger.logError(log, "handleTrade", gameId,
                        new IllegalArgumentException("Game not found"),
                        Map.of("playerId", trade.playerId));
                return;
            }

            Game game = gameOpt.get();
            if (!"ACTIVE".equals(game.getStatus())) {
                GameLogger.logTradeRejected(log, gameId, trade.playerId,
                        trade.type, trade.amount, "Game is not ACTIVE");
                return;
            }

            // Verify participant
            Long playerId = trade.playerId;
            if (!game.getCreator().getId().equals(playerId) &&
                    (game.getOpponent() == null || !game.getOpponent().getId().equals(playerId))) {
                GameLogger.logTradeRejected(log, gameId, playerId,
                        trade.type, trade.amount, "Player is not a participant");
                return;
            }

            String symbol = (trade.symbol != null && !trade.symbol.isBlank())
                    ? trade.symbol
                    : game.getStockSymbol();

            // Persist via MatchTradeService
            try {
                MatchTradeRequest req = new MatchTradeRequest();
                req.setGameId(gameId);
                req.setUserId(playerId);
                req.setSymbol(symbol);
                req.setType(trade.type);
                req.setQuantity(trade.amount);

                Trade saved = matchTradeService.placeTrade(req);

                // Broadcast updated state
                GameStateSnapshot snapshot = buildGameState(game);
                broadcaster.sendToGame(gameId, "state", snapshot);
                broadcaster.sendToGame(gameId, "trade", saved);

            } catch (Exception e) {
                GameLogger.logError(log, "handleTrade - placeTrade", gameId, e, Map.of(
                        "playerId", playerId,
                        "type", trade.type,
                        "amount", trade.amount,
                        "symbol", symbol
                ));
                broadcaster.sendErrorToUser(playerId, gameId, e.getMessage());
            }

        } finally {
            GameLogger.clearContext();
        }
    }

    // ===== POSITION QUERY =====

    @MessageMapping("/game/{gameId}/position")
    public void getPosition(
            @DestinationVariable long gameId,
            @Payload Map<String, Long> payload
    ) {
        Long userId = payload.get("userId");
        if (userId == null) return;

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
}
