package com.tradelearn.server.model;

public class PlayerPosition {
    private int quantity = 0;
    private double averagePrice = 0.0;
    private double realizedPnL = 0.0;

    public void buy(int qty, double price) {
        double totalCost = (averagePrice * quantity) + (price * qty);
        quantity += qty;
        averagePrice = quantity == 0 ? 0.0 : totalCost / quantity;
    }

    public void sell(int qty, double price) {
        if (qty > quantity) return;
        double profit = (price - averagePrice) * qty;
        realizedPnL += profit;
        quantity -= qty;
        if (quantity == 0) {
            averagePrice = 0.0;
        }
    }

    public double getTotalPnL(double currentPrice) {
        double unrealized = (currentPrice - averagePrice) * quantity;
        return realizedPnL + unrealized;
    }

    public int getQuantity() { return quantity; }
    public double getAveragePrice() { return averagePrice; }
    public double getRealizedPnL() { return realizedPnL; }
}