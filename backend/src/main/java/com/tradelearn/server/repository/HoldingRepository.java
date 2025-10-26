package com.tradelearn.server.repository;

import com.tradelearn.server.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    // A custom method to find all holdings for a specific portfolio
    List<Holding> findByPortfolioId(Long portfolioId);

    // A custom method to find a specific holding (e.g., "IBM") in a specific portfolio
    Optional<Holding> findByPortfolioIdAndStockSymbol(Long portfolioId, String stockSymbol);
}
