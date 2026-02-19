package com.tradelearn.server.exception;

/**
 * Thrown when a game operation is attempted in an invalid state.
 * E.g., trying to place a trade in a FINISHED game.
 */
public class InvalidGameStateException extends RuntimeException {
    private final long gameId;
    private final String currentState;
    private final String expectedState;

    public InvalidGameStateException(long gameId, String currentState, String expectedState) {
        super(String.format("Game %d is in state '%s', expected '%s'", gameId, currentState, expectedState));
        this.gameId = gameId;
        this.currentState = currentState;
        this.expectedState = expectedState;
    }

    public InvalidGameStateException(long gameId, String currentState, String expectedState, String message) {
        super(message);
        this.gameId = gameId;
        this.currentState = currentState;
        this.expectedState = expectedState;
    }

    public long getGameId() {
        return gameId;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getExpectedState() {
        return expectedState;
    }
}
