package com.tradelearn.server.service; // Or your chosen sub-package if you made one

public class StockDataPoint {
    public String time;
    public double open;
    public double high;
    public double low;
    public double close;

    // Default constructor (needed by some libraries like Jackson)
    public StockDataPoint() {}

    public StockDataPoint(String time, double open, double high, double low, double close) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
    }

    // Getters are needed for Spring/Jackson to send the data as JSON
    public String getTime() { return time; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }

    // Setters are less critical here but good practice
    public void setTime(String time) { this.time = time; }
    public void setOpen(double open) { this.open = open; }
    public void setHigh(double high) { this.high = high; }
    public void setLow(double low) { this.low = low; }
    public void setClose(double close) { this.close = close; }
}