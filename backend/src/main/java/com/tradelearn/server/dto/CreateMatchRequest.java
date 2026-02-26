package com.tradelearn.server.dto;

import com.tradelearn.server.validation.ValidStockSymbol;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new 1v1 match.
 *
 * All fields are validated before reaching the service layer.
 * Price fields are intentionally excluded — the server resolves
 * all prices via CandleService to prevent client manipulation.
 */
public class CreateMatchRequest {

    /**
     * Creator ID — set server-side from JWT token.
     * Not required in request body (ignored if sent by client).
     */
    private Long creatorId;

    @NotBlank(message = "Stock symbol is required")
    @ValidStockSymbol
    private String stockSymbol;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 60, message = "Duration cannot exceed 60 minutes")
    private int durationMinutes;

    @Min(value = 10000, message = "Starting balance must be at least ₹10,000")
    @Max(value = 100_000_000, message = "Starting balance cannot exceed ₹10,00,00,000")
    private Double startingBalance;

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public String getStockSymbol() { return stockSymbol; }
    public void setStockSymbol(String stockSymbol) { this.stockSymbol = stockSymbol; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public Double getStartingBalance() { return startingBalance; }
    public void setStartingBalance(Double startingBalance) { this.startingBalance = startingBalance; }
}
