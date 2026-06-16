package com.tradelearn.server.dto;

public class ChallengeDTO {
    private Long id;
    private String name;
    private String description;
    private String challengeType;
    private int targetValue;
    private int xpReward;
    private int bonusPoints;
    private String badgeReward;
    private int progress;
    private boolean completed;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getChallengeType() { return challengeType; }
    public void setChallengeType(String challengeType) { this.challengeType = challengeType; }
    public int getTargetValue() { return targetValue; }
    public void setTargetValue(int targetValue) { this.targetValue = targetValue; }
    public int getXpReward() { return xpReward; }
    public void setXpReward(int xpReward) { this.xpReward = xpReward; }
    public int getBonusPoints() { return bonusPoints; }
    public void setBonusPoints(int bonusPoints) { this.bonusPoints = bonusPoints; }
    public String getBadgeReward() { return badgeReward; }
    public void setBadgeReward(String badgeReward) { this.badgeReward = badgeReward; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
