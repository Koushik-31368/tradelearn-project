package com.tradelearn.fairness.adapter;

import com.tradelearn.fairness.engine.ActionSettler;
import com.tradelearn.fairness.engine.EpochLockstepEngine;
import com.tradelearn.fairness.engine.EpochSettlementResult;
import com.tradelearn.fairness.engine.EngineQueueResult;
import com.tradelearn.fairness.engine.PendingAction;
import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.game.epoch.EpochTradeQueue;
import com.tradelearn.server.game.model.Trade;
import com.tradelearn.server.game.service.MatchTradeService;
import com.tradelearn.server.infrastructure.redis.store.PositionSnapshotStore;
import com.tradelearn.server.websocket.GameBroadcaster;

import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Spring adapter that bridges {@link EpochLockstepEngine} with TradeLearn's
 * domain layer.
 *
 * <h2>Separation of Concerns</h2>
 * <ul>
 *   <li>{@link EpochLockstepEngine} — pure Java engine, zero application
 *       dependencies. Handles epoch queuing, staleness checks, and atomic
 *       settlement ordering.</li>
 *   <li>{@link TradeLearnEpochAdapter} (this class) — Spring {@code @Service}.
 *       Translates between TradeLearn types ({@link EpochTradeQueue}, game IDs,
 *       {@link MatchTradeService}) and the generic engine API.</li>
 * </ul>
 *
 * <h2>API Compatibility</h2>
 * This class exposes the same method signatures that {@code CandleEpochGate}
 * previously exposed, so all existing consumers ({@code MatchSchedulerService},
 * {@code MatchLifecycleService}, {@code MatchScoringService},
 * {@code GameWebSocketHandler}) require only an import change.
 */
@Service
public class TradeLearnEpochAdapter {


    /**
     * The generic engine, parameterised over {@link EpochTradeQueue} as the
     * action payload. A single engine instance handles all concurrent sessions.
     */
    private final EpochLockstepEngine<EpochTradeQueue> engine;

    private final MatchTradeService matchTradeService;
    private final PositionSnapshotStore positionStore;
    private final GameBroadcaster broadcaster;

    public TradeLearnEpochAdapter(MatchTradeService matchTradeService,
                                  PositionSnapshotStore positionStore,
                                  GameBroadcaster broadcaster) {
        this.engine            = new EpochLockstepEngine<>();
        this.matchTradeService = matchTradeService;
        this.positionStore     = positionStore;
        this.broadcaster       = broadcaster;
    }

    // ── TradeLearn API (mirrors the old CandleEpochGate) ───────────────────

    /** Register a game session in the engine. Called when a match becomes ACTIVE. */
    public void initGame(long gameId) {
        engine.initSession(sessionKey(gameId));
    }

    /** Evict a game session from the engine. Called when a match ends. */
    public void evictGame(long gameId) {
        engine.evictSession(sessionKey(gameId));
    }

    /**
     * Queue an epoch-tagged trade for end-of-epoch settlement.
     *
     * @param trade the trade tagged with the epoch at WebSocket-receipt time
     * @return      the queue result (ACCEPTED, STALE_EPOCH, QUEUE_FULL, SESSION_NOT_FOUND)
     */
    public EngineQueueResult queueTrade(EpochTradeQueue trade) {
        PendingAction<EpochTradeQueue> action = new PendingAction<>(
                sessionKey(trade.gameId()),
                String.valueOf(trade.userId()),
                trade.epoch(),
                trade.receivedNanos(),
                trade   // the original EpochTradeQueue is the payload
        );
        return engine.queue(action);
    }

    /**
     * Settle all trades queued for the given epoch, at the current candle price.
     *
     * <p><b>Must be called BEFORE advancing the candle index.</b> This ensures
     * the price seen by {@link MatchTradeService#placeTrade} is the epoch-N close,
     * not the epoch-(N+1) open.
     *
     * @param gameId        the game
     * @param epochToSettle the candle index whose trades are to be settled
     * @param opponentId    the opponent's userId for scoreboard broadcast (-1 if unknown)
     * @return              settlement summary (total / settled / rejected counts)
     */
    public EpochSettlementResult settleEpoch(long gameId, int epochToSettle, long opponentId) {
        ActionSettler<EpochTradeQueue> settler = buildTradeSettler(gameId, opponentId);
        return engine.settleEpoch(sessionKey(gameId), epochToSettle, settler);
    }

    /**
     * Returns the current epoch for a game (next epoch to settle), or -1 if
     * the game is not registered.
     */
    public int getCurrentEpoch(long gameId) {
        return engine.getCurrentEpoch(sessionKey(gameId));
    }

    /** Total pending trades across all epochs for a game. */
    public int pendingTradeCount(long gameId) {
        return engine.pendingActionCount(sessionKey(gameId));
    }

    /** Number of active game sessions in the engine. */
    public int activeGameCount() {
        return engine.activeSessionCount();
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    /**
     * Maps a TradeLearn game ID to a generic engine session key.
     * Simple string conversion keeps the engine free of numeric IDs.
     */
    private static String sessionKey(long gameId) {
        return "game:" + gameId;
    }

    /**
     * Builds the TradeLearn-specific {@link ActionSettler} for trade settlement.
     *
     * <p>Calls {@link MatchTradeService#placeTrade} — which reads the current
     * candle price server-authoritatively — then broadcasts the result and
     * updates the live scoreboard. All side effects are contained here;
     * the engine itself is unaware of them.
     */
    private ActionSettler<EpochTradeQueue> buildTradeSettler(long gameId, long opponentId) {
        return action -> {
            EpochTradeQueue qt = action.payload();

            MatchTradeRequest req = new MatchTradeRequest();
            req.setGameId(gameId);
            req.setUserId(qt.userId());
            req.setSymbol(qt.symbol());
            req.setType(qt.type());
            req.setQuantity(qt.quantity());
            // Price is intentionally omitted — CandleService resolves it inside
            // placeTrade() using the current candle index, which is still epoch N
            // because settleEpoch() is called BEFORE advanceCandle(). ✓

            Trade saved = matchTradeService.placeTrade(req);

            broadcaster.sendToGame(gameId, "trade", saved);

            if (opponentId > 0) {
                Map<String, Object> scoreboard = positionStore.buildScoreboardPayload(
                        gameId, qt.userId(), opponentId, saved.getPrice());
                broadcaster.sendToGame(gameId, "scoreboard", scoreboard);
            }
        };
    }
}
