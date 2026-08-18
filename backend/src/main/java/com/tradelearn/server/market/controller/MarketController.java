package com.tradelearn.server.market.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.Candle;
import com.tradelearn.server.market.MarketDataException;
import com.tradelearn.server.market.service.MarketDataService;

import java.time.LocalDate;

/**
 * REST controller that exposes historical market data for the Simulator and
 * Practice Mode.
 *
 * <p>All endpoints are public (no JWT required). CORS is handled globally
 * by {@code SecurityConfig} — no {@code @CrossOrigin} needed here.</p>
 *
 * <p>Base path: {@code /api/market}</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/market/history}  — OHLCV candles for a date range</li>
 *   <li>{@code GET /api/market/price}    — last close price (point-in-time)</li>
 *   <li>{@code GET /api/market/latest}   — latest candle snapshot</li>
 * </ul>
 *
 * <h3>Error handling</h3>
 * {@link MarketDataException} (rate-limit / key error from Alpha Vantage) is
 * caught and returned as {@code 503 Service Unavailable} with a structured
 * JSON body so the frontend can show a user-friendly message rather than a
 * generic network error.
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private static final Logger log = LoggerFactory.getLogger(MarketController.class);

    private final MarketDataService marketDataService;

    public MarketController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    // ── Endpoints ──────────────────────────────────────────────────────────

    /**
     * GET /api/market/history?symbol=AAPL&start=2024-01-01&end=2024-03-31
     *
     * Returns OHLCV candles for the given symbol and date range.
     *
     * <p>Response candle shape:
     * <pre>{ "date": "2024-01-15", "open": 185.3, "high": 188.0,
     *       "low": 184.5, "close": 186.1, "volume": 60400000 }</pre>
     */
    @GetMapping("/history")
    public ResponseEntity<?> getMarketHistory(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "symbol parameter is required"));
        }

        String querySymbol = sanitise(symbol);
        if (querySymbol.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }

        try {
            List<Candle> candles = marketDataService.getHistoricalData(querySymbol, start, end);
            return ResponseEntity.ok(candles);
        } catch (MarketDataException e) {
            log.warn("[MarketController] Market data error for '{}': {}", querySymbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error",  e.getMessage(),
                            "source", e.getSource(),
                            "symbol", querySymbol
                    ));
        } catch (Exception e) {
            log.warn("[MarketController] History request failed for '{}': {}", querySymbol, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/market/price?symbol=AAPL
     *
     * Returns the last close price from the most recent available candle.
     *
     * <p>Response: {@code { "symbol": "AAPL", "price": 186.10 }}
     */
    @GetMapping("/price")
    public ResponseEntity<?> getCurrentPrice(@RequestParam String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "symbol parameter is required"));
        }

        String clean = sanitiseLoose(symbol);
        if (clean.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }

        try {
            LocalDate endDate   = LocalDate.now();
            LocalDate startDate = endDate.minusDays(10);
            List<Candle> candles = marketDataService.getHistoricalData(clean, startDate, endDate);
            if (candles.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No price data found for " + clean));
            }
            double price = candles.get(candles.size() - 1).getClose();
            return ResponseEntity.ok(Map.of("symbol", clean, "price", price));
        } catch (MarketDataException e) {
            log.warn("[MarketController] Market data error fetching price for '{}': {}", clean, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage(), "source", e.getSource()));
        } catch (RuntimeException e) {
            log.warn("[MarketController] Price request failed for '{}': {}", clean, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/market/latest?symbol=AAPL
     *
     * Returns the single most-recent candle for the given symbol.
     * Called by {@code fetchLatestCandle()} in {@code market.api.js}.
     *
     * <p>Response: {@code { "date": "2024-06-14", "open": ..., "high": ...,
     *              "low": ..., "close": ..., "volume": ... }}
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestCandle(@RequestParam String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "symbol parameter is required"));
        }

        String clean = sanitiseLoose(symbol);
        if (clean.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid symbol"));
        }

        try {
            LocalDate endDate   = LocalDate.now();
            LocalDate startDate = endDate.minusDays(10);
            List<Candle> candles = marketDataService.getHistoricalData(clean, startDate, endDate);
            if (candles.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No candle data found for " + clean));
            }
            Candle latest = candles.get(candles.size() - 1);
            return ResponseEntity.ok(latest);
        } catch (MarketDataException e) {
            log.warn("[MarketController] Market data error for latest candle '{}': {}", clean, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage(), "source", e.getSource()));
        } catch (Exception e) {
            log.warn("[MarketController] Latest candle request failed for '{}': {}", clean, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Strips anything that isn't an uppercase letter, digit, or dot.
     * Also up-cases and appends {@code .NS} for recognised Indian NSE tickers
     * that don't already have an exchange suffix, so they work with Yahoo Finance
     * (legacy fallback path).
     */
    private String sanitise(String raw) {
        String upper = raw.trim().toUpperCase().replaceAll("[^A-Z0-9.]", "");
        if (upper.isEmpty()) return upper;

        // Append .NS for known Indian tickers (Yahoo Finance legacy path)
        if (!upper.contains(".") && isIndianStock(upper)) {
            upper += ".NS";
        }
        return upper;
    }

    /** Looser sanitise used for price/latest where no .NS suffix is needed. */
    private String sanitiseLoose(String raw) {
        return raw.trim().toUpperCase().replaceAll("[^A-Z0-9._-]", "");
    }

    private boolean isIndianStock(String symbol) {
        return List.of(
                "TCS", "RELIANCE", "INFY", "HDFCBANK", "SBIN", "ITC",
                "WIPRO", "MARUTI", "KOTAKBANK", "LT", "AXISBANK",
                "HINDUNILVR", "TATASTEEL", "BHARTIARTL"
        ).contains(symbol);
    }
}
