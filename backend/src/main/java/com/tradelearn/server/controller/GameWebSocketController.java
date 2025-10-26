package com.tradelearn.server.controller;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.SendTo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// --- DTOs (Data Transfer Objects) for WebSocket Messages ---

class PlayerState {
    public double cash = 1000000;
    public int longShares = 0; // Renamed from 'shares' for clarity

    // --- NEW: Fields for Short Selling ---
    public int shortShares = 0; // How many shares are currently shorted
    public double avgShortPrice = 0.0; // Average price at which shares were shorted

    // Getters
    public double getCash() { return cash; }
    public int getLongShares() { return longShares; }
    public int getShortShares() { return shortShares; }
    public double getAvgShortPrice() { return avgShortPrice; }
}

class GameState {
    public PlayerState player1 = new PlayerState();
    public PlayerState player2 = new PlayerState();
    public PlayerState getPlayer1() { return player1; }
    public PlayerState getPlayer2() { return player2; }
}

class TradeAction {
    public String type; // "BUY", "SELL", "SHORT", "COVER"
    public int amount;
    public double price;
    public Long playerId;

    // Getters & Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
}
// --- End DTOs ---

class GameReadiness {
    private final AtomicInteger readyCount = new AtomicInteger(0);
    public int incrementAndGet() { return readyCount.incrementAndGet(); }
    public void reset() { readyCount.set(0); }
}

@Controller
public class GameWebSocketController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private GameRepository gameRepository;

    private final Map<Long, GameReadiness> gameReadinessMap = new ConcurrentHashMap<>();
    private final Map<Long, GameState> gameStateMap = new ConcurrentHashMap<>();

    @MessageMapping("/game/{gameId}/ready")
    public void playerReady(@DestinationVariable Long gameId) {
        // ... (this method remains the same) ...
        GameReadiness readiness = gameReadinessMap.computeIfAbsent(gameId, k -> new GameReadiness());
        int currentReadyCount = readiness.incrementAndGet();
        if (currentReadyCount >= 2) {
            readiness.reset();
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/nextRound", "NEXT_ROUND");
        }
    }

    @MessageMapping("/game/{gameId}/trade")
    public void handleTrade(@DestinationVariable Long gameId, @Payload TradeAction trade) {
        Optional<Game> gameOptional = gameRepository.findById(gameId);
        if (gameOptional.isEmpty()) { return; }
        Game game = gameOptional.get();
        GameState currentState = gameStateMap.computeIfAbsent(gameId, k -> new GameState());

        PlayerState actingPlayer;
        if (trade.getPlayerId().equals(game.getCreator().getId())) {
            actingPlayer = currentState.player1;
        } else if (trade.getPlayerId().equals(game.getOpponent().getId())) {
            actingPlayer = currentState.player2;
        } else {
            return; // Unknown player
        }

        double cost = trade.getAmount() * trade.getPrice();

        // --- UPDATED TRADE LOGIC ---
        switch (trade.getType().toUpperCase()) {
            case "BUY":
                if (actingPlayer.cash >= cost) {
                    actingPlayer.cash -= cost;
                    actingPlayer.longShares += trade.getAmount();
                } else { return; /* Insufficient funds */ }
                break;
            case "SELL":
                if (actingPlayer.longShares >= trade.getAmount()) {
                    actingPlayer.cash += cost;
                    actingPlayer.longShares -= trade.getAmount();
                } else { return; /* Insufficient shares */ }
                break;
            case "SHORT":
                // Logic to open a new short position
                // For simplicity, we assume 100% margin (player gets cash for sale)
                // We also need to update the average short price
                double newTotalShortCost = (actingPlayer.avgShortPrice * actingPlayer.shortShares) + cost;
                actingPlayer.shortShares += trade.getAmount();
                actingPlayer.avgShortPrice = newTotalShortCost / actingPlayer.shortShares;
                actingPlayer.cash += cost;
                break;
            case "COVER":
                // Logic to close an existing short position (buy back shares)
                if (actingPlayer.shortShares >= trade.getAmount()) {
                    // Check if player has enough cash to buy back
                    if (actingPlayer.cash >= cost) {
                        actingPlayer.cash -= cost;
                        actingPlayer.shortShares -= trade.getAmount();
                        // If all shorts are closed, reset avg price
                        if (actingPlayer.shortShares == 0) {
                            actingPlayer.avgShortPrice = 0.0;
                        }
                    } else { return; /* Insufficient cash to cover */ }
                } else { return; /* Not enough shorted shares to cover */ }
                break;
            default:
                return; // Unknown trade type
        }
        // --- END UPDATED LOGIC ---

        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", currentState);
    }

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String greeting(String message) {
        System.out.println("Received hello message: " + message);
        return "Hello back from server!";
    }
}