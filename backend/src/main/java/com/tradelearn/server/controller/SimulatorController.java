package com.tradelearn.server.controller;

import com.tradelearn.server.dto.TradeRequest;
import com.tradelearn.server.model.Holding;
import com.tradelearn.server.model.Portfolio;
import com.tradelearn.server.repository.HoldingRepository;
import com.tradelearn.server.repository.PortfolioRepository; // Import PortfolioRepository
import com.tradelearn.server.service.SimulatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional; // Import Optional

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    @Autowired
    private SimulatorService simulatorService;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private PortfolioRepository portfolioRepository; // Add PortfolioRepository

    @PostMapping("/trade")
    public ResponseEntity<?> executeTrade(@RequestBody TradeRequest tradeRequest) {
        try {
            Portfolio updatedPortfolio = simulatorService.executeTrade(tradeRequest);
            return ResponseEntity.ok(updatedPortfolio);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // --- ADD THIS NEW METHOD ---
    @GetMapping("/holdings")
    public ResponseEntity<?> getHoldings(@RequestParam Long userId) {
        // 1. Find the user's portfolio
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserId(userId);
        if (portfolioOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Portfolio not found"));
        }

        // 2. Get the portfolio ID
        Long portfolioId = portfolioOpt.get().getId();

        // 3. Find all holdings for that portfolio
        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);

        return ResponseEntity.ok(holdings);
    }
}