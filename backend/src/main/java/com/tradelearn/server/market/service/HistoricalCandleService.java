package com.tradelearn.server.market.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Loads historical BTC candle data from a bundled CSV resource and provides
 * random segments for use in practice mode. Uses a package-private inner record
 * as the canonical candle representation (no dependency on the deleted model.Candle).
 */
@Service
public class HistoricalCandleService {

    /**
     * Simple OHLCV candle representation for historical data.
     * Not a JPA entity — exists only for in-memory use.
     */
    public static class Candle {
        private long timestamp;
        private double open;
        private double high;
        private double low;
        private double close;
        private double volume;

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public double getOpen() { return open; }
        public void setOpen(double open) { this.open = open; }
        public double getHigh() { return high; }
        public void setHigh(double high) { this.high = high; }
        public double getLow() { return low; }
        public void setLow(double low) { this.low = low; }
        public double getClose() { return close; }
        public void setClose(double close) { this.close = close; }
        public double getVolume() { return volume; }
        public void setVolume(double volume) { this.volume = volume; }
    }

    private final List<Candle> btcCandles = new ArrayList<>();

    @PostConstruct
    public void loadCandles() throws Exception {
        InputStream is = getClass().getResourceAsStream("/candles/btc.csv");
        if (is == null) {
            // Candle CSV not present — service will return empty segments
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        reader.lines().skip(1).forEach(line -> {
            String[] parts = line.split(",");
            if (parts.length < 6) return;
            Candle c = new Candle();
            c.setTimestamp(Long.parseLong(parts[0].trim()));
            c.setOpen(Double.parseDouble(parts[1].trim()));
            c.setHigh(Double.parseDouble(parts[2].trim()));
            c.setLow(Double.parseDouble(parts[3].trim()));
            c.setClose(Double.parseDouble(parts[4].trim()));
            c.setVolume(Double.parseDouble(parts[5].trim()));
            btcCandles.add(c);
        });
    }

    public List<Candle> getRandomSegment(int length) {
        if (btcCandles.size() < length) {
            return btcCandles;
        }
        int start = new Random().nextInt(btcCandles.size() - length);
        return btcCandles.subList(start, start + length);
    }
}
