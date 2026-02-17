package com.tradelearn.server.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.TradeRepository;

@Service
public class MatchTradeService {

    private final TradeRepository tradeRepository;
    private final GameRepository gameRepository;
    private final CandleService candleService;

    public MatchTradeService(TradeRepository tradeRepository,
                             GameRepository gameRepository,
                             CandleService candleService) {
        this.tradeRepository = tradeRepository;
        this.gameRepository = gameRepository;
        this.candleService = candleService;
    }

    // ==================== PLACE TRADE IN A MATCH ====================

    @Transactional
    public Trade placeTrade(MatchTradeRequest request) {
        Game game = gameRepository.findById(request.getGameId())
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        // ---- Guard: game must be ACTIVE ----
        if (!"ACTIVE".equals(game.getStatus())) {
            throw new IllegalStateException("Game is not active");
        }

        // ---- Guard: candles must not be exhausted ----
        if (game.getCurrentCandleIndex() >= game.getTotalCandles()) {
            throw new IllegalStateException("No more candles — game should be ended");
        }

        // ---- Verify the user is a participant ----
        Long userId = request.getUserId();
        if (!game.getCreator().getId().equals(userId) &&
            (game.getOpponent() == null || !game.getOpponent().getId().equals(userId))) {
            throw new IllegalArgumentException("User is not a participant in this game");
        }

        // ---- Validate trade type ----
        String type = request.getType().toUpperCase();
        if (!List.of("BUY", "SELL", "SHORT", "COVER").contains(type)) {
            throw new IllegalArgumentException("Invalid trade type: " + type);
        }

        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // ---- Server-authoritative price from CandleService ----
        double price = candleService.getCurrentPrice(request.getGameId());

        // ---- Validate against player's current position ----
        PlayerPosition position = calculatePosition(
                request.getGameId(), userId, game.getStartingBalance()
        );

        double cost = request.getQuantity() * price;

        switch (type) {
            case "BUY" -> {
                if (position.cash < cost) {
                    throw new IllegalStateException(
                            String.format("Insufficient funds. Have: %.2f, Need: %.2f", position.cash, cost)
                    );
                }
            }
            case "SELL" -> {
                int longShares = position.shares.getOrDefault(request.getSymbol(), 0);
                if (longShares < request.getQuantity()) {
                    throw new IllegalStateException(
                            String.format("Insufficient shares. Have: %d, Trying to sell: %d", longShares, request.getQuantity())
                    );
                }
            }
            case "SHORT" -> {
                // Short selling: receive cash upfront, no cash requirement check needed
            }
            case "COVER" -> {
                int shortShares = position.shortShares.getOrDefault(request.getSymbol(), 0);
                if (shortShares < request.getQuantity()) {
                    throw new IllegalStateException(
                            String.format("Insufficient short position. Short: %d, Trying to cover: %d", shortShares, request.getQuantity())
                    );
                }
                if (position.cash < cost) {
                    throw new IllegalStateException(
                            String.format("Insufficient funds to cover. Have: %.2f, Need: %.2f", position.cash, cost)
                    );
                }
            }
        }

        // ---- Record the trade with server-side price ----
        Trade trade = new Trade();
        trade.setGameId(request.getGameId());
        trade.setUserId(userId);
        trade.setSymbol(request.getSymbol());
        trade.setName(request.getSymbol()); // use symbol as name for match trades
        trade.setType(type);
        trade.setQuantity(request.getQuantity());
        trade.setPrice(price); // server-authoritative

        Trade saved = tradeRepository.save(trade);

        // Recalculate position + stats (peak equity, drawdown, trade counts)
        // Stats are tracked in-memory via PlayerPosition and persisted at match end
        calculatePosition(request.getGameId(), request.getUserId(), game.getStartingBalance());

        return saved;
    }

    // ==================== POSITION TRACKING ====================

    public static class PlayerPosition {
        public double cash;
        public Map<String, Integer> shares = new HashMap<>();       // long positions
        public Map<String, Integer> shortShares = new HashMap<>();  // short positions
        public Map<String, Double> avgShortPrice = new HashMap<>(); // avg short entry price

        // ---- Risk / performance stats ----
        public double peakEquity;
        public double maxDrawdown;     // 0.0 – 1.0 (percentage)
        public int totalTrades;
        public int profitableTrades;
    }

