package com.tradelearn.server.dto;

public class JoinGameRequest {

    private long opponentId;   // 🔥 primitive

    public long getOpponentId() {
        return opponentId;
    }

    public void setOpponentId(long opponentId) {
        this.opponentId = opponentId;
    }
}