package com.tradelearn.server.dto;

public record FriendDTO(
    Long requestId,
    Long userId,
    String username,
    int rating,
    String status,
    boolean sender   // NOTE: named 'sender' not 'isSender' — Jackson strips the 'is' prefix
                     // from boolean record accessors (isSender() -> "sender" in JSON).
                     // Frontend reads f.sender to determine direction.
) {}
