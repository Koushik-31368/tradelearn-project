package com.tradelearn.server.dto;

import java.time.LocalDate;

public class EquityPointDto {
    private LocalDate date;
    private double equity;

    public EquityPointDto(LocalDate date, double equity) {
        this.date = date;
        this.equity = equity;
    }

    // Getters and Setters
    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public double getEquity() {
        return equity;
    }

    public void setEquity(double equity) {
        this.equity = equity;
    }
}
