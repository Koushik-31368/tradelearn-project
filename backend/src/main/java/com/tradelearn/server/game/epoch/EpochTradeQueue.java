package com.tradelearn.server.game.epoch;

/**
 * An individual trade request tagged with the candle epoch at which
 * the server received it via WebSocket.
 *
 * <h3>Why epoch tagging matters</h3>
 * In a two-player match both players observe the same candle price
 * stream. A player with lower network latency can receive a new candle
 * broadcast earlier and immediately submit a trade at the old price —
 * an unfair timing advantage that has nothing to do with trading skill.
 *
 * By recording {@link #epoch} at WebSocket-receipt time (before any DB
 * or thread-pool delay), the server knows exactly which candle window
 * the trade belongs to, regardless of when it is eventually persisted.
 *
 * @param gameId          the match
 * @param userId          the authenticated trader (server-validated, never from payload)
 * @param symbol          stock symbol
 * @param type            BUY | SELL | SHORT | COVER
 * @param quantity        number of shares
 * @param epoch           candle index at WebSocket-receipt time
 * @param receivedNanos   System.nanoTime() at WebSocket-receipt (for ordering within epoch)
 */
public record EpochTradeQueue(
        long   gameId,
        long   userId,
        String symbol,
        String type,
        int    quantity,
        int    epoch,
        long   receivedNanos
) {}
