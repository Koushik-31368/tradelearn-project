package com.tradelearn.server.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.service.MarketDataService;

/**
 * REST controller serving local OHLCV candle datasets.
 *
 * <p>Base path: {@code /api/candles}</p>
 */
@RestController
@RequestMapping("/api/candles")
public class CandleController {

    private static final Logger log = LoggerFactory.getLogger(CandleController.class);

    private final MarketDataService marketDataService;

    public CandleController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * GET /api/candles/{symbol}
     *
     * Returns all OHLCV candles from the local dataset for the given NSE symbol.
     *
     * <p>Response candle shape:</p>
     * <pre>{ "time": "2020-03-01", "open": 1452.3, "high": 1460.0,
     *        "low": 1448.5, "close": 1455.1, "volume": 120400 }</pre>
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<?> getCandles(@PathVariable String symbol) {
        String clean = symbol.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }
        try {
            List<Map<String, Object>> candles = marketDataService.getHistoricalData(clean);
            return ResponseEntity.ok(candles);
        } catch (RuntimeException e) {
            log.warn("[CandleController] Request failed for '{}': {}", clean, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
