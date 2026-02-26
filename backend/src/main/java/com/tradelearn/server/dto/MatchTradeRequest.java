package com.tradelearn.server.dto;

import com.tradelearn.server.validation.ValidTradeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for placing a trade within an active match.
 *
 * Price is intentionally excluded — resolved server-side by CandleService
 * to guarantee server-authoritative pricing. This prevents all client-side
 * price manipulation attacks.
 */
public class MatchTradeRequest {

    @NotNull(message = "Game ID is required")
    @Positive(message = "Game ID must be positive")
    private Long gameId;

    /**
     * User ID — set server-side from JWT token.
     * Not required in request body (ignored if sent by client).
     */
    private Long userId;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Trade type is required")
    @ValidTradeType
    private String type;

    @Positive(message = "Quantity must be a positive integer")
    private int quantity;

    // Price is intentionally excluded — resolved server-side by CandleService.

    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    // Backward-compatible overload
    public void setGameId(long gameId) { this.gameId = gameId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    // Backward-compatible overload
    public void setUserId(long userId) { this.userId = userId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
