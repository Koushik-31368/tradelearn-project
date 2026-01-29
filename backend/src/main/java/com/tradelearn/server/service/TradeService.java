package com.tradelearn.server.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.TradeRepository;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;

    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    // Create new trade with validation
    @Transactional
    public Trade createTrade(Trade trade) {

        if (trade.getUserId() <= 0) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (trade.getSymbol() == null || trade.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Stock symbol is required");
        }
        if (trade.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        return tradeRepository.save(trade);
    }

    // Get all trades for a user
    public List<Trade> getUserTrades(long userId) {
        return tradeRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    // Get single trade by ID
    public Optional<Trade> getTradeById(long tradeId) {
        return tradeRepository.findById(tradeId);
    }

    // Get trades by symbol
    public List<Trade> getUserTradesBySymbol(long userId, String symbol) {
        return tradeRepository.findByUserIdAndSymbol(userId, symbol);
    }

    // Delete trade
    @Transactional
    public void deleteTrade(long tradeId) {
        tradeRepository.deleteById(tradeId);
    }

    // Get total trade count for user
    public long getUserTradeCount(long userId) {
        return tradeRepository.countByUserId(userId);
    }

    // Admin: get all trades
    public List<Trade> getAllTrades() {
        return tradeRepository.findAll();
    }
}