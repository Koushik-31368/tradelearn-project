// src/main/java/com/tradelearn/server/dto/TradeDto.java
package com.tradelearn.server.dto;

import java.time.LocalDate;

public class TradeDto {
    private LocalDate date;
    private String type; // BUY or SELL
    private double price;
    private int quantity;

    public TradeDto() {}
    public TradeDto(LocalDate date, String type, double price, int quantity) {
        this.date = date; this.type = type; this.price = price; this.quantity = quantity;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}