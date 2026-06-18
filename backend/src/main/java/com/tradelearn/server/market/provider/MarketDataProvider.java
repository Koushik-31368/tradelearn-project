package com.tradelearn.server.market.provider;

import com.tradelearn.server.dto.Candle;
import java.time.LocalDate;
import java.util.List;

public interface MarketDataProvider {
    List<Candle> getHistoricalData(String symbol, LocalDate start, LocalDate end);
}
