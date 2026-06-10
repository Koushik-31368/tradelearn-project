package com.tradelearn.server.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tradelearn.server.model.Trade;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    // Find all trades for a specific user
    List<Trade> findByUserIdOrderByTimestampDesc(Long userId);

    // Find trades by user and symbol
    List<Trade> findByUserIdAndSymbol(Long userId, String symbol);

    // Find trades by type (BUY or SELL)
    List<Trade> findByUserIdAndType(Long userId, String type);

    // Count total trades for a user
    Long countByUserId(Long userId);

    // Find all trades for a specific game
    List<Trade> findByGameIdOrderByTimestampAsc(Long gameId);

    // Find trades for a specific user in a specific game
    List<Trade> findByGameIdAndUserId(Long gameId, Long userId);

    // Count trades in a game
    Long countByGameId(Long gameId);
}

