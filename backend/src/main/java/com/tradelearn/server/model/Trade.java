package com.tradelearn.server.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 10)
    private String type; // BUY or SELL

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    // Constructors
    public Trade() {
        this.timestamp = LocalDateTime.now();
    }

    public Trade(Long userId, String symbol, String name, String type, Integer quantity, Double price) {
        this.userId = userId;
        this.symbol = symbol;
        this.name = name;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Trade{" +
                "id=" + id +
                ", userId=" + userId +
                ", symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", timestamp=" + timestamp +
                '}';
    }
}

