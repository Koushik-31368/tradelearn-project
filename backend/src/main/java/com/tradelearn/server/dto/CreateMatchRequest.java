package com.tradelearn.server.dto;

public class CreateMatchRequest {

    private long creatorId;
    private String stockSymbol;
    private int durationMinutes;
    private Double startingBalance;

    public long getCreatorId() { return creatorId; }
    public void setCreatorId(long creatorId) { this.creatorId = creatorId; }

    public String getStockSymbol() { return stockSymbol; }
    public void setStockSymbol(String stockSymbol) { this.stockSymbol = stockSymbol; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public Double getStartingBalance() { return startingBalance; }
    public void setStartingBalance(Double startingBalance) { this.startingBalance = startingBalance; }
}
