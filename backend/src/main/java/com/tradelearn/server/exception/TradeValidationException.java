package com.tradelearn.server.exception;

/**
 * Thrown when trade validation fails (insufficient funds, invalid quantity, etc.).
 */
public class TradeValidationException extends RuntimeException {
    private final long gameId;
    private final long userId;
    private final String tradeType;
    private final int quantity;

    public TradeValidationException(long gameId, long userId, String tradeType, int quantity, String message) {
        super(message);
        this.gameId = gameId;
        this.userId = userId;
        this.tradeType = tradeType;
        this.quantity = quantity;
    }

    public long getGameId() {
        return gameId;
    }

    public long getUserId() {
        return userId;
    }

    public String getTradeType() {
        return tradeType;
    }

    public int getQuantity() {
        return quantity;
    }
}
