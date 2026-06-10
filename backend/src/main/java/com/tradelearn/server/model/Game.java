package com.tradelearn.server.model;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Optimistic lock: Hibernate auto-increments on every UPDATE.
    // If two transactions read the same version and both try to write,
    // the second one gets an OptimisticLockException → safe retry or fail.
    // columnDefinition ensures existing rows get DEFAULT 0 (not NULL).
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version = 0L;

    @Column(name = "stock_symbol", nullable = false)
    private String stockSymbol;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private String status; // WAITING, ACTIVE, FINISHED

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToOne
    @JoinColumn(name = "opponent_id")
    private User opponent;

    @ManyToOne
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "starting_balance", nullable = false)
    private Double startingBalance = 1_000_000.0;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "creator_final_balance")
    private Double creatorFinalBalance;

    @Column(name = "opponent_final_balance")
    private Double opponentFinalBalance;

    @Column(name = "creator_final_score")
    private Double creatorFinalScore;

    @Column(name = "opponent_final_score")
    private Double opponentFinalScore;

    @Column(name = "creator_rating_delta")
    private Integer creatorRatingDelta;

    @Column(name = "opponent_rating_delta")
    private Integer opponentRatingDelta;

    @Column(name = "current_candle_index", nullable = false)
    private int currentCandleIndex = 0;

    @Column(name = "total_candles", nullable = false)
    private int totalCandles = 0;

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

    public User getWinner() { return winner; }
    public void setWinner(User winner) { this.winner = winner; }

    public Double getStartingBalance() { return startingBalance; }
    public void setStartingBalance(Double startingBalance) { this.startingBalance = startingBalance; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Double getCreatorFinalBalance() { return creatorFinalBalance; }
    public void setCreatorFinalBalance(Double creatorFinalBalance) { this.creatorFinalBalance = creatorFinalBalance; }

    public Double getOpponentFinalBalance() { return opponentFinalBalance; }
    public void setOpponentFinalBalance(Double opponentFinalBalance) { this.opponentFinalBalance = opponentFinalBalance; }

    public Double getCreatorFinalScore() { return creatorFinalScore; }
    public void setCreatorFinalScore(Double creatorFinalScore) { this.creatorFinalScore = creatorFinalScore; }

    public Double getOpponentFinalScore() { return opponentFinalScore; }
    public void setOpponentFinalScore(Double opponentFinalScore) { this.opponentFinalScore = opponentFinalScore; }

    public Integer getCreatorRatingDelta() { return creatorRatingDelta; }
    public void setCreatorRatingDelta(Integer creatorRatingDelta) { this.creatorRatingDelta = creatorRatingDelta; }

    public Integer getOpponentRatingDelta() { return opponentRatingDelta; }
    public void setOpponentRatingDelta(Integer opponentRatingDelta) { this.opponentRatingDelta = opponentRatingDelta; }

    public int getCurrentCandleIndex() { return currentCandleIndex; }
    public void setCurrentCandleIndex(int currentCandleIndex) { this.currentCandleIndex = currentCandleIndex; }

    public int getTotalCandles() { return totalCandles; }
    public void setTotalCandles(int totalCandles) { this.totalCandles = totalCandles; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}