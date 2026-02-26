package com.tradelearn.server.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In-memory store for player position snapshots during active matches.
 *
 * Eliminates the O(n) trade-replay bottleneck by maintaining an incrementally
 * updated position for each player in each game. Positions are copy-on-write:
 * every update creates a new immutable snapshot, so concurrent readers never
 * see a half-updated state.
 *
 * Lifecycle:
 *   initializePosition()  → when a match becomes ACTIVE (after join)
 *   applyTrade()          → after each trade is persisted
 *   updateMarkPrice()     → on each candle tick (updates equity stats)
 *   getPosition()         → fast O(1) reads for scoreboard, validation
 *   evictGame()           → when a match ends or is abandoned
 *
 * Thread-safety:
 *   - ConcurrentHashMap for the store itself
 *   - Copy-on-write for each position (new object on every mutation)
 *   - Writes are already serialized per-game by the pessimistic lock on Game row
 *   - Reads are lock-free and always see a consistent snapshot
 */
@Service
public class PositionSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(PositionSnapshotStore.class);

    /**
     * Maximum number of position snapshots to keep in memory.
     * Default: 20,000 (= 10K concurrent games × 2 players).
     * Beyond this, new initializations are rejected until games end and evict.
     */
    private final int maxSnapshots;

    /** Key: "gameId:userId" → latest PlayerPosition snapshot */
    private final ConcurrentHashMap<String, MatchTradeService.PlayerPosition> snapshots =
            new ConcurrentHashMap<>();

    public PositionSnapshotStore(
            @Value("${tradelearn.snapshot.max-entries:200}") int maxSnapshots) {
        this.maxSnapshots = maxSnapshots;
    }

    private static String key(long gameId, long userId) {
        return gameId + ":" + userId;
    }

    // ==================== LIFECYCLE ====================

    /**
     * Initialize a player's position at match start.
     * Rejects if the store has reached its memory bound (maxSnapshots).
     *
     * @return true if initialized, false if rejected (store full)
     */
    public boolean initializePosition(long gameId, long userId, double startingBalance) {
        String k = key(gameId, userId);
        // Allow re-initialization for existing keys (idempotent)
        if (!snapshots.containsKey(k) && snapshots.size() >= maxSnapshots) {
            log.warn("[Position] Store full ({}/{}) — rejecting init for game={} user={}",
                    snapshots.size(), maxSnapshots, gameId, userId);
            return false;
        }
        MatchTradeService.PlayerPosition pos = new MatchTradeService.PlayerPosition();
        pos.cash = startingBalance;
        pos.peakEquity = startingBalance;
        pos.maxDrawdown = 0.0;
        pos.totalTrades = 0;
        pos.profitableTrades = 0;
        snapshots.put(k, pos);
        log.debug("[Position] Initialized game={} user={} balance={} (store size={})",
                gameId, userId, startingBalance, snapshots.size());
        return true;
    }

    /**
     * Get current position snapshot (O(1), lock-free).
     * Returns null if no snapshot exists (game not started or already evicted).
     */
    public MatchTradeService.PlayerPosition getPosition(long gameId, long userId) {
        return snapshots.get(key(gameId, userId));
    }

    /**
     * Unconditionally store a position snapshot.
     * Used when rebuilding from trade replay (e.g., server restart).
     * Respects memory bounds — rejects if store is full and key is new.
     *
     * @return true if stored, false if rejected (store full)
     */
    public boolean putPosition(long gameId, long userId, MatchTradeService.PlayerPosition pos) {
        String k = key(gameId, userId);
        if (!snapshots.containsKey(k) && snapshots.size() >= maxSnapshots) {
            log.warn("[Position] Store full ({}/{}) — rejecting put for game={} user={}",
                    snapshots.size(), maxSnapshots, gameId, userId);
            return false;
        }
        snapshots.put(k, pos);
        return true;
    }

    /**
     * Check if a position snapshot exists.
     */
    public boolean hasPosition(long gameId, long userId) {
        return snapshots.containsKey(key(gameId, userId));
    }

    // ==================== TRADE APPLICATION ====================

    /**
     * Apply a trade incrementally and return the updated position.
     * Creates a deep copy (copy-on-write) so concurrent readers are safe.
     *
     * @param gameId    the game
     * @param userId    the player who traded
     * @param type      BUY, SELL, SHORT, COVER
     * @param symbol    stock symbol
     * @param quantity  number of shares
     * @param price     execution price (server-authoritative)
     * @return the updated position, or null if no snapshot exists
     */
    public MatchTradeService.PlayerPosition applyTrade(
            long gameId, long userId,
            String type, String symbol,
            int quantity, double price) {

        String k = key(gameId, userId);
        MatchTradeService.PlayerPosition current = snapshots.get(k);
        if (current == null) {
            log.warn("[Position] No snapshot for game={} user={} — cannot apply trade", gameId, userId);
            return null;
        }

        // ── Deep copy (copy-on-write) ──
        MatchTradeService.PlayerPosition pos = deepCopy(current);
        double cost = quantity * price;
        pos.totalTrades++;

        switch (type.toUpperCase()) {
            case "BUY" -> {
                pos.cash -= cost;
                pos.shares.merge(symbol, quantity, Integer::sum);
            }
            case "SELL" -> {
                pos.cash += cost;
                pos.shares.merge(symbol, -quantity, Integer::sum);
                if (pos.shares.getOrDefault(symbol, 0) <= 0) {
                    pos.shares.remove(symbol);
                }
            }
            case "SHORT" -> {
                pos.cash += cost;
                int prevShort = pos.shortShares.getOrDefault(symbol, 0);
                double prevAvg = pos.avgShortPrice.getOrDefault(symbol, 0.0);
                double totalValue = (prevAvg * prevShort) + cost;
                int newShort = prevShort + quantity;
                pos.shortShares.put(symbol, newShort);
                pos.avgShortPrice.put(symbol, totalValue / newShort);
            }
            case "COVER" -> {
                pos.cash -= cost;
                double avgEntry = pos.avgShortPrice.getOrDefault(symbol, price);
                if (price < avgEntry) {
                    pos.profitableTrades++;
                }
                int newShort = pos.shortShares.getOrDefault(symbol, 0) - quantity;
                if (newShort <= 0) {
                    pos.shortShares.remove(symbol);
                    pos.avgShortPrice.remove(symbol);
                } else {
                    pos.shortShares.put(symbol, newShort);
                }
            }
        }

        // ── Update equity tracking ──
        double equity = snapshotEquity(pos, price);

        // SELL profitability heuristic (matches original calculatePosition logic)
        if ("SELL".equalsIgnoreCase(type) && equity > pos.peakEquity) {
            pos.profitableTrades++;
        }

        if (equity > pos.peakEquity) {
            pos.peakEquity = equity;
        }
        if (pos.peakEquity > 0) {
            double drawdown = (pos.peakEquity - equity) / pos.peakEquity;
            if (drawdown > pos.maxDrawdown) {
                pos.maxDrawdown = drawdown;
            }
        }

        // ── Atomic swap ──
        snapshots.put(k, pos);
        return pos;
    }

    // ==================== MARK PRICE UPDATE ====================

    /**
     * Update equity stats based on a new candle price (no trade).
     * Called on each candle tick to keep peakEquity and maxDrawdown current.
     */
    public MatchTradeService.PlayerPosition updateMarkPrice(long gameId, long userId, double markPrice) {
        String k = key(gameId, userId);
        MatchTradeService.PlayerPosition current = snapshots.get(k);
        if (current == null) return null;

        double equity = snapshotEquity(current, markPrice);

        boolean needsUpdate = equity > current.peakEquity;
        if (!needsUpdate && current.peakEquity > 0) {
            double drawdown = (current.peakEquity - equity) / current.peakEquity;
            needsUpdate = drawdown > current.maxDrawdown;
        }

        if (needsUpdate) {
            MatchTradeService.PlayerPosition pos = deepCopy(current);
            if (equity > pos.peakEquity) pos.peakEquity = equity;
            if (pos.peakEquity > 0) {
                double drawdown = (pos.peakEquity - equity) / pos.peakEquity;
                if (drawdown > pos.maxDrawdown) pos.maxDrawdown = drawdown;
            }
            snapshots.put(k, pos);
            return pos;
        }

        return current;
    }

    // ==================== SCOREBOARD ====================

    /**
     * Build a complete scoreboard payload for both players.
     * Updates mark prices and returns a map ready for WebSocket broadcast.
     */
    public Map<String, Object> buildScoreboardPayload(
            long gameId, long player1Id, long player2Id, double markPrice) {

        updateMarkPrice(gameId, player1Id, markPrice);
        updateMarkPrice(gameId, player2Id, markPrice);

        MatchTradeService.PlayerPosition p1 = getPosition(gameId, player1Id);
        MatchTradeService.PlayerPosition p2 = getPosition(gameId, player2Id);

        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", gameId);
        payload.put("markPrice", markPrice);
        if (p1 != null) payload.put("player1", positionToMap(player1Id, p1));
        if (p2 != null) payload.put("player2", positionToMap(player2Id, p2));
        return payload;
    }

    private Map<String, Object> positionToMap(long userId, MatchTradeService.PlayerPosition pos) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("cash", pos.cash);
        m.put("longShares", new HashMap<>(pos.shares));
        m.put("shortShares", new HashMap<>(pos.shortShares));
        m.put("peakEquity", pos.peakEquity);
        m.put("maxDrawdown", pos.maxDrawdown);
        m.put("totalTrades", pos.totalTrades);
        m.put("profitableTrades", pos.profitableTrades);
        return m;
    }

    // ==================== CLEANUP ====================

    /**
     * Evict all snapshots for a game (on game end / abandon).
     */
    public void evictGame(long gameId) {
        String prefix = gameId + ":";
        snapshots.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        log.debug("[Position] Evicted snapshots for game={}", gameId);
    }

    /**
     * Total number of active snapshots (diagnostic).
     */
    public int snapshotCount() {
        return snapshots.size();
    }

    // ==================== UTILITY ====================

    /**
     * Equity = cash + longs×price − shorts×price.
     */
    public static double snapshotEquity(MatchTradeService.PlayerPosition pos, double markPrice) {
        double equity = pos.cash;
        for (int qty : pos.shares.values())      equity += qty * markPrice;
        for (int qty : pos.shortShares.values()) equity -= qty * markPrice;
        return equity;
    }

    /**
     * Deep copy a PlayerPosition (for copy-on-write safety).
     */
    private static MatchTradeService.PlayerPosition deepCopy(MatchTradeService.PlayerPosition src) {
        MatchTradeService.PlayerPosition copy = new MatchTradeService.PlayerPosition();
        copy.cash = src.cash;
        copy.shares = new HashMap<>(src.shares);
        copy.shortShares = new HashMap<>(src.shortShares);
        copy.avgShortPrice = new HashMap<>(src.avgShortPrice);
        copy.peakEquity = src.peakEquity;
        copy.maxDrawdown = src.maxDrawdown;
        copy.totalTrades = src.totalTrades;
        copy.profitableTrades = src.profitableTrades;
        return copy;
    }
}
