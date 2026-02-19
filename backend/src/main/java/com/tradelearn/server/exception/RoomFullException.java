package com.tradelearn.server.exception;

/**
 * Thrown when attempting to join a room that is already full (max 2 players).
 */
public class RoomFullException extends RuntimeException {
    private final long gameId;
    private final int currentPlayers;
    private final int maxPlayers;

    public RoomFullException(long gameId, int currentPlayers, int maxPlayers) {
        super(String.format("Room %d is full (%d/%d players)", gameId, currentPlayers, maxPlayers));
        this.gameId = gameId;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
    }

    public long getGameId() {
        return gameId;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
}
