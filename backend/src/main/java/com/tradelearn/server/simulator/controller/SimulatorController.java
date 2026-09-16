package com.tradelearn.server.simulator.controller;

import com.tradelearn.server.dto.TradeRequest;
import com.tradelearn.server.simulator.model.Portfolio;
import com.tradelearn.server.simulator.repository.PortfolioRepository;
import com.tradelearn.server.simulator.service.SimulatorService;
import com.tradelearn.server.user.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<?> executeTrade(
            @RequestBody TradeRequest tradeRequest,
            @AuthenticationPrincipal User principal) {
        try {
            // Override userId with the authenticated principal — prevents spoofing
            // another user's portfolio by sending a different userId in the body.
            tradeRequest.setUserId(principal.getId());
            Portfolio updatedPortfolio = simulatorService.executeTrade(tradeRequest);
            return ResponseEntity.ok(updatedPortfolio);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolio(@AuthenticationPrincipal User principal) {
        // userId comes from the JWT, not from a query param — prevents peeking at
        // other users' portfolios.
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUser_Id(principal.getId());
        if (portfolioOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Portfolio not found"));
        }
        return ResponseEntity.ok(portfolioOpt.get());
    }
}