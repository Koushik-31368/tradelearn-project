package com.tradelearn.server.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.service.MarketDataService;
import java.time.LocalDate;

/**
 * REST controller that exposes historical market data for Practice Mode.
 *
 * <p>All endpoints are public (no JWT required). CORS is handled globally
 * by {@code SecurityConfig} — no {@code @CrossOrigin} needed here.</p>
 *
 * <p>All candle data is served from local classpath JSON files — no external
 * API dependencies.</p>
 *
 * <p>Base paths: {@code /api/market} (legacy) and {@code /api/candles}</p>
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private static final Logger log = LoggerFactory.getLogger(MarketController.class);

    private final MarketDataService marketDataService;

    public MarketController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * GET /api/market/history?symbol=INFY
     *
     * Returns all OHLCV candles from the local dataset for the given NSE symbol.
     * The {@code start} and {@code end} parameters are accepted for backward
     * compatibility but ignored — the full local dataset is always returned.
     *
     * <p>Response candle shape:</p>
     * <pre>{ "time": "2020-03-01", "open": 1452.3, "high": 1460.0,
     *        "low": 1448.5, "close": 1455.1, "volume": 120400 }</pre>
     */
    @GetMapping("/history")
    public ResponseEntity<?> getMarketHistory(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbol parameter is required"));
        }

        String querySymbol = symbol.trim().toUpperCase().replaceAll("[^A-Z0-9.]", "");
        if (querySymbol.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }

        try {
            // Append .NS if it's a common Indian stock without suffix for Yahoo Finance
            if (!querySymbol.contains(".") && isIndianStock(querySymbol)) {
                querySymbol += ".NS";
            }
            
            List<com.tradelearn.server.dto.Candle> candles = marketDataService.getHistoricalData(querySymbol, start, end);
            return ResponseEntity.ok(candles);
        } catch (Exception e) {
            log.warn("[MarketController] History request failed for '{}': {}", querySymbol, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private boolean isIndianStock(String symbol) {
        return List.of("TCS", "RELIANCE", "INFY", "HDFCBANK", "SBIN", "ITC", "WIPRO", "MARUTI", "KOTAKBANK", "LT", "AXISBANK", "HINDUNILVR", "TATASTEEL", "BHARTIARTL").contains(symbol);
    }

    /**
     * GET /api/market/price?symbol=RELIANCE
     *
     * Returns the last close price from the local dataset for the given symbol.
     *
     * <p>Response: {@code { "symbol": "RELIANCE", "price": 2456.75 }}</p>
     */
    @GetMapping("/price")
    public ResponseEntity<?> getCurrentPrice(@RequestParam String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbol parameter is required"));
        }

        String clean = symbol.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }

        try {
            java.time.LocalDate endDate = java.time.LocalDate.now();
            java.time.LocalDate startDate = endDate.minusDays(5);
            List<com.tradelearn.server.dto.Candle> candles = marketDataService.getHistoricalData(clean, startDate, endDate);
            if (candles.isEmpty()) {
                 return ResponseEntity.badRequest().body(Map.of("error", "No price data found"));
            }
            double price = candles.get(candles.size() - 1).getClose();
            return ResponseEntity.ok(Map.of("symbol", clean, "price", price));
        } catch (RuntimeException e) {
            log.warn("[MarketController] Price request failed for '{}': {}", clean, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
