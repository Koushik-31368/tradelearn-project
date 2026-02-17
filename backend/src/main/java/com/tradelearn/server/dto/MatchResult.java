package com.tradelearn.server.dto;

public class MatchResult {

    private Long gameId;
    private String status;
    private String stockSymbol;

    private Long creatorId;
    private String creatorUsername;
    private double creatorFinalBalance;
    private double creatorProfit;

    private Long opponentId;
    private String opponentUsername;
    private double opponentFinalBalance;
    private double opponentProfit;

    private Long winnerId;
    private String winnerUsername;

    // ---- Risk / performance stats ----
    private double creatorPeakEquity;
    private double creatorMaxDrawdown;
    private int creatorTotalTrades;
    private int creatorProfitableTrades;

    private double opponentPeakEquity;
    private double opponentMaxDrawdown;
    private int opponentTotalTrades;
    private int opponentProfitableTrades;

    // ---- Hybrid scores ----
    private double creatorFinalScore;
    private double opponentFinalScore;

    // ---- ELO rating ----
    private int creatorRatingDelta;
    private int opponentRatingDelta;
    private int creatorNewRating;
    private int opponentNewRating;

    // Getters and Setters
    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStockSymbol() { return stockSymbol; }
    public void setStockSymbol(String stockSymbol) { this.stockSymbol = stockSymbol; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }

    public double getCreatorFinalBalance() { return creatorFinalBalance; }
    public void setCreatorFinalBalance(double creatorFinalBalance) { this.creatorFinalBalance = creatorFinalBalance; }

    public double getCreatorProfit() { return creatorProfit; }
    public void setCreatorProfit(double creatorProfit) { this.creatorProfit = creatorProfit; }

    public Long getOpponentId() { return opponentId; }
    public void setOpponentId(Long opponentId) { this.opponentId = opponentId; }

    public String getOpponentUsername() { return opponentUsername; }
    public void setOpponentUsername(String opponentUsername) { this.opponentUsername = opponentUsername; }

    public double getOpponentFinalBalance() { return opponentFinalBalance; }
    public void setOpponentFinalBalance(double opponentFinalBalance) { this.opponentFinalBalance = opponentFinalBalance; }

    public double getOpponentProfit() { return opponentProfit; }
    public void setOpponentProfit(double opponentProfit) { this.opponentProfit = opponentProfit; }

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }

    public double getCreatorPeakEquity() { return creatorPeakEquity; }
    public void setCreatorPeakEquity(double v) { this.creatorPeakEquity = v; }

    public double getCreatorMaxDrawdown() { return creatorMaxDrawdown; }
    public void setCreatorMaxDrawdown(double v) { this.creatorMaxDrawdown = v; }

    public int getCreatorTotalTrades() { return creatorTotalTrades; }
    public void setCreatorTotalTrades(int v) { this.creatorTotalTrades = v; }

    public int getCreatorProfitableTrades() { return creatorProfitableTrades; }
    public void setCreatorProfitableTrades(int v) { this.creatorProfitableTrades = v; }

    public double getOpponentPeakEquity() { return opponentPeakEquity; }
    public void setOpponentPeakEquity(double v) { this.opponentPeakEquity = v; }

    public double getOpponentMaxDrawdown() { return opponentMaxDrawdown; }
    public void setOpponentMaxDrawdown(double v) { this.opponentMaxDrawdown = v; }

    public int getOpponentTotalTrades() { return opponentTotalTrades; }
    public void setOpponentTotalTrades(int v) { this.opponentTotalTrades = v; }

    public int getOpponentProfitableTrades() { return opponentProfitableTrades; }
    public void setOpponentProfitableTrades(int v) { this.opponentProfitableTrades = v; }

    public double getCreatorFinalScore() { return creatorFinalScore; }
    public void setCreatorFinalScore(double v) { this.creatorFinalScore = v; }

    public double getOpponentFinalScore() { return opponentFinalScore; }
    public void setOpponentFinalScore(double v) { this.opponentFinalScore = v; }

    public int getCreatorRatingDelta() { return creatorRatingDelta; }
    public void setCreatorRatingDelta(int v) { this.creatorRatingDelta = v; }

    public int getOpponentRatingDelta() { return opponentRatingDelta; }
    public void setOpponentRatingDelta(int v) { this.opponentRatingDelta = v; }

    public int getCreatorNewRating() { return creatorNewRating; }
    public void setCreatorNewRating(int v) { this.creatorNewRating = v; }

    public int getOpponentNewRating() { return opponentNewRating; }
    public void setOpponentNewRating(int v) { this.opponentNewRating = v; }
}
