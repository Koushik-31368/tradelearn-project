package com.tradelearn.server.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradelearn.server.dto.Candle;
import com.tradelearn.server.market.provider.AlphaVantageProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed caching layer for Alpha Vantage candle data.
 *
 * <h3>Cache key format</h3>
 * {@code av:candles:{SYMBOL}} — one entry per symbol, holding the full
 * {@code outputsize=full} dataset (≤ 20 years of daily candles).  Callers
 * filter by date range after the cache is loaded.
 *
 * <h3>TTL strategy</h3>
 * Daily EOD candles are cached for <b>6 hours</b>.  This is safe because
 * Alpha Vantage's free-tier {@code TIME_SERIES_DAILY_ADJUSTED} data is
 * updated once per trading day after market close.
 *
 * <h3>Activation</h3>
 * Requires <em>both</em> {@code redis.enabled=true} AND
 * {@code alpha-vantage.enabled=true}.  If either is absent this bean is
 * skipped and {@link MarketDataService} falls straight through to the
 * {@link AlphaVantageProvider} (which has its own in-memory cache at the
 * {@code MarketDataService} level).
 *
 * <h3>Graceful degradation</h3>
 * Any Redis {@link Exception} is caught, logged at WARN level, and the call
 * falls through to the live provider — the endpoint never fails because of a
 * Redis outage.
 */
@Service
@ConditionalOnProperty(name = {"redis.enabled", "alpha-vantage.enabled"}, havingValue = "true")
public class AlphaVantageCacheService {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageCacheService.class);

    /** Redis key prefix for all Alpha Vantage candle entries. */
    private static final String KEY_PREFIX = "av:candles:";

    /** How long to keep a full daily-series entry in Redis. */
    private static final Duration TTL_DAILY = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;
    private final AlphaVantageProvider alphaVantageProvider;
    private final ObjectMapper objectMapper;

    public AlphaVantageCacheService(StringRedisTemplate redisTemplate,
                                    AlphaVantageProvider alphaVantageProvider,
                                    ObjectMapper objectMapper) {
        this.redisTemplate         = redisTemplate;
        this.alphaVantageProvider  = alphaVantageProvider;
        this.objectMapper          = objectMapper;
    }

    /**
     * Fetch candles for {@code symbol} in the given date range.
     *
     * <ol>
     *   <li>Try Redis → if hit, deserialise and filter by date range.</li>
     *   <li>On miss, call {@link AlphaVantageProvider} for the full history,
     *       store the full list in Redis, then filter and return.</li>
     * </ol>
     *
     * @param symbol Bare symbol string (e.g. {@code "AAPL"}, {@code "RELIANCE"})
     * @param start  Inclusive start date
     * @param end    Inclusive end date
     * @return Filtered, ascending-sorted candle list (may be empty).
     */
    public List<Candle> getCandles(String symbol, LocalDate start, LocalDate end) {
        String redisKey = KEY_PREFIX + symbol.toUpperCase();

        // ── L1: Redis cache check ──────────────────────────────────────────
        try {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            String cached = ops.get(redisKey);
            if (cached != null) {
                log.debug("[AVCache] Redis HIT for '{}'", symbol);
                List<Candle> all = objectMapper.readValue(cached, new TypeReference<>() {});
                return filterByDateRange(all, start, end);
            }
        } catch (Exception e) {
            log.warn("[AVCache] Redis read error for '{}' — falling through to API: {}", symbol, e.getMessage());
        }

        // ── L2: Live API call ──────────────────────────────────────────────
        // We always fetch the full history so the cached entry covers any future
        // date-range query for the same symbol, maximising cache utilisation.
        log.debug("[AVCache] Redis MISS for '{}' — calling Alpha Vantage", symbol);
        List<Candle> fresh = alphaVantageProvider.getHistoricalData(
                symbol,
                LocalDate.of(2000, 1, 1),   // wide start: get all available history
                LocalDate.now()
        );

        // ── Store in Redis (best-effort) ───────────────────────────────────
        if (!fresh.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(fresh);
                redisTemplate.opsForValue().set(redisKey, json, TTL_DAILY);
                log.debug("[AVCache] Stored {} candles for '{}' in Redis (TTL={}h)",
                        fresh.size(), symbol, TTL_DAILY.toHours());
            } catch (Exception e) {
                log.warn("[AVCache] Redis write error for '{}' — cache not updated: {}", symbol, e.getMessage());
            }
        }

        return filterByDateRange(fresh, start, end);
    }

    /**
     * Evict the cached candle list for a symbol (e.g. after a forced refresh).
     */
    public void evict(String symbol) {
        try {
            redisTemplate.delete(KEY_PREFIX + symbol.toUpperCase());
            log.info("[AVCache] Evicted Redis cache for '{}'", symbol);
        } catch (Exception e) {
            log.warn("[AVCache] Redis evict error for '{}': {}", symbol, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<Candle> filterByDateRange(List<Candle> candles, LocalDate start, LocalDate end) {
        if (candles == null) return Collections.emptyList();
        return candles.stream()
                .filter(c -> {
                    LocalDate d = c.getLocalDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .toList();
    }
}
