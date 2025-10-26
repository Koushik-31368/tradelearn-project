package com.tradelearn.server.controller;

import com.tradelearn.server.model.Trade;
import com.tradelearn.server.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/trades")
@CrossOrigin(origins = "*") // Allow all origins for development
public class TradeController {

    @Autowired
    private TradeService tradeService;

    // Create new trade
    @PostMapping
    public ResponseEntity<Trade> createTrade(@RequestBody Trade trade) {
        try {
            Trade createdTrade = tradeService.createTrade(trade);
            return new ResponseEntity<>(createdTrade, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get all trades for a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Trade>> getUserTrades(@PathVariable Long userId) {
        try {
            List<Trade> trades = tradeService.getUserTrades(userId);
            if (trades.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(trades, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get single trade by ID
    @GetMapping("/{id}")
    public ResponseEntity<Trade> getTradeById(@PathVariable Long id) {
        Optional<Trade> trade = tradeService.getTradeById(id);
        return trade.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // Get trades by symbol for a user
    @GetMapping("/user/{userId}/symbol/{symbol}")
    public ResponseEntity<List<Trade>> getUserTradesBySymbol(
            @PathVariable Long userId,
            @PathVariable String symbol) {
        try {
            List<Trade> trades = tradeService.getUserTradesBySymbol(userId, symbol);
            return new ResponseEntity<>(trades, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Delete trade
    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteTrade(@PathVariable Long id) {
        try {
            tradeService.deleteTrade(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get trade count for user
    @GetMapping("/user/{userId}/count")
    public ResponseEntity<Long> getUserTradeCount(@PathVariable Long userId) {
        try {
            Long count = tradeService.getUserTradeCount(userId);
            return new ResponseEntity<>(count, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Get all trades (admin only)
    @GetMapping
    public ResponseEntity<List<Trade>> getAllTrades() {
        try {
            List<Trade> trades = tradeService.getAllTrades();
            if (trades.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(trades, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
