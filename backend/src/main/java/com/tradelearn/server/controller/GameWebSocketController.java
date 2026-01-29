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

import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;

@Controller
public class GameWebSocketController {

    // ===== PUBLIC STATIC DTOs (fixes "exporting non-public type") =====

    public static class TradeAction {
        public String type;
        public int amount;
        public double price;
        public long playerId;
    }

    public static class PlayerState {
        public double cash = 1_000_000;
        public int longShares;
        public int shortShares;
        public double avgShortPrice;
    }

    public static class GameState {
        public PlayerState player1 = new PlayerState();
        public PlayerState player2 = new PlayerState();
    }

    private static class GameReadiness {
        private final AtomicInteger readyCount = new AtomicInteger(0);
        int incrementAndGet() { return readyCount.incrementAndGet(); }
        void reset() { readyCount.set(0); }
    }

    // ===== DEPENDENCIES =====

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRepository gameRepository;

    public GameWebSocketController(SimpMessagingTemplate messagingTemplate,
                                   GameRepository gameRepository) {
        this.messagingTemplate = messagingTemplate;
        this.gameRepository = gameRepository;
    }

    private final Map<Long, GameReadiness> readinessMap = new ConcurrentHashMap<>();
    private final Map<Long, GameState> stateMap = new ConcurrentHashMap<>();

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

    // ===== TRADE HANDLER =====

    @MessageMapping("/game/{gameId}/trade")
    public void handleTrade(
            @DestinationVariable long gameId,
            @Payload TradeAction trade
    ) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        GameState state =
                stateMap.computeIfAbsent(gameId, k -> new GameState());

        PlayerState player;
        if (trade.playerId == game.getCreator().getId()) {
            player = state.player1;
        } else if (game.getOpponent() != null &&
                   trade.playerId == game.getOpponent().getId()) {
            player = state.player2;
        } else {
            return;
        }

        double cost = trade.amount * trade.price;

        switch (trade.type.toUpperCase()) {
            case "BUY" -> {
                if (player.cash < cost) return;
                player.cash -= cost;
                player.longShares += trade.amount;
            }
            case "SELL" -> {
                if (player.longShares < trade.amount) return;
                player.cash += cost;
                player.longShares -= trade.amount;
            }
            case "SHORT" -> {
                double total =
                        (player.avgShortPrice * player.shortShares) + cost;
                player.shortShares += trade.amount;
                player.avgShortPrice = total / player.shortShares;
                player.cash += cost;
            }
            case "COVER" -> {
                if (player.shortShares < trade.amount) return;
                if (player.cash < cost) return;
                player.cash -= cost;
                player.shortShares -= trade.amount;
                if (player.shortShares == 0) {
                    player.avgShortPrice = 0.0;
                }
            }
            default -> {
                return;
            }
        }

        messagingTemplate.convertAndSend(
                "/topic/game/" + gameId + "/state",
                state
        );
    }

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String hello(String msg) {
        return "Hello from server";
    }
}