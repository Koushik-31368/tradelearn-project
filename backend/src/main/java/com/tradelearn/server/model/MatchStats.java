package com.tradelearn.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stores per-player risk/performance statistics for a finished match.
 * One row per player per game.
 */
@Entity
@Table(name = "match_stats")
public class MatchStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Highest equity (cash + positions value) reached during the match */
    @Column(name = "peak_equity", nullable = false)
    private double peakEquity;

    /** Largest percentage drop from peak equity */
    @Column(name = "max_drawdown", nullable = false)
    private double maxDrawdown;

    /** Total number of trades placed */
    @Column(name = "total_trades", nullable = false)
    private int totalTrades;

    /** Number of trades that were profitable (sells/covers that made money) */
    @Column(name = "profitable_trades", nullable = false)
    private int profitableTrades;

    /** Final equity at match end */
    @Column(name = "final_equity", nullable = false)
    private double finalEquity;

    /** Composite hybrid score (0–100) */
    @Column(name = "final_score", nullable = false)
    private double finalScore;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public double getPeakEquity() { return peakEquity; }
    public void setPeakEquity(double peakEquity) { this.peakEquity = peakEquity; }

    public double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

    public int getProfitableTrades() { return profitableTrades; }
    public void setProfitableTrades(int profitableTrades) { this.profitableTrades = profitableTrades; }

    public double getFinalEquity() { return finalEquity; }
    public void setFinalEquity(double finalEquity) { this.finalEquity = finalEquity; }

    public double getFinalScore() { return finalScore; }
    public void setFinalScore(double finalScore) { this.finalScore = finalScore; }
}
