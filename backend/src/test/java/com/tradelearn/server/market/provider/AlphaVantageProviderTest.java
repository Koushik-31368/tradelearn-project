package com.tradelearn.server.market.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradelearn.server.dto.Candle;
import com.tradelearn.server.market.MarketDataException;
import com.tradelearn.server.market.config.AlphaVantageProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlphaVantageProvider}.
 *
 * <p>The real Alpha Vantage API is never called — {@link RestTemplate} is mocked.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>Happy path — JSON parsed, date range filtered, sorted ascending</li>
 *   <li>Rate-limit {@code "Note"} response → {@link MarketDataException}</li>
 *   <li>Key/plan {@code "Information"} response → {@link MarketDataException}</li>
 *   <li>Network error ({@link ResourceAccessException}) → empty list, no crash</li>
 *   <li>Candles outside requested date range are excluded</li>
 *   <li>Blank API key → {@link MarketDataException}</li>
 *   <li>Empty JSON response → empty list</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AlphaVantageProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private AlphaVantageProperties props;
    private AlphaVantageProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END   = LocalDate.of(2024, 1, 31);

    /** Minimal valid Alpha Vantage TIME_SERIES_DAILY_ADJUSTED JSON. */
    private static final String HAPPY_JSON = """
            {
              "Meta Data": { "1. Information": "Daily Adjusted Time Series" },
              "Time Series (Daily)": {
                "2024-01-15": {
                  "1. open": "185.00",
                  "2. high": "190.00",
                  "3. low":  "183.00",
                  "4. close": "188.50",
                  "5. adjusted close": "188.50",
                  "6. volume": "60000000",
                  "7. dividend amount": "0.0000",
                  "8. split coefficient": "1.0"
                },
                "2024-01-10": {
                  "1. open": "180.00",
                  "2. high": "184.00",
                  "3. low":  "178.00",
                  "4. close": "182.00",
                  "5. adjusted close": "182.00",
                  "6. volume": "55000000",
                  "7. dividend amount": "0.0000",
                  "8. split coefficient": "1.0"
                }
              }
            }
            """;

    /** Outside requested date range. */
    private static final String OUT_OF_RANGE_JSON = """
            {
              "Time Series (Daily)": {
                "2023-12-01": {
                  "1. open": "175.00",
                  "2. high": "178.00",
                  "3. low":  "173.00",
                  "4. close": "176.00",
                  "5. adjusted close": "176.00",
                  "6. volume": "45000000",
                  "7. dividend amount": "0.0",
                  "8. split coefficient": "1.0"
                }
              }
            }
            """;

    private static final String RATE_LIMIT_JSON = """
            {
              "Note": "Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day."
            }
            """;

    private static final String KEY_ERROR_JSON = """
            {
              "Information": "The **demo** API key is for demo purposes only. Please claim your free API key."
            }
            """;

    @BeforeEach
    void setUp() {
        props = new AlphaVantageProperties();
        props.setEnabled(true);
        props.setApiKey("TEST_KEY");
        props.setBaseUrl("https://www.alphavantage.co/query");

        provider = new AlphaVantageProvider(props, objectMapper, restTemplate);
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void getHistoricalData_happyPath_returnsCandlesSortedAscending() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(HAPPY_JSON);

        List<Candle> candles = provider.getHistoricalData("AAPL", START, END);

        assertThat(candles).hasSize(2);

        // Sorted ascending by date
        assertThat(candles.get(0).getDate()).isEqualTo("2024-01-10");
        assertThat(candles.get(1).getDate()).isEqualTo("2024-01-15");

        Candle first = candles.get(0);
        assertThat(first.getOpen()).isEqualTo(180.0);
        assertThat(first.getHigh()).isEqualTo(184.0);
        assertThat(first.getLow()).isEqualTo(178.0);
        assertThat(first.getClose()).isEqualTo(182.0);
        assertThat(first.getVolume()).isEqualTo(55_000_000L);
    }

    @Test
    void getHistoricalData_happyPath_callsApiOnce() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(HAPPY_JSON);

        provider.getHistoricalData("AAPL", START, END);

        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    // ── Date range filtering ────────────────────────────────────────────────

    @Test
    void getHistoricalData_outOfRangeCandles_excluded() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(OUT_OF_RANGE_JSON);

        // Request window: Jan 2024 — candle is from Dec 2023
        List<Candle> candles = provider.getHistoricalData("AAPL", START, END);

        assertThat(candles).isEmpty();
    }

    // ── Rate-limit response ─────────────────────────────────────────────────

    @Test
    void getHistoricalData_rateLimitNote_throwsMarketDataException() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(RATE_LIMIT_JSON);

        assertThatThrownBy(() -> provider.getHistoricalData("AAPL", START, END))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("rate limit")
                .satisfies(ex -> assertThat(((MarketDataException) ex).getSource())
                        .isEqualTo("alpha-vantage"));
    }

    // ── API key / plan error ────────────────────────────────────────────────

    @Test
    void getHistoricalData_keyError_throwsMarketDataException() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(KEY_ERROR_JSON);

        assertThatThrownBy(() -> provider.getHistoricalData("AAPL", START, END))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("API key error")
                .satisfies(ex -> assertThat(((MarketDataException) ex).getSource())
                        .isEqualTo("alpha-vantage"));
    }

    // ── Network error ───────────────────────────────────────────────────────

    @Test
    void getHistoricalData_networkError_returnsEmptyListNoException() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        List<Candle> candles = provider.getHistoricalData("AAPL", START, END);

        assertThat(candles).isEmpty();
    }

    // ── Blank API key ───────────────────────────────────────────────────────

    @Test
    void getHistoricalData_blankApiKey_throwsMarketDataException() {
        props.setApiKey("");
        // RestTemplate should never be called when key is blank
        verifyNoInteractions(restTemplate);

        assertThatThrownBy(() -> provider.getHistoricalData("AAPL", START, END))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("MARKET_DATA_API_KEY");
    }

    // ── Empty JSON response ─────────────────────────────────────────────────

    @Test
    void getHistoricalData_emptyResponse_returnsEmptyList() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("");

        List<Candle> candles = provider.getHistoricalData("AAPL", START, END);

        assertThat(candles).isEmpty();
    }

    // ── JSON with no time-series key ────────────────────────────────────────

    @Test
    void getHistoricalData_missingTimeSeries_returnsEmptyList() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"Meta Data\": {}}");

        List<Candle> candles = provider.getHistoricalData("AAPL", START, END);

        assertThat(candles).isEmpty();
    }
}
