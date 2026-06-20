package com.tradelearn.server.market.service;

import com.tradelearn.server.dto.Candle;
import com.tradelearn.server.market.provider.MarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MarketDataService}.
 *
 * <p>Coverage areas:
 * <ul>
 *   <li>Cache miss → delegates to provider</li>
 *   <li>Cache hit → provider is NOT called a second time</li>
 *   <li>Provider returns null → service returns empty list (null-safe)</li>
 *   <li>Provider returns empty list → result is not cached</li>
 *   <li>{@link Candle#getLocalDate()} parses ISO date strings correctly</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private MarketDataProvider provider;

    private MarketDataService service;

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END   = LocalDate.of(2024, 6, 1);

    @BeforeEach
    void setUp() {
        service = new MarketDataService(provider);
    }

    // ── Cache miss ───────────────────────────────────────────────────────────

    @Test
    void getHistoricalData_cacheMiss_callsProvider() {
        List<Candle> candles = List.of(
                Candle.builder().date("2024-01-02").open(1450).high(1462).low(1440).close(1458).volume(3_000_000L).build()
        );
        when(provider.getHistoricalData("INFY", START, END)).thenReturn(candles);

        List<Candle> result = service.getHistoricalData("INFY", START, END);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClose()).isEqualTo(1458.0);
        verify(provider, times(1)).getHistoricalData("INFY", START, END);
    }

    // ── Cache hit ────────────────────────────────────────────────────────────

    @Test
    void getHistoricalData_cacheHit_providerCalledOnlyOnce() {
        List<Candle> candles = List.of(
                Candle.builder().date("2024-01-02").open(1450).high(1462).low(1440).close(1458).volume(3_000_000L).build()
        );
        when(provider.getHistoricalData("INFY", START, END)).thenReturn(candles);

        // First call — cache miss
        service.getHistoricalData("INFY", START, END);
        // Second call — should be a cache hit
        List<Candle> result = service.getHistoricalData("INFY", START, END);

        assertThat(result).hasSize(1);
        // Provider must only have been called once despite two service calls
        verify(provider, times(1)).getHistoricalData("INFY", START, END);
    }

    // ── Null provider response ────────────────────────────────────────────────

    @Test
    void getHistoricalData_providerReturnsNull_returnsEmptyList() {
        when(provider.getHistoricalData("TATASTEEL", START, END)).thenReturn(null);

        List<Candle> result = service.getHistoricalData("TATASTEEL", START, END);

        assertThat(result).isEmpty();
    }

    // ── Empty provider response ───────────────────────────────────────────────

    @Test
    void getHistoricalData_providerReturnsEmpty_notCached() {
        when(provider.getHistoricalData("WIPRO", START, END)).thenReturn(List.of());

        service.getHistoricalData("WIPRO", START, END);
        service.getHistoricalData("WIPRO", START, END);

        // Should call provider both times since empty list is not cached
        verify(provider, times(2)).getHistoricalData("WIPRO", START, END);
    }

    // ── Candle.getLocalDate() ────────────────────────────────────────────────

    @Test
    void candle_getLocalDate_parsesIsoDateCorrectly() {
        Candle c = Candle.builder().date("2024-06-15").build();
        assertThat(c.getLocalDate()).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    @Test
    void candle_getLocalDate_fallsBackToEpochOnMalformedDate() {
        Candle c = Candle.builder().date("not-a-date").build();
        assertThat(c.getLocalDate()).isEqualTo(LocalDate.EPOCH);
    }

    @Test
    void candle_getLocalDate_fallsBackToEpochOnNullDate() {
        Candle c = new Candle();
        assertThat(c.getLocalDate()).isEqualTo(LocalDate.EPOCH);
    }

    // ── Cache key isolation (different symbols / date ranges) ────────────────

    @Test
    void getHistoricalData_differentSymbols_cachedSeparately() {
        List<Candle> infyCandles = List.of(Candle.builder().date("2024-01-02").close(1458).build());
        List<Candle> tcsCandles  = List.of(Candle.builder().date("2024-01-02").close(3650).build());

        when(provider.getHistoricalData("INFY", START, END)).thenReturn(infyCandles);
        when(provider.getHistoricalData("TCS",  START, END)).thenReturn(tcsCandles);

        List<Candle> infy = service.getHistoricalData("INFY", START, END);
        List<Candle> tcs  = service.getHistoricalData("TCS",  START, END);

        assertThat(infy.get(0).getClose()).isEqualTo(1458.0);
        assertThat(tcs.get(0).getClose()).isEqualTo(3650.0);

        // Each provider method called exactly once
        verify(provider).getHistoricalData("INFY", START, END);
        verify(provider).getHistoricalData("TCS",  START, END);
    }
}
