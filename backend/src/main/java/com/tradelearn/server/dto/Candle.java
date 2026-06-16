package com.tradelearn.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {
    private String date; // ISO Date String or formatted date
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
