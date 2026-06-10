package com.tradelearn.server.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "portfolio_id", nullable = false)
    @JsonBackReference
    private Portfolio portfolio;

    @Column(name = "stock_symbol", nullable = false)
    private String stockSymbol;

    @Column(nullable = false)
    private Integer quantity; // Positive for long, negative for short

    @Column(name = "average_purchase_price", nullable = false)
    private Double averagePurchasePrice;

    // Default constructor (required by JPA/Hibernate)
    public Holding() {}

    // Constructor to make creating new holdings easier
    public Holding(Portfolio portfolio, String stockSymbol, Integer quantity, Double averagePurchasePrice) {
        this.portfolio = portfolio;
        this.stockSymbol = stockSymbol;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Portfolio getPortfolio() {
        return portfolio;
    }
    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }
    public String getStockSymbol() {
        return stockSymbol;
    }
    public void setStockSymbol(String stockSymbol) {
        this.stockSymbol = stockSymbol;
    }
    public Integer getQuantity() {
        return quantity;
    }
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    public Double getAveragePurchasePrice() {
        return averagePurchasePrice;
    }
    public void setAveragePurchasePrice(Double averagePurchasePrice) {
        this.averagePurchasePrice = averagePurchasePrice;
    }
}