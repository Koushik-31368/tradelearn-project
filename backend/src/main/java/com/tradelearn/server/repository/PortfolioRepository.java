package com.tradelearn.server.repository;

import com.tradelearn.server.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    // A custom method to find a portfolio using a user's ID
    Optional<Portfolio> findByUserId(Long userId);
}