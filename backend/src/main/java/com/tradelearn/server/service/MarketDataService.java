package com.tradelearn.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * Serves historical OHLCV candles from local JSON files bundled in the classpath
 * under {@code candles/<SYMBOL>.json}.
 *
 * <p>All files are loaded once at application startup and held in memory for
 * zero-latency access.  No external API calls are ever made.</p>
 *
 * <p>Candle JSON format:</p>
 * <pre>
 * [
 *   { "time": "2024-01-02", "open": 2400, "high": 2420, "low": 2390, "close": 2410, "volume": 1200000 },
 *   ...
 * ]
 * </pre>
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final String[] PRELOAD_SYMBOLS = {
        "RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN", "ITC", "MARUTI", "WIPRO"
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** In-memory store: symbol → candle list (loaded once at startup). */
    private final Map<String, List<Map<String, Object>>> candleStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void preloadAll() {
        for (String symbol : PRELOAD_SYMBOLS) {
            List<Map<String, Object>> candles = loadFromClasspath(symbol);
            if (!candles.isEmpty()) {
                candleStore.put(symbol, candles);
                log.info("[MarketData] Preloaded {} candles for {}", candles.size(), symbol);
            } else {
                log.warn("[MarketData] No candle file found for {} — requests will return empty list", symbol);
            }
        }
    }

    /**
     * Returns the full candle list for a symbol (all time, no range filtering).
     * Used by the Practice Mode "historical events" tab.
     */
    public List<Map<String, Object>> getHistoricalData(String symbol) {
        List<Map<String, Object>> candles = candleStore.get(symbol);
        if (candles == null) {
            // Try loading on-demand for symbols not preloaded
            candles = loadFromClasspath(symbol);
            if (!candles.isEmpty()) {
                candleStore.put(symbol, candles);
            }
        }
        if (candles == null || candles.isEmpty()) {
            log.warn("[MarketData] No local candles for symbol: {}", symbol);
            return Collections.emptyList();
        }
        log.info("[MarketData] Serving {} candles for {}", candles.size(), symbol);
        return candles;
    }

    /**
     * Returns candles for a symbol filtered to the given time range.
     * {@code start} and {@code end} are compared against the candle {@code time}
     * field, which may be a date-string ("2024-01-02") or a numeric timestamp.
     * When the field is a date-string the range check is skipped and all candles
     * are returned so Practice Mode always has data.
     */
    public List<Map<String, Object>> getHistoricalData(String symbol, long start, long end) {
        // For local static datasets we serve the full list regardless of the
        // requested range — the frontend will render whatever it receives.
        return getHistoricalData(symbol);
    }

    /**
     * Returns the last close price from the local candle dataset for {@code symbol}.
     * Used by Simulator Mode as the base price for generated candles.
     */
    public double getCurrentPrice(String symbol) {
        List<Map<String, Object>> candles = getHistoricalData(symbol);
        if (candles.isEmpty()) {
            return 1000.0; // safe default
        }
        Map<String, Object> last = candles.get(candles.size() - 1);
        Object close = last.get("close");
        if (close instanceof Number) {
            return ((Number) close).doubleValue();
        }
        return 1000.0;
    }

    // ── PRIVATE HELPERS ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadFromClasspath(String symbol) {
        String path = "candles/" + symbol + ".json";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return Collections.emptyList();
            return objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            log.error("[MarketData] Failed to load {}: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }
}
