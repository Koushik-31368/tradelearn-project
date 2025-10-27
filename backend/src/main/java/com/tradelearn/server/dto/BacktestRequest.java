package com.tradelearn.server.dto;

import java.util.List;

public class BacktestRequest {
    private String symbol;
    private double initialCapital;
    private int smaFast;
    private int smaSlow;
    private List<CandleDto> candles;

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getInitialCapital() {
        return initialCapital;
    }

    public void setInitialCapital(double initialCapital) {
        this.initialCapital = initialCapital;
    }

    public int getSmaFast() {
        return smaFast;
    }

    public void setSmaFast(int smaFast) {
        this.smaFast = smaFast;
    }

    public int getSmaSlow() {
        return smaSlow;
    }

    public void setSmaSlow(int smaSlow) {
        this.smaSlow = smaSlow;
    }

    public List<CandleDto> getCandles() {
        return candles;
    }

    public void setCandles(List<CandleDto> candles) {
        this.candles = candles;
    }
}
