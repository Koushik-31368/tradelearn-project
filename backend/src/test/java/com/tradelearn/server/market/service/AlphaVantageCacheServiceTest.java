package com.tradelearn.server.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradelearn.server.dto.Candle;
import com.tradelearn.server.market.MarketDataException;
import com.tradelearn.server.market.provider.AlphaVantageProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlphaVantageCacheService}.
 *
 * <p>Neither Redis nor the Alpha Vantage API is called for real —
 * {@link StringRedisTemplate} and {@link AlphaVantageProvider} are mocked.
 *
 * <p>The {@link ObjectMapper} is configured with {@link JavaTimeModule} so that
 * {@link Candle#getLocalDate()} (a computed {@link LocalDate} property) can be
 * serialised when building the in-memory JSON fixture used by cache-hit tests.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>Cache miss → provider called, result stored in Redis</li>
 *   <li>Cache hit → provider NOT called</li>
 *   <li>Cache hit with date-range filter applied</li>
 *   <li>Redis read error → falls through to provider (graceful degradation)</li>
 *   <li>Redis write error → provider result still returned (no crash)</li>
 *   <li>Empty provider result → not stored in Redis</li>
 *   <li>Provider throws MarketDataException → exception propagated to caller</li>
 *   <li>Symbol is uppercased for Redis key lookup</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null") // Mockito matchers (anyString, eq, any) return null at compile-time; safe at runtime
class AlphaVantageCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private AlphaVantageProvider alphaVantageProvider;

    private AlphaVantageCacheService cacheService;

    /**
     * Plain ObjectMapper — works without JavaTimeModule because {@code Candle.getLocalDate()}
     * is annotated {@code @JsonIgnore} and won't be serialised.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END   = LocalDate.of(2024, 1, 31);

    private static final List<Candle> SAMPLE_CANDLES = List.of(
            Candle.builder().date("2024-01-10").open(180).high(184).low(178).close(182).volume(55_000_000L).build(),
            Candle.builder().date("2024-01-15").open(185).high(190).low(183).close(188).volume(60_000_000L).build()
    );

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cacheService = new AlphaVantageCacheService(redisTemplate, alphaVantageProvider, objectMapper);
    }

    // ── Cache miss: provider called, result stored ──────────────────────────

    @Test
    void getCandles_cacheMiss_callsProviderAndStoresInRedis() {
        when(valueOps.get(anyString())).thenReturn(null); // cache miss
        when(alphaVantageProvider.getHistoricalData(eq("AAPL"), any(), any()))
                .thenReturn(SAMPLE_CANDLES);

        List<Candle> result = cacheService.getCandles("AAPL", START, END);

        assertThat(result).hasSize(2);
        // Provider was called once for the full history
        verify(alphaVantageProvider, times(1)).getHistoricalData(eq("AAPL"), any(), any());
        // Result was stored in Redis
        verify(valueOps, times(1)).set(eq("av:candles:AAPL"), anyString(), any());
    }

    // ── Cache hit: provider NOT called ─────────────────────────────────────

    @Test
    void getCandles_cacheHit_providerNotCalled() throws Exception {
        String cachedJson = objectMapper.writeValueAsString(SAMPLE_CANDLES);
        when(valueOps.get(anyString())).thenReturn(cachedJson);

        List<Candle> result = cacheService.getCandles("AAPL", START, END);

        assertThat(result).hasSize(2);
        verifyNoInteractions(alphaVantageProvider);
    }

    // ── Cache hit: date range filter applied ────────────────────────────────

    @Test
    void getCandles_cacheHit_filtersToRequestedDateRange() throws Exception {
        // Cache has candles for Jan 10 and Jan 15; request only Jan 10-12
        String cachedJson = objectMapper.writeValueAsString(SAMPLE_CANDLES);
        when(valueOps.get(anyString())).thenReturn(cachedJson);

        LocalDate narrowStart = LocalDate.of(2024, 1, 10);
        LocalDate narrowEnd   = LocalDate.of(2024, 1, 12);
        List<Candle> result = cacheService.getCandles("AAPL", narrowStart, narrowEnd);

        // Only 2024-01-10 falls within Jan 10-12
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo("2024-01-10");
    }

    // ── Redis read error: falls through to provider ─────────────────────────

    @Test
    void getCandles_redisReadError_fallsThroughToProvider() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));
        when(alphaVantageProvider.getHistoricalData(eq("AAPL"), any(), any()))
                .thenReturn(SAMPLE_CANDLES);

        List<Candle> result = cacheService.getCandles("AAPL", START, END);

        assertThat(result).hasSize(2);
        verify(alphaVantageProvider, times(1)).getHistoricalData(eq("AAPL"), any(), any());
    }

    // ── Redis write error: provider result still returned ──────────────────

    @Test
    void getCandles_redisWriteError_resultStillReturned() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(alphaVantageProvider.getHistoricalData(eq("AAPL"), any(), any()))
                .thenReturn(SAMPLE_CANDLES);
        doThrow(new RuntimeException("Redis write failed"))
                .when(valueOps).set(anyString(), anyString(), any());

        // Should NOT throw — graceful degradation; result is still returned
        List<Candle> result = cacheService.getCandles("AAPL", START, END);

        // Provider result is propagated despite Redis write error
        assertThat(result).hasSize(2);
    }

    // ── Empty provider result: not stored in Redis ──────────────────────────

    @Test
    void getCandles_emptyProviderResult_notStoredInRedis() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(alphaVantageProvider.getHistoricalData(eq("AAPL"), any(), any()))
                .thenReturn(List.of());

        List<Candle> result = cacheService.getCandles("AAPL", START, END);

        assertThat(result).isEmpty();
        // Empty list should not be cached
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    // ── Provider throws MarketDataException: propagated ─────────────────────

    @Test
    void getCandles_providerThrowsMarketDataException_propagatedToCaller() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(alphaVantageProvider.getHistoricalData(eq("AAPL"), any(), any()))
                .thenThrow(new MarketDataException("alpha-vantage", "Rate limit reached"));

        assertThatThrownBy(() -> cacheService.getCandles("AAPL", START, END))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("Rate limit");
    }

    // ── Symbol is uppercased before Redis key lookup ─────────────────────────

    @Test
    void getCandles_symbolIsUppercasedForRedisKey() {
        when(valueOps.get("av:candles:AAPL")).thenReturn(null);
        when(alphaVantageProvider.getHistoricalData(anyString(), any(), any()))
                .thenReturn(List.of());

        // Pass lower-case symbol
        cacheService.getCandles("aapl", START, END);

        verify(valueOps).get("av:candles:AAPL");
    }
}
