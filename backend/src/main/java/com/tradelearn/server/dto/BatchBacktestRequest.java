// BatchBacktestRequest.java
package com.tradelearn.server.dto;

import java.util.List;

/**
 * Request body for the batch backtest endpoint (multiple symbols, same candle set).
 *
 * <p>Migrated from {@code CandleDto} to the canonical {@link Candle} DTO.
 */
public class BatchBacktestRequest {
    private List<String> symbols;
    private double initialCapital;
    private Integer smaFast;
    private Integer smaSlow;
    private List<Candle> candles;
    private BacktestRequest.OosConfig oos;

    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }

    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }

    public Integer getSmaFast() { return smaFast; }
    public void setSmaFast(Integer smaFast) { this.smaFast = smaFast; }

    public Integer getSmaSlow() { return smaSlow; }
    public void setSmaSlow(Integer smaSlow) { this.smaSlow = smaSlow; }

    public List<Candle> getCandles() { return candles; }
    public void setCandles(List<Candle> candles) { this.candles = candles; }

    public BacktestRequest.OosConfig getOos() { return oos; }
    public void setOos(BacktestRequest.OosConfig oos) { this.oos = oos; }
}
