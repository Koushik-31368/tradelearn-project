// src/main/java/com/tradelearn/server/dto/EquityPointDto.java
package com.tradelearn.server.dto;

import java.time.LocalDate;

public class EquityPointDto {
    private LocalDate date;
    private double equity;

    public EquityPointDto() {}
    public EquityPointDto(LocalDate date, double equity) {
        this.date = date; this.equity = equity;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double getEquity() { return equity; }
    public void setEquity(double equity) { this.equity = equity; }
}