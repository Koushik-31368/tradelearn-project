package com.tradelearn.server.controller;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.tradelearn.server.config.WebSocketEventListener;
import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.service.MatchTradeService;
import com.tradelearn.server.service.RoomManager;

@Controller
public class GameWebSocketController {

    // ===== PUBLIC STATIC DTOs =====

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

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRepository gameRepository;
    private final MatchTradeService matchTradeService;
    private final WebSocketEventListener wsEventListener;
    private final RoomManager roomManager;

    public GameWebSocketController(SimpMessagingTemplate messagingTemplate,
                                   GameRepository gameRepository,
                                   MatchTradeService matchTradeService,
                                   WebSocketEventListener wsEventListener,
                                   RoomManager roomManager) {
        this.messagingTemplate = messagingTemplate;
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
            messagingTemplate.convertAndSend(
                    "/topic/game/" + gameId + "/nextRound",
                    "NEXT_ROUND"
            );
        }
    }

    // ===== TRADE HANDLER (now persists via MatchTradeService) =====

    @SuppressWarnings("null")
    @MessageMapping("/game/{gameId}/trade")
    public void handleTrade(
            @DestinationVariable long gameId,
            @Payload TradeAction trade,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        // Register this session for disconnect tracking
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null && trade.playerId > 0) {
            wsEventListener.registerSession(sessionId, trade.playerId, gameId);
        }

        log.info("[TRADE] Game {} | Player {} | {} {} shares of {}",
                gameId, trade.playerId, trade.type, trade.amount, trade.symbol);

        @SuppressWarnings("null")
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (!"ACTIVE".equals(game.getStatus())) return;

        // Verify participant
        Long playerId = trade.playerId;
        if (!game.getCreator().getId().equals(playerId) &&
            (game.getOpponent() == null || !game.getOpponent().getId().equals(playerId))) {
            return;
        }

        String symbol = (trade.symbol != null && !trade.symbol.isBlank())
                ? trade.symbol
                : game.getStockSymbol();

        // Persist the trade through MatchTradeService
        try {
            MatchTradeRequest req = new MatchTradeRequest();
            req.setGameId(gameId);
            req.setUserId(playerId);
            req.setSymbol(symbol);
            req.setType(trade.type);
            req.setQuantity(trade.amount);

            Trade saved = matchTradeService.placeTrade(req);

            // Build updated state from DB
            GameStateSnapshot snapshot = buildGameState(game);

            // Broadcast updated state
            messagingTemplate.convertAndSend(
                    "/topic/game/" + gameId + "/state",
                    snapshot
            );

            // Also broadcast the individual trade for the activity feed
            messagingTemplate.convertAndSend(
                    "/topic/game/" + gameId + "/trade",
                    saved
            );

        } catch (IllegalStateException | IllegalArgumentException e) {
            // Send error back to the specific player
            messagingTemplate.convertAndSend(
                    "/topic/game/" + gameId + "/error/" + playerId,
                    Map.of("error", e.getMessage())
            );
        }
    }

    // ===== POSITION QUERY (WebSocket) =====

    @MessageMapping("/game/{gameId}/position")
    public void getPosition(
            @DestinationVariable long gameId,
            @Payload Map<String, Long> payload
    ) {
        Long userId = payload.get("userId");
        if (userId == null) return;

        @SuppressWarnings("null")
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        MatchTradeService.PlayerPosition position =
                matchTradeService.getPlayerPosition(gameId, userId, game.getStartingBalance());

        messagingTemplate.convertAndSend(
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