package com.tradelearn.server.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.service.MarketDataService;

/**
 * REST controller that exposes historical market data for Practice Mode.
 *
 * <p>All endpoints are public (no JWT required). CORS is handled globally
 * by {@code SecurityConfig} — no {@code @CrossOrigin} needed here.</p>
 *
 * <p>Base path: {@code /api/market}</p>
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
     * GET /api/market/history?symbol=RELIANCE&start=1580515200&end=1585699200
     *
     * Returns OHLCV candles for the given NSE symbol.
     *
     * <ul>
     *   <li>Without {@code start}/{@code end} — returns the last 5 trading days
     *       at 5-minute resolution (Live Data tab).</li>
     *   <li>With {@code start}/{@code end} (Unix epoch seconds) — returns the
     *       requested historical range; the backend auto-picks the best interval
     *       ({@code 5m} / {@code 1h} / {@code 1d}) based on the range age and span
     *       (Historical Events tab).</li>
     * </ul>
     *
     * <p>Response candle shape:</p>
     * <pre>{ "time": 1709550600000, "open": 1452.3, "high": 1460.0,
     *        "low": 1448.5, "close": 1455.1, "volume": 120400 }</pre>
     */
    @GetMapping("/history")
    public ResponseEntity<?> getMarketHistory(
            @RequestParam String symbol,
            @RequestParam(required = false) Long start,
            @RequestParam(required = false) Long end) {

        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbol parameter is required"));
        }

        String clean = symbol.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }

        // Validate timestamps when provided
        if ((start == null) != (end == null)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Both start and end must be provided together"));
        }
        if (start != null && end != null && end <= start) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "end must be greater than start"));
        }

        try {
            List<Map<String, Object>> candles = (start != null && end != null)
                    ? marketDataService.getHistoricalData(clean, start, end)
                    : marketDataService.getHistoricalData(clean);
            return ResponseEntity.ok(candles);
        } catch (RuntimeException e) {
            log.warn("[MarketController] History request failed for '{}': {}", clean, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