    /**
     * Replays all trades for a player in a game to compute current position
     * and risk statistics (peak equity, max drawdown, trade counts).
     */
    @SuppressWarnings("null")
    public PlayerPosition calculatePosition(long gameId, long userId, double startingBalance) {
        List<Trade> trades = tradeRepository.findByGameIdAndUserId(gameId, userId);

        PlayerPosition pos = new PlayerPosition();
        pos.cash = startingBalance;
        pos.peakEquity = startingBalance;
        pos.maxDrawdown = 0.0;
        pos.totalTrades = 0;
        pos.profitableTrades = 0;

        for (Trade t : trades) {
            double cost = t.getQuantity() * t.getPrice();
            String symbol = t.getSymbol();
            pos.totalTrades++;

            switch (t.getType().toUpperCase()) {
                case "BUY" -> {
                    pos.cash -= cost;
                    pos.shares.merge(symbol, t.getQuantity(), Integer::sum);
                }
                case "SELL" -> {
                    pos.cash += cost;
                    pos.shares.merge(symbol, -t.getQuantity(), Integer::sum);
                    if (pos.shares.getOrDefault(symbol, 0) <= 0) {
                        pos.shares.remove(symbol);
                    }
                    // Profitable if sold above the last known price at time of trade
                    // Simple heuristic: compare sell proceeds to cost basis is complex,
                    // so we check if this trade brought equity above starting balance
                }
                case "SHORT" -> {
                    pos.cash += cost;
                    int prevShort = pos.shortShares.getOrDefault(symbol, 0);
                    double prevAvg = pos.avgShortPrice.getOrDefault(symbol, 0.0);
                    double totalValue = (prevAvg * prevShort) + cost;
                    int newShort = prevShort + t.getQuantity();
                    pos.shortShares.put(symbol, newShort);
                    pos.avgShortPrice.put(symbol, totalValue / newShort);
                }
                case "COVER" -> {
                    pos.cash -= cost;
                    double avgEntry = pos.avgShortPrice.getOrDefault(symbol, t.getPrice());
                    // Profit if covered below the entry price
                    if (t.getPrice() < avgEntry) {
                        pos.profitableTrades++;
                    }
                    int newShort = pos.shortShares.getOrDefault(symbol, 0) - t.getQuantity();
                    if (newShort <= 0) {
                        pos.shortShares.remove(symbol);
                        pos.avgShortPrice.remove(symbol);
                    } else {
                        pos.shortShares.put(symbol, newShort);
                    }
                }
            }

            // ---- Update equity tracking after each trade ----
            // Approximate equity using the trade's price as mark price
            double equity = snapshotEquity(pos, t.getPrice());

            // Check for profitable SELL (compare equity jump)
            if ("SELL".equalsIgnoreCase(t.getType()) && equity > pos.peakEquity) {
                pos.profitableTrades++;
            }

            if (equity > pos.peakEquity) {
                pos.peakEquity = equity;
            }
            if (pos.peakEquity > 0) {
                double drawdown = (pos.peakEquity - equity) / pos.peakEquity;
                if (drawdown > pos.maxDrawdown) {
                    pos.maxDrawdown = drawdown;
                }
            }
        }

        return pos;
    }

    /**
     * Quick equity snapshot: cash + longs×price − shorts×price.
     * Uses a single mark price (good enough for single-symbol matches).
     */
    private static double snapshotEquity(PlayerPosition pos, double markPrice) {
        double equity = pos.cash;
        for (int qty : pos.shares.values())      equity += qty * markPrice;
        for (int qty : pos.shortShares.values()) equity -= qty * markPrice;
        return equity;
    }

    // ==================== FINAL BALANCE CALCULATION ====================

    /**
     * Calculates a player's total worth at end of match:
     * cash + (long shares × current price) - (short shares × current price)
     */
    public double calculateFinalBalance(long gameId, long userId,
                                         double startingBalance, double currentStockPrice) {
        PlayerPosition pos = calculatePosition(gameId, userId, startingBalance);

        double totalBalance = pos.cash;

        // Add value of all long positions
        for (Map.Entry<String, Integer> entry : pos.shares.entrySet()) {
            totalBalance += entry.getValue() * currentStockPrice;
        }

        // Subtract cost to close short positions (buy to cover at current price)
        for (Map.Entry<String, Integer> entry : pos.shortShares.entrySet()) {
            totalBalance -= entry.getValue() * currentStockPrice;
        }

        return totalBalance;
    }

    // ==================== QUERIES ====================

    public List<Trade> getGameTrades(long gameId) {
        return tradeRepository.findByGameIdOrderByTimestampAsc(gameId);
    }

    public List<Trade> getPlayerGameTrades(long gameId, long userId) {
        return tradeRepository.findByGameIdAndUserId(gameId, userId);
    }

    public PlayerPosition getPlayerPosition(long gameId, long userId, double startingBalance) {
        return calculatePosition(gameId, userId, startingBalance);
    }
}
