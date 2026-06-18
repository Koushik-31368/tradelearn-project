package com.tradelearn.server.service;

import com.tradelearn.server.dto.Candle;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class YahooFinanceProvider implements MarketDataProvider {

    private final RestTemplate restTemplate;

    public YahooFinanceProvider() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    @SuppressWarnings("null")
    public List<Candle> getHistoricalData(String symbol, LocalDate start, LocalDate end) {
        long period1 = start.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = end.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        String url = String.format(
            "https://query1.finance.yahoo.com/v7/finance/download/%s?period1=%d&period2=%d&interval=1d&events=history",
            symbol, period1, period2
        );

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return parseCsv(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // Return empty list on failure for now
        }
    }

    private List<Candle> parseCsv(String csvData) {
        List<Candle> candles = new ArrayList<>();
        if (csvData == null || csvData.isEmpty()) return candles;

        String[] lines = csvData.split("\n");
        // Skip header line (index 0)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(",");
            if (parts.length < 7) continue;

            // Date,Open,High,Low,Close,Adj Close,Volume
            // Handle null/empty data from Yahoo ("null")
            if (parts[1].equalsIgnoreCase("null")) continue;

            try {
                Candle candle = Candle.builder()
                        .date(parts[0])
                        .open(Double.parseDouble(parts[1]))
                        .high(Double.parseDouble(parts[2]))
                        .low(Double.parseDouble(parts[3]))
                        .close(Double.parseDouble(parts[4]))
                        .volume(Long.parseLong(parts[6]))
                        .build();
                candles.add(candle);
            } catch (NumberFormatException e) {
                // Ignore parse errors for specific lines
            }
        }
        return candles;
    }
}
