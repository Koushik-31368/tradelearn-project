package com.tradelearn.server.dto;

public class EndMatchRequest {

    private long gameId;

    // currentStockPrice is intentionally excluded — resolved server-side by CandleService.

    public long getGameId() { return gameId; }
    public void setGameId(long gameId) { this.gameId = gameId; }
}
