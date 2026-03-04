package com.tradelearn.server.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Fetches historical OHLCV candles from Yahoo Finance for a given NSE symbol.
 *
 * <p>Two modes are supported:</p>
 * <ul>
 *   <li><b>Recent (live)</b> — last 5 trading days at 5-minute resolution
 *       via the {@code range=5d&interval=5m} query.</li>
 *   <li><b>Date-range (historical events)</b> — arbitrary {@code period1}/
 *       {@code period2} range; the interval is auto-selected based on the
 *       span so Yahoo Finance always returns data:
 *       <ul>
 *         <li>&lt; 60 days old → {@code 5m}</li>
 *         <li>&lt; 730 days old → {@code 1h}</li>
 *         <li>older → {@code 1d}</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Yahoo Finance sometimes embeds {@code null} at closed-market timestamps.
 * Any candle whose OHLCV contains a null value is silently skipped.</p>
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final String YAHOO_BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";
    /** Recent-data URL — last 5 trading days at 5-minute resolution. */
    private static final String YAHOO_RECENT_URL =
            YAHOO_BASE + "%s.NS?interval=5m&range=5d";
    /** Date-range URL — period1/period2 are Unix epoch seconds; interval chosen dynamically. */
    private static final String YAHOO_RANGE_URL =
            YAHOO_BASE + "%s.NS?period1=%d&period2=%d&interval=%s";

    /** Epoch seconds threshold below which Yahoo's 5-minute data starts (roughly 60 days). */
    private static final long SECONDS_60D  = 60L  * 24 * 3600;
    /** Epoch seconds threshold below which Yahoo's 1-hour data starts (roughly 730 days). */
    private static final long SECONDS_730D = 730L * 24 * 3600;
    /** TTL in seconds for the "recent" (live-mode) cache entries (~10 minutes). */
    private static final long RECENT_CACHE_TTL_SEC = 600L;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── In-memory caches ────────────────────────────────────────────────────
    /** Cache for historical range requests — keyed by "symbol*start*end". */
    private final Map<String, List<Map<String, Object>>> historyCache =
            new ConcurrentHashMap<>();
    /** Cache for recent (live 5-day) requests — keyed by symbol. */
    private final Map<String, List<Map<String, Object>>> recentCache =
            new ConcurrentHashMap<>();
    /** Insertion timestamps (epoch seconds) for recent cache entries. */
    private final Map<String, Long> recentCacheTimestamps = new ConcurrentHashMap<>();
    /** Cache for current-price requests — keyed by symbol; TTL = RECENT_CACHE_TTL_SEC. */
    private final Map<String, Double>  priceCache           = new ConcurrentHashMap<>();
    private final Map<String, Long>    priceCacheTimestamps = new ConcurrentHashMap<>();

    // ── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Returns up to ~375 five-minute candles for the given NSE symbol
     * (last 5 trading days).  Results are cached for {@code RECENT_CACHE_TTL_SEC} seconds.
     */
    public List<Map<String, Object>> getHistoricalData(String symbol) {
        long now = Instant.now().getEpochSecond();
        Long ts  = recentCacheTimestamps.get(symbol);
        if (ts != null && (now - ts) < RECENT_CACHE_TTL_SEC && recentCache.containsKey(symbol)) {
            log.info("[MarketData] Cache HIT (recent) for {}", symbol);
            return recentCache.get(symbol);
        }
        String url = String.format(YAHOO_RECENT_URL, symbol);
        log.info("[MarketData] Fetching recent candles for {} — {}", symbol, url);
        List<Map<String, Object>> data = fetchAndParse(symbol, url);
        recentCache.put(symbol, data);
        recentCacheTimestamps.put(symbol, now);
        return data;
    }

    /**
     * Returns OHLCV candles for {@code symbol} between {@code start} and {@code end}
     * (Unix epoch <b>seconds</b>).  Used by the <em>Historical Events</em> tab.
     *
     * <p>The interval is chosen automatically:</p>
     * <ul>
     *   <li>End is within the last 60 days → {@code 5m}</li>
     *   <li>End is within the last 730 days → {@code 1h}</li>
     *   <li>Older → {@code 1d} (fully supported for any historical range)</li>
     * </ul>
     *
     * @param symbol NSE symbol without {@code .NS}, e.g. {@code RELIANCE}
     * @param start  range start in Unix epoch seconds
     * @param end    range end   in Unix epoch seconds
     */
    public List<Map<String, Object>> getHistoricalData(String symbol, long start, long end) {
        String key = symbol + "*" + start + "*" + end;
        if (historyCache.containsKey(key)) {
            log.info("[MarketData] Cache HIT (history) for key={}", key);
            return historyCache.get(key);
        }

        long nowSec     = System.currentTimeMillis() / 1000L;
        long ageOfEnd   = nowSec - end;
        long spanSec    = end - start;

        String interval;
        if (ageOfEnd < SECONDS_60D && spanSec <= SECONDS_60D) {
            interval = "5m";
        } else if (ageOfEnd < SECONDS_730D) {
            interval = "1h";
        } else {
            interval = "1d";
        }

        String url = String.format(YAHOO_RANGE_URL, symbol, start, end, interval);
        log.info("[MarketData] Fetching range candles for {} interval={} — {}", symbol, interval, url);
        List<Map<String, Object>> data = fetchAndParse(symbol, url);
        historyCache.put(key, data);
        return data;
    }

    /**
     * Returns the current (or latest) market price for a given NSE symbol via
     * Yahoo Finance meta.regularMarketPrice.  Results are cached for
     * {@code RECENT_CACHE_TTL_SEC} seconds to avoid rate limits.
     *
     * @param symbol NSE symbol without {@code .NS}
     * @return current price
     */
    public double getCurrentPrice(String symbol) {
        long now = Instant.now().getEpochSecond();
        Long ts  = priceCacheTimestamps.get(symbol);
        if (ts != null && (now - ts) < RECENT_CACHE_TTL_SEC && priceCache.containsKey(symbol)) {
            log.info("[MarketData] Cache HIT (price) for {}", symbol);
            return priceCache.get(symbol);
        }

        String url = YAHOO_BASE + symbol + ".NS?interval=1m&range=1d";
        log.info("[MarketData] Fetching current price for {} — {}", symbol, url);
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response == null) throw new RuntimeException("Empty response from Yahoo Finance");
            double price = response
                    .path("chart").path("result").path(0)
                    .path("meta").path("regularMarketPrice").asDouble();
            if (price <= 0) throw new RuntimeException("Invalid price returned for " + symbol);
            priceCache.put(symbol, price);
            priceCacheTimestamps.put(symbol, now);
            return price;
        } catch (RuntimeException e) {
            log.error("[MarketData] getCurrentPrice failed for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to get current price for " + symbol + ": " + e.getMessage(), e);
        }
    }

    // ── PRIVATE HELPERS ─────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private List<Map<String, Object>> fetchAndParse(String symbol, String url) {

        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response == null) {
                throw new RuntimeException("Empty response from Yahoo Finance for symbol: " + symbol);
            }

            JsonNode chartNode = response.path("chart");
            if (chartNode.isMissingNode() || chartNode.path("error").isNull() == false) {
                JsonNode err = chartNode.path("error");
                if (!err.isNull() && !err.isMissingNode()) {
                    throw new RuntimeException("Yahoo Finance error: " + err.toString());
                }
            }

            JsonNode resultArr = chartNode.path("result");
            if (resultArr.isMissingNode() || resultArr.isNull() || resultArr.size() == 0) {
                throw new RuntimeException("No data returned from Yahoo Finance for symbol: " + symbol);
            }

            JsonNode result = resultArr.get(0);
            JsonNode timestamps = result.path("timestamp");
            JsonNode quote      = result.path("indicators").path("quote").path(0);

            JsonNode openArr   = quote.path("open");
            JsonNode highArr   = quote.path("high");
            JsonNode lowArr    = quote.path("low");
            JsonNode closeArr  = quote.path("close");
            JsonNode volumeArr = quote.path("volume");

            List<Map<String, Object>> candles = new ArrayList<>();

            for (int i = 0; i < timestamps.size(); i++) {
                // Skip candles with any null OHLCV value (market closed slots)
                JsonNode o = openArr.path(i);
                JsonNode h = highArr.path(i);
                JsonNode l = lowArr.path(i);
                JsonNode c = closeArr.path(i);
                JsonNode v = volumeArr.path(i);

                if (o.isNull() || h.isNull() || l.isNull() || c.isNull() ||
                    o.isMissingNode() || h.isMissingNode() || l.isMissingNode() || c.isMissingNode()) {
                    continue;
                }

                double openVal   = o.asDouble();
                double highVal   = h.asDouble();
                double lowVal    = l.asDouble();
                double closeVal  = c.asDouble();
                long   volumeVal = v.isNull() || v.isMissingNode() ? 0L : v.asLong();

                // Basic sanity check — skip obviously bad candles
                if (openVal <= 0 || highVal <= 0 || lowVal <= 0 || closeVal <= 0) {
                    continue;
                }

                Map<String, Object> candle = new HashMap<>();
                candle.put("time",   timestamps.get(i).asLong() * 1000L); // convert to ms
                candle.put("open",   openVal);
                candle.put("high",   highVal);
                candle.put("low",    lowVal);
                candle.put("close",  closeVal);
                candle.put("volume", volumeVal);

                candles.add(candle);
            }

            log.info("[MarketData] Returning {} clean candles for {}", candles.size(), symbol);
            return candles;

        } catch (RuntimeException e) {
            log.error("[MarketData] Failed to fetch data for {}: {}", symbol, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[MarketData] Failed to fetch data for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to load market data for " + symbol + ": " + e.getMessage(), e);
        }
    }
}
