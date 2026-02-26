                    @Transient
                    private java.util.Set<String> rematchRequests = new java.util.HashSet<>();

                    public void requestRematch(String userId) {
                        rematchRequests.add(userId);
                    }

                    public boolean bothRequestedRematch() {
                        return rematchRequests.contains(player1) && rematchRequests.contains(player2);
                    }
                private String winnerId;
                private boolean finished = false;

                public String getWinnerId() { return winnerId; }
                public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
                public boolean isFinished() { return finished; }
                public void setFinished(boolean finished) { this.finished = finished; }
            public String decideWinner(double finalPrice) {
                PlayerPosition p1 = getPosition(player1);
                PlayerPosition p2 = getPosition(player2);
                double pnl1 = p1.getTotalPnL(finalPrice);
                double pnl2 = p2.getTotalPnL(finalPrice);
                return pnl1 > pnl2 ? player1 : player2;
            }
        private String player1;
        private String player2;

        public String getPlayer1() { return player1; }
        public void setPlayer1(String player1) { this.player1 = player1; }
        public String getPlayer2() { return player2; }
        public void setPlayer2(String player2) { this.player2 = player2; }
    public enum GameMode {
        CLASSIC,
        RANKED
    }

    private GameMode mode = GameMode.RANKED;

    public GameMode getMode() { return mode; }
    public void setMode(GameMode mode) { this.mode = mode; }
package com.tradelearn.server.model;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.Transient;

public class Match {
        @Transient
        private Map<String, PlayerPosition> positions = new HashMap<>();

        public PlayerPosition getPosition(String userId) {
            return positions.computeIfAbsent(userId, k -> new PlayerPosition());
        }

        public Map<String, PlayerPosition> getPositions() {
            return positions;
        }
    private String matchId;
    private List<Candle> candles;
    private int currentIndex = 0;
    private long startTime;
    private long endTime;

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public List<Candle> getCandles() { return candles; }
    public void setCandles(List<Candle> candles) { this.candles = candles; }
    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int currentIndex) { this.currentIndex = currentIndex; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
}
