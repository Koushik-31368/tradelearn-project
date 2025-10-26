package com.tradelearn.server.controller;

import com.tradelearn.server.dto.TradeRequest;
import com.tradelearn.server.model.Portfolio;
import com.tradelearn.server.repository.PortfolioRepository;
import com.tradelearn.server.service.SimulatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    @Autowired
    private SimulatorService simulatorService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @PostMapping("/trade")
    public ResponseEntity<?> executeTrade(@RequestBody TradeRequest tradeRequest) {
        try {
            Portfolio updatedPortfolio = simulatorService.executeTrade(tradeRequest);
            return ResponseEntity.ok(updatedPortfolio);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolio(@RequestParam Long userId) {
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUser_Id(userId);
        if (portfolioOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Portfolio not found"));
        }
        return ResponseEntity.ok(portfolioOpt.get());
    }
}