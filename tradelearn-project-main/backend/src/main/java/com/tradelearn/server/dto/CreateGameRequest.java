// src/main/java/com/tradelearn/server/dto/CreateGameRequest.java
package com.tradelearn.server.dto;

public class CreateGameRequest {
    private String stockSymbol;
    private int durationMinutes;
    private Long creatorId;

    // Getters and Setters
    public String getStockSymbol() {
        return stockSymbol;
    }
    public void setStockSymbol(String stockSymbol) {
        this.stockSymbol = stockSymbol;
    }
    public int getDurationMinutes() {
        return durationMinutes;
    }
    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
    public Long getCreatorId() {
        return creatorId;
    }
    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }
}