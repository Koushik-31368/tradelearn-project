package com.tradelearn.server.service;

import com.tradelearn.server.dto.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketDataService {

    private final MarketDataProvider provider;
    
    // Simple in-memory cache: "SYMBOL_START_END" -> List<Candle>
    private final Map<String, List<Candle>> cache = new ConcurrentHashMap<>();

    @Autowired
    public MarketDataService(YahooFinanceProvider provider) {
        this.provider = provider;
    }

    public List<Candle> getHistoricalData(String symbol, LocalDate start, LocalDate end) {
        String cacheKey = String.format("%s_%s_%s", symbol, start.toString(), end.toString());
        
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        List<Candle> data = provider.getHistoricalData(symbol, start, end);
        if (data != null && !data.isEmpty()) {
            cache.put(cacheKey, data);
        }
        
        return data;
    }
}
