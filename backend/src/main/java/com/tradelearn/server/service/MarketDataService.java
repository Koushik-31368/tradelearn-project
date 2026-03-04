package com.tradelearn.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches historical OHLCV candles from Yahoo Finance for a given NSE symbol.
 *
 * <p>Endpoint used:
 * {@code https://query1.finance.yahoo.com/v8/finance/chart/{symbol}.NS?interval=5m&range=5d}
 * </p>
 *
 * <p>Yahoo Finance sometimes embeds {@code null} at closed-market timestamps.
 * Any candle whose OHLCV contains a null value is silently skipped so the
 * frontend chart always receives clean numeric data.</p>
 */
@Slf4j
@Service
public class MarketDataService {

    private static final String YAHOO_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s.NS?interval=5m&range=5d";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Returns up to ~375 five-minute candles for the given NSE stock symbol
     * (last 5 trading days worth of 5-minute bars).
     *
     * @param symbol NSE symbol without the {@code .NS} suffix, e.g. {@code INFY}
     * @return list of candle maps with keys: {@code time, open, high, low, close, volume}
     * @throws RuntimeException if Yahoo Finance is unreachable or returns no data
     */
    public List<Map<String, Object>> getHistoricalData(String symbol) {
        String url = String.format(YAHOO_URL, symbol);
        log.info("[MarketData] Fetching candles for {} — {}", symbol, url);

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

        } catch (Exception e) {
            log.error("[MarketData] Failed to fetch data for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to load market data for " + symbol + ": " + e.getMessage(), e);
        }
    }
}
