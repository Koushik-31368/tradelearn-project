// BatchBacktestRequest.java
package com.tradelearn.server.dto;

import java.util.List;

public class BatchBacktestRequest {
    private List<String> symbols;
    private double initialCapital;
    private Integer smaFast;
    private Integer smaSlow;
    private List<CandleDto> candles;
    private BacktestRequest.OosConfig oos;

    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }

    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }

    public Integer getSmaFast() { return smaFast; }
    public void setSmaFast(Integer smaFast) { this.smaFast = smaFast; }

    public Integer getSmaSlow() { return smaSlow; }
    public void setSmaSlow(Integer smaSlow) { this.smaSlow = smaSlow; }

    public List<CandleDto> getCandles() { return candles; }
    public void setCandles(List<CandleDto> candles) { this.candles = candles; }

    public BacktestRequest.OosConfig getOos() { return oos; }
    public void setOos(BacktestRequest.OosConfig oos) { this.oos = oos; }
}
