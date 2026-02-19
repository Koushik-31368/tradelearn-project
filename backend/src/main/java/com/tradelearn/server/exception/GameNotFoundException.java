package com.tradelearn.server.exception;

/**
 * Thrown when a requested game is not found in the database.
 */
public class GameNotFoundException extends RuntimeException {
    private final long gameId;

    public GameNotFoundException(long gameId) {
        super("Game not found: " + gameId);
        this.gameId = gameId;
    }

    public GameNotFoundException(long gameId, String message) {
        super(message);
        this.gameId = gameId;
    }

    public long getGameId() {
        return gameId;
    }
}
