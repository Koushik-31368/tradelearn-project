// src/main/java/com/tradelearn/server/dto/BacktestResult.java
package com.tradelearn.server.dto;

import java.util.List;

public class BacktestResult {
    private String symbol;
    private double initialCapital;
    private double finalCapital;
    private double returnPct;
    private double maxDrawdownPct;
    private double winRatePct;
    private int tradesCount;

    private List<TradeDto> trades;
    private List<EquityPointDto> equityCurve;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }

    public double getFinalCapital() { return finalCapital; }
    public void setFinalCapital(double finalCapital) { this.finalCapital = finalCapital; }

    public double getReturnPct() { return returnPct; }
    public void setReturnPct(double returnPct) { this.returnPct = returnPct; }

    public double getMaxDrawdownPct() { return maxDrawdownPct; }
    public void setMaxDrawdownPct(double maxDrawdownPct) { this.maxDrawdownPct = maxDrawdownPct; }

    public double getWinRatePct() { return winRatePct; }
    public void setWinRatePct(double winRatePct) { this.winRatePct = winRatePct; }

    public int getTradesCount() { return tradesCount; }
    public void setTradesCount(int tradesCount) { this.tradesCount = tradesCount; }

    public List<TradeDto> getTrades() { return trades; }
    public void setTrades(List<TradeDto> trades) { this.trades = trades; }

    public List<EquityPointDto> getEquityCurve() { return equityCurve; }
    public void setEquityCurve(List<EquityPointDto> equityCurve) { this.equityCurve = equityCurve; }
}