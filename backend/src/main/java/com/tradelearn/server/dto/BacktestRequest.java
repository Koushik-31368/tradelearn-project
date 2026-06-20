package com.tradelearn.server.dto;

import java.util.List;

/**
 * Request body for the SMA-crossover backtest endpoint.
 *
 * <p>Candles are supplied by the client and must be {@link Candle} objects
 * with ISO-8601 {@code date} strings (e.g. {@code "2024-01-15"}).
 * Use {@link Candle#getLocalDate()} when date arithmetic is needed.
 */
public class BacktestRequest {
    private String symbol;
    private double initialCapital;
    private int smaFast;
    private int smaSlow;
    private List<Candle> candles;
    private OosConfig oos;

    public static class OosConfig {
        private boolean enabled;
        private String splitDate;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSplitDate() {
            return splitDate;
        }

        public void setSplitDate(String splitDate) {
            this.splitDate = splitDate;
        }
    }

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

    public List<Candle> getCandles() {
        return candles;
    }

    public void setCandles(List<Candle> candles) {
        this.candles = candles;
    }

    public OosConfig getOos() {
        return oos;
    }

    public void setOos(OosConfig oos) {
        this.oos = oos;
    }
}