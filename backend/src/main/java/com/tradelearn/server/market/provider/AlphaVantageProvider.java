package com.tradelearn.server.market.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradelearn.server.dto.Candle;
import com.tradelearn.server.market.MarketDataException;
import com.tradelearn.server.market.config.AlphaVantageProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * {@link MarketDataProvider} backed by the Alpha Vantage REST API
 * ({@code TIME_SERIES_DAILY_ADJUSTED}).
 *
 * <h3>Activation</h3>
 * This bean is created only when {@code alpha-vantage.enabled=true} AND the
 * {@code MARKET_DATA_API_KEY} environment variable is set.  When active, it is
 * marked {@code @Primary} so Spring autowires it ahead of
 * {@link YahooFinanceProvider}.
 *
 * <h3>Rate-limit handling</h3>
 * Alpha Vantage free tier: <b>5 requests/minute, 500/day</b>.
 * When the API returns a {@code "Note"} or {@code "Information"} key, this
 * provider throws a {@link MarketDataException} so the controller can return a
 * clean {@code 503} to the frontend instead of an empty or malformed response.
 *
 * <h3>Symbol mapping</h3>
 * Indian NSE symbols (RELIANCE, TCS, etc.) are served from classpath JSON
 * files by the fallback path in {@code MarketDataService}; Alpha Vantage is
 * used for US symbols (AAPL, MSFT, …).  Pass the raw symbol as given — the
 * provider does not append exchange suffixes.
 */
@Service
@Primary
@ConditionalOnProperty(name = "alpha-vantage.enabled", havingValue = "true")
public class AlphaVantageProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageProvider.class);

    /** Alpha Vantage JSON key for the rate-limit "courtesy" message. */
    private static final String NOTE_KEY        = "Note";
    /** Alpha Vantage JSON key for plan / key-error information messages. */
    private static final String INFORMATION_KEY = "Information";
    /** Top-level key in TIME_SERIES_DAILY_ADJUSTED response. */
    private static final String TIME_SERIES_KEY = "Time Series (Daily)";

    private final AlphaVantageProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AlphaVantageProvider(AlphaVantageProperties props,
                                ObjectMapper objectMapper) {
        this.props        = props;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Package-private constructor for unit tests — allows injecting a mock
     * {@link RestTemplate}.
     */
    AlphaVantageProvider(AlphaVantageProperties props,
                         ObjectMapper objectMapper,
                         RestTemplate restTemplate) {
        this.props        = props;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    // ── MarketDataProvider contract ────────────────────────────────────────

    /**
     * Fetch daily OHLCV candles from Alpha Vantage for {@code symbol},
     * filtered to {@code [start, end]} inclusive.
     *
     * <p>Uses {@code outputsize=full} to get up to 20 years of data, so a
     * single API call covers any date range.  This is intentional: the Redis
     * caching layer in {@link com.tradelearn.server.market.service.AlphaVantageCacheService}
     * stores the full response, so subsequent date-range queries for the same
     * symbol are served from cache without another API call.
     *
     * @throws MarketDataException if the API returns a rate-limit or error response.
     */
    @Override
    public List<Candle> getHistoricalData(String symbol, LocalDate start, LocalDate end) {
        String apiKey = props.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[AlphaVantage] MARKET_DATA_API_KEY is not set — cannot fetch market data");
            throw new MarketDataException("alpha-vantage",
                    "MARKET_DATA_API_KEY environment variable is not set");
        }

        String url = buildUrl(symbol, apiKey);
        log.debug("[AlphaVantage] Fetching {} ({} → {})", symbol, start, end);

        String rawJson;
        try {
            rawJson = restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.warn("[AlphaVantage] Network error fetching {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }

        if (rawJson == null || rawJson.isBlank()) {
            log.warn("[AlphaVantage] Empty response for symbol '{}'", symbol);
            return Collections.emptyList();
        }

        return parseAndFilter(rawJson, symbol, start, end);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String buildUrl(String symbol, String apiKey) {
        return String.format(
                "%s?function=TIME_SERIES_DAILY_ADJUSTED&symbol=%s&outputsize=full&apikey=%s",
                props.getBaseUrl(), symbol, apiKey
        );
    }

    /**
     * Parses the Alpha Vantage JSON, checks for error/rate-limit indicators,
     * maps entries to {@link Candle} objects, and filters by date range.
     */
    private List<Candle> parseAndFilter(String rawJson, String symbol,
                                        LocalDate start, LocalDate end) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            log.error("[AlphaVantage] Failed to parse JSON for '{}': {}", symbol, e.getMessage());
            return Collections.emptyList();
        }

        // Rate-limit notice
        if (root.has(NOTE_KEY)) {
            String note = root.get(NOTE_KEY).asText();
            log.warn("[AlphaVantage] Rate-limit hit for '{}': {}", symbol, note);
            throw new MarketDataException("alpha-vantage",
                    "Alpha Vantage rate limit reached — please wait a moment and retry. " +
                    "Consider enabling Redis caching to reduce API calls.");
        }

        // Key / plan error
        if (root.has(INFORMATION_KEY)) {
            String info = root.get(INFORMATION_KEY).asText();
            log.error("[AlphaVantage] API key/plan error for '{}': {}", symbol, info);
            throw new MarketDataException("alpha-vantage",
                    "Alpha Vantage API key error: " + info);
        }

        JsonNode timeSeries = root.get(TIME_SERIES_KEY);
        if (timeSeries == null || timeSeries.isEmpty()) {
            log.warn("[AlphaVantage] No '{}' data in response for '{}'", TIME_SERIES_KEY, symbol);
            return Collections.emptyList();
        }

        List<Candle> candles = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = timeSeries.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String dateStr = entry.getKey();          // "2024-01-15"
            JsonNode day   = entry.getValue();

            LocalDate date;
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception e) {
                continue; // skip malformed date keys
            }

            // Filter to requested window
            if (date.isBefore(start) || date.isAfter(end)) {
                continue;
            }

            try {
                Candle c = Candle.builder()
                        .date(dateStr)
                        .open(parseDouble(day, "1. open"))
                        .high(parseDouble(day, "2. high"))
                        .low(parseDouble(day, "3. low"))
                        .close(parseDouble(day, "4. close"))
                        .volume(parseLong(day, "6. volume"))
                        .build();
                candles.add(c);
            } catch (Exception e) {
                log.debug("[AlphaVantage] Skipping malformed candle entry {}: {}", dateStr, e.getMessage());
            }
        }

        // Alpha Vantage returns data newest-first; sort ascending for the chart
        candles.sort((a, b) -> a.getLocalDate().compareTo(b.getLocalDate()));

        log.info("[AlphaVantage] Returning {} candles for '{}' ({} → {})",
                candles.size(), symbol, start, end);
        return candles;
    }

    private static double parseDouble(JsonNode node, String key) {
        JsonNode field = node.get(key);
        if (field == null) throw new IllegalArgumentException("Missing field: " + key);
        return field.asDouble();
    }

    private static long parseLong(JsonNode node, String key) {
        JsonNode field = node.get(key);
        if (field == null) throw new IllegalArgumentException("Missing field: " + key);
        return field.asLong();
    }
}
