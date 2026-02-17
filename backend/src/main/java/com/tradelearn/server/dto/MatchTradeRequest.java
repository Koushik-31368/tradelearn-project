package com.tradelearn.server.dto;

public class MatchTradeRequest {

    private long gameId;
    private long userId;
    private String symbol;
    private String type; // BUY, SELL, SHORT, COVER
    private int quantity;

    // Price is intentionally excluded — resolved server-side by CandleService.

    public long getGameId() { return gameId; }
    public void setGameId(long gameId) { this.gameId = gameId; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
