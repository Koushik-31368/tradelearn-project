package com.tradelearn.server.dto;

public record FriendDTO(
    Long requestId,
    Long userId,
    String username,
    int rating,
    String status,
    boolean isSender
) {}
