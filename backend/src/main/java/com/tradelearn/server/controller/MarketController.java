package com.tradelearn.server.controller;

import com.tradelearn.server.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes historical market data for Practice Mode.
 *
 * <p>All endpoints are public (no JWT required). CORS is handled globally
 * by {@code SecurityConfig} — no {@code @CrossOrigin} needed here.</p>
 *
 * <p>Base path: {@code /api/market}</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketDataService marketDataService;

    /**
     * GET /api/market/history?symbol=INFY
     *
     * Returns a list of 5-minute OHLCV candles for the given NSE symbol.
     * Up to ~375 candles (5 trading days × 75 candles/day).
     *
     * <p>Response candle shape:</p>
     * <pre>{ "time": 1709550600000, "open": 1452.3, "high": 1460.0,
     *        "low": 1448.5, "close": 1455.1, "volume": 120400 }</pre>
     *
     * @param symbol NSE symbol without ".NS" suffix (e.g. {@code INFY}, {@code RELIANCE})
     */
    @GetMapping("/history")
    public ResponseEntity<?> getMarketHistory(@RequestParam String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbol parameter is required"));
        }

        // Sanitise: uppercase, strip whitespace
        String clean = symbol.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }

        try {
            List<Map<String, Object>> candles = marketDataService.getHistoricalData(clean);
            return ResponseEntity.ok(candles);
        } catch (RuntimeException e) {
            log.warn("[MarketController] History request failed for '{}': {}", clean, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
