package com.tradelearn.server.market.service;

import com.tradelearn.server.market.provider.MarketDataProvider;

import com.tradelearn.server.dto.Candle;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Market data service with a bounded LRU cache.
 *
 * <p>The cache is bounded to {@value #MAX_CACHE_SIZE} entries using an
 * access-order {@link LinkedHashMap} wrapped in {@link Collections#synchronizedMap}.
 * This prevents the memory leak that would occur with an unbounded
 * {@code ConcurrentHashMap} when many different symbol/date combinations are requested.
 *
 * <p>Cache key format: {@code "SYMBOL_START_END"} (e.g. {@code "INFY_2024-01-01_2024-06-01"}).
 * Eviction is LRU — least recently accessed entries are removed first.
 */
@Service
public class MarketDataService {

    private static final int MAX_CACHE_SIZE = 200;

    private final MarketDataProvider provider;

    /**
     * Bounded LRU cache: access-order LinkedHashMap, synchronized, max 200 entries.
     * When the 201st entry would be inserted, the least recently accessed entry is evicted.
     */
    private final Map<String, List<Candle>> cache = Collections.synchronizedMap(
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
     * Results are cached by (symbol, start, end) key to avoid redundant
     * Yahoo Finance API calls across repeated game starts.
     *
     * @param symbol NSE symbol without .NS suffix (e.g. "INFY")
     * @param start  inclusive start date
     * @param end    inclusive end date
     * @return list of candles, or empty list if the provider returns no data
     */
    public List<Candle> getHistoricalData(String symbol, LocalDate start, LocalDate end) {
        String cacheKey = String.format("%s_%s_%s", symbol, start, end);

        // Check cache first — synchronized map, so this is thread-safe
        List<Candle> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Candle> data = provider.getHistoricalData(symbol, start, end);
        if (data != null && !data.isEmpty()) {
            cache.put(cacheKey, data);
        }

        return data != null ? data : List.of();
    }
}

