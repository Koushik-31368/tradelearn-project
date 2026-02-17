package com.tradelearn.server.controller;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.service.MatchTradeService;

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

    private static class GameReadiness {
        private final AtomicInteger readyCount = new AtomicInteger(0);
        int incrementAndGet() { return readyCount.incrementAndGet(); }
        void reset() { readyCount.set(0); }
    }

    // ===== DEPENDENCIES =====

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRepository gameRepository;
    private final MatchTradeService matchTradeService;

    public GameWebSocketController(SimpMessagingTemplate messagingTemplate,
                                   GameRepository gameRepository,
                                   MatchTradeService matchTradeService) {
        this.messagingTemplate = messagingTemplate;
        this.gameRepository = gameRepository;
        this.matchTradeService = matchTradeService;
    }

    private final Map<Long, GameReadiness> readinessMap = new ConcurrentHashMap<>();

    // ===== READY HANDLER =====

    @MessageMapping("/game/{gameId}/ready")
    public void playerReady(@DestinationVariable long gameId) {
        GameReadiness readiness =
                readinessMap.computeIfAbsent(gameId, k -> new GameReadiness());

        if (readiness.incrementAndGet() >= 2) {
            readiness.reset();
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
            @Payload TradeAction trade
    ) {
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