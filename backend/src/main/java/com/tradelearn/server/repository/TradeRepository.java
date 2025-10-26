package com.tradelearn.server.repository;

import com.tradelearn.server.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    // Find all trades for a specific user
    List<Trade> findByUserIdOrderByTimestampDesc(Long userId);

    // Find trades by user and symbol
    List<Trade> findByUserIdAndSymbol(Long userId, String symbol);

    // Find trades by type (BUY or SELL)
    List<Trade> findByUserIdAndType(Long userId, String type);  // ✅ FIXED - removed space

    // Count total trades for a user
    Long countByUserId(Long userId);
}

