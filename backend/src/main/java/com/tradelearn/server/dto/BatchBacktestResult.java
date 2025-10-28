// BatchBacktestResult.java
package com.tradelearn.server.dto;

import java.util.List;

public class BatchBacktestResult {
    public static class Item {
        private String symbol;
        private double returnPct;
        private double maxDrawdownPct;
        private double winRatePct;
        private int trades;
        private double finalCapital;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public double getReturnPct() { return returnPct; }
        public void setReturnPct(double returnPct) { this.returnPct = returnPct; }
        public double getMaxDrawdownPct() { return maxDrawdownPct; }
        public void setMaxDrawdownPct(double maxDrawdownPct) { this.maxDrawdownPct = maxDrawdownPct; }
        public double getWinRatePct() { return winRatePct; }
        public void setWinRatePct(double winRatePct) { this.winRatePct = winRatePct; }
        public int getTrades() { return trades; }
        public void setTrades(int trades) { this.trades = trades; }
        public double getFinalCapital() { return finalCapital; }
        public void setFinalCapital(double finalCapital) { this.finalCapital = finalCapital; }
    }

    private List<Item> results;

    public List<Item> getResults() { return results; }
    public void setResults(List<Item> results) { this.results = results; }
}
