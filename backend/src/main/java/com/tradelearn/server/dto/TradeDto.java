package com.tradelearn.server.dto;

import java.time.LocalDate;

public class TradeDto {
    private LocalDate date;
    private String type;
    private double price;
    private int quantity;

    public TradeDto(LocalDate date, String type, double price, int quantity) {
        this.date = date;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
    }

    // Getters and Setters
    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
