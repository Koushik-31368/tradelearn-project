package com.tradelearn.server.dto;

public record AchievementDTO(
    String name,
    String description,
    String icon,
    String earnedAt
) {}
