package com.tradelearn.server.model;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "games")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "stock_symbol", nullable = false)
    private String stockSymbol;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(nullable = false)
    private String status;
    @ManyToOne
    @JoinColumn(name = "creator_id")
    private User creator;
    @ManyToOne
    @JoinColumn(name = "opponent_id")
    private User opponent;
    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockSymbol() { return stockSymbol; }
    public void setStockSymbol(String stockSymbol) { this.stockSymbol = stockSymbol; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }
    public User getOpponent() { return opponent; }
    public void setOpponent(User opponent) { this.opponent = opponent; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}