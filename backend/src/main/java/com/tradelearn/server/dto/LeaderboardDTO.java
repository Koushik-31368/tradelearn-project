package com.tradelearn.server.dto;

import com.tradelearn.server.model.User;

public class LeaderboardDTO {
    private String username;
    private int rating;
    private int wins;
    private int losses;
    private String rank;

    public LeaderboardDTO(User user, String rank) {
        this.username = user.getUsername();
        this.rating = user.getRating();
        this.wins = user.getWins();
        this.losses = user.getLosses();
        this.rank = rank;
    }

    public String getUsername() { return username; }
    public int getRating() { return rating; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public String getRank() { return rank; }
}
