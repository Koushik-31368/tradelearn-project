package com.tradelearn.server.service;

import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;

    // Create new trade with basic validation and error handling
    @Transactional
    public Trade createTrade(Trade trade) throws IllegalArgumentException {
        // Basic checks for minimal required fields
        if (trade.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (trade.getSymbol() == null || trade.getSymbol().isEmpty()) {
            throw new IllegalArgumentException("Stock symbol is required");
        }
        if (trade.getQuantity() == null || trade.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        // Add more business logic checks here if needed (for 'type', 'price', etc.)

        // Save to DB
        return tradeRepository.save(trade);
    }

    // Get all trades for a user
    public List<Trade> getUserTrades(Long userId) {
        return tradeRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    // Get single trade by ID
    public Optional<Trade> getTradeById(Long id) {
        return tradeRepository.findById(id);
    }

    // Get trades by symbol
    public List<Trade> getUserTradesBySymbol(Long userId, String symbol) {
        return tradeRepository.findByUserIdAndSymbol(userId, symbol);
    }

    // Delete trade
    @Transactional
    public void deleteTrade(Long id) {
        tradeRepository.deleteById(id);
    }

    // Get total trade count for user
    public Long getUserTradeCount(Long userId) {
        return tradeRepository.countByUserId(userId);
    }

    // Get all trades (admin only)
    public List<Trade> getAllTrades() {
        return tradeRepository.findAll();
    }
}
