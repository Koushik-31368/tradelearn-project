package com.tradelearn.server.market;

/**
 * Thrown when the external market-data provider (Alpha Vantage) returns an
 * error, is rate-limited, or cannot be reached.
 *
 * <p>Catching this specifically in {@link com.tradelearn.server.market.controller.MarketController}
 * lets us return a {@code 503 Service Unavailable} with a structured JSON body
 * rather than a generic 500, so the frontend can show a useful message.
 */
public class MarketDataException extends RuntimeException {

    private final String source;

    public MarketDataException(String source, String message) {
        super(message);
        this.source = source;
    }

    public MarketDataException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    /** The data source that threw (e.g. {@code "alpha-vantage"}). */
    public String getSource() {
        return source;
    }
}
