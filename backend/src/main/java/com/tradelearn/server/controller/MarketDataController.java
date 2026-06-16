package com.tradelearn.server.controller;

import com.tradelearn.server.dto.Candle;
import com.tradelearn.server.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final MarketDataService marketDataService;

    @Autowired
    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistoricalData(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        
        try {
            // Append .NS if it's a common Indian stock without suffix for Yahoo Finance
            String querySymbol = symbol.toUpperCase();
            if (!querySymbol.contains(".") && isIndianStock(querySymbol)) {
                querySymbol += ".NS";
            }
            
            List<Candle> candles = marketDataService.getHistoricalData(querySymbol, start, end);
            return ResponseEntity.ok(candles);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private boolean isIndianStock(String symbol) {
        // Simple heuristic for Yahoo Finance
        return List.of("TCS", "RELIANCE", "INFY", "HDFCBANK", "SBIN", "ITC", "WIPRO", "MARUTI", "KOTAKBANK", "LT", "AXISBANK", "HINDUNILVR", "TATASTEEL", "BHARTIARTL").contains(symbol);
    }
}
