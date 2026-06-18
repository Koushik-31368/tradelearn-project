package com.tradelearn.server.dto;

import com.tradelearn.server.market.service.CandleService;

public class EndMatchRequest {

    private long gameId;

    // currentStockPrice is intentionally excluded — resolved server-side by CandleService.

    public long getGameId() { return gameId; }
    public void setGameId(long gameId) { this.gameId = gameId; }
}
