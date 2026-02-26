package com.tradelearn.server.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.tradelearn.server.model.Candle;

import jakarta.annotation.PostConstruct;

@Service
public class HistoricalCandleService {
    private final List<Candle> btcCandles = new ArrayList<>();

    @PostConstruct
    public void loadCandles() throws Exception {
        InputStream is = getClass().getResourceAsStream("/candles/btc.csv");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        reader.lines().skip(1).forEach(line -> {
            String[] parts = line.split(",");
            Candle c = new Candle();
            c.setTimestamp(Long.parseLong(parts[0]));
            c.setOpen(Double.parseDouble(parts[1]));
            c.setHigh(Double.parseDouble(parts[2]));
            c.setLow(Double.parseDouble(parts[3]));
            c.setClose(Double.parseDouble(parts[4]));
            c.setVolume(Double.parseDouble(parts[5]));
            btcCandles.add(c);
        });
    }

    public List<Candle> getRandomSegment(int length) {
        int start = new Random().nextInt(btcCandles.size() - length);
        return btcCandles.subList(start, start + length);
    }
}
