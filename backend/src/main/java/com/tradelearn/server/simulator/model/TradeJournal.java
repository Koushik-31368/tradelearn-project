package com.tradelearn.server.simulator.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_journals")
public class TradeJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "thesis", columnDefinition = "TEXT")
    private String thesis;

    @Column(name = "entry_price")
    private Double entryPrice;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "target_price")
    private Double targetPrice;

    @Column(name = "thesis_category", length = 50)
    private String thesisCategory;

    @Column(name = "pnl")
    private Double pnl;

    @Column(name = "outcome_status", length = 20)
    private String outcomeStatus; // WIN, LOSS, BREAKEVEN

    @Column(name = "risk_amount")
    private Double riskAmount;

    @Column(name = "reflection_status", length = 20)
    private String reflectionStatus = "PENDING"; // PENDING, COMPLETED

    @Column(name = "mistakes_made", columnDefinition = "TEXT")
    private String mistakesMade;

    @Column(name = "lessons_learned", columnDefinition = "TEXT")
    private String lessonsLearned;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public TradeJournal() {}

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getThesis() { return thesis; }
    public void setThesis(String thesis) { this.thesis = thesis; }

    public Double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(Double entryPrice) { this.entryPrice = entryPrice; }

    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }

    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }

    public Double getRiskAmount() { return riskAmount; }
    public void setRiskAmount(Double riskAmount) { this.riskAmount = riskAmount; }

    public String getReflectionStatus() { return reflectionStatus; }
    public void setReflectionStatus(String reflectionStatus) { this.reflectionStatus = reflectionStatus; }

    public String getMistakesMade() { return mistakesMade; }
    public void setMistakesMade(String mistakesMade) { this.mistakesMade = mistakesMade; }

    public String getLessonsLearned() { return lessonsLearned; }
    public void setLessonsLearned(String lessonsLearned) { this.lessonsLearned = lessonsLearned; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getThesisCategory() { return thesisCategory; }
    public void setThesisCategory(String thesisCategory) { this.thesisCategory = thesisCategory; }

    public Double getPnl() { return pnl; }
    public void setPnl(Double pnl) { this.pnl = pnl; }

    public String getOutcomeStatus() { return outcomeStatus; }
    public void setOutcomeStatus(String outcomeStatus) { this.outcomeStatus = outcomeStatus; }
}
