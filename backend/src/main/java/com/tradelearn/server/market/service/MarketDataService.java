package com.tradelearn.server.market.service;

import com.tradelearn.server.market.provider.MarketDataProvider;

import com.tradelearn.server.dto.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Market data service with a two-level cache.
 *
 * <h3>Cache hierarchy</h3>
 * <ol>
 *   <li><b>L1 — in-memory LRU</b> (always active): bounded to
 *       {@value #MAX_CACHE_SIZE} entries using an access-order
 *       {@link LinkedHashMap} wrapped in {@link Collections#synchronizedMap}.
 *       Prevents the memory leak that would occur with an unbounded
 *       {@code ConcurrentHashMap} when many symbol/date combinations are
 *       requested, and avoids re-deserialising from Redis on repeated calls
 *       within the same JVM session.</li>
 *   <li><b>L2 — Redis</b> (optional, active when
 *       {@code redis.enabled=true} AND {@code alpha-vantage.enabled=true}):
 *       provided by {@link AlphaVantageCacheService}, which stores one
 *       full-history entry per symbol with a 6-hour TTL.  On a Redis miss
 *       the service calls the Alpha Vantage API.</li>
 * </ol>
 *
 * <p>Cache key format for L1: {@code "SYMBOL_START_END"}
 * (e.g. {@code "INFY_2024-01-01_2024-06-01"}).
 * Eviction is LRU — least recently accessed entries are removed first.
 *
 * <h3>Indian-stock fallback</h3>
 * When {@link AlphaVantageCacheService} is absent (Redis or Alpha Vantage
 * disabled) the request falls through to the underlying
 * {@link MarketDataProvider} — which may be
 * {@link com.tradelearn.server.market.provider.AlphaVantageProvider}
 * (when enabled) or
 * {@link com.tradelearn.server.market.provider.YahooFinanceProvider}
 * (legacy fallback).
 */
@Service
public class MarketDataService {

    private static final int MAX_CACHE_SIZE = 200;

    private final MarketDataProvider provider;

    /**
     * Optional Redis-backed cache layer — present only when both
     * {@code redis.enabled=true} and {@code alpha-vantage.enabled=true}.
     */
    @Autowired(required = false)
    private AlphaVantageCacheService redisCache;

    /**
     * Bounded LRU cache: access-order LinkedHashMap, synchronised, max 200 entries.
     * When the 201st entry would be inserted, the least recently accessed entry
     * is evicted.
     */
    private final Map<String, List<Candle>> localCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, /* accessOrder= */ true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Candle>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    public MarketDataService(MarketDataProvider provider) {
        this.provider = provider;
    }

    /**
     * Fetch historical OHLCV candles for the given symbol and date range.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>L1 in-memory LRU cache.</li>
     *   <li>L2 Redis cache (via {@link AlphaVantageCacheService}), if available.</li>
     *   <li>Live {@link MarketDataProvider} call.</li>
     * </ol>
     *
     * @param symbol NSE/US symbol string (e.g. "INFY", "AAPL")
     * @param start  Inclusive start date
     * @param end    Inclusive end date
     * @return list of candles, or empty list if the provider returns no data
     */
    public List<Candle> getHistoricalData(String symbol, LocalDate start, LocalDate end) {
        String cacheKey = String.format("%s_%s_%s", symbol, start, end);

        // ── L1: in-memory LRU ────────────────────────────────────────────
        List<Candle> localHit = localCache.get(cacheKey);
        if (localHit != null) {
            return localHit;
        }

        // ── L2: Redis (when available) ───────────────────────────────────
        if (redisCache != null) {
            List<Candle> redisHit = redisCache.getCandles(symbol, start, end);
            if (redisHit != null && !redisHit.isEmpty()) {
                localCache.put(cacheKey, redisHit);
                return redisHit;
            }
        }

        // ── L3: Live provider ────────────────────────────────────────────
        List<Candle> data = provider.getHistoricalData(symbol, start, end);
        if (data != null && !data.isEmpty()) {
            localCache.put(cacheKey, data);
        }

        return data != null ? data : List.of();
    }
}
