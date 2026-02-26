// src/hooks/useGameSocket.js
// ─────────────────────────────────────────────────────────────
// Centralised STOMP-over-SockJS hook for TradeLearn multiplayer
// ─────────────────────────────────────────────────────────────
import { useEffect, useRef, useState, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { wsBase, getToken } from '../utils/api';

/* ──────── public enums ──────── */
export const SocketState = Object.freeze({
    DISCONNECTED: 'DISCONNECTED',
    CONNECTING:   'CONNECTING',
    CONNECTED:    'CONNECTED',
    ERROR:        'ERROR',
});

export const GamePhase = Object.freeze({
    CREATING:  'CREATING',   // game being created (REST in progress)
    WAITING:   'WAITING',    // waiting for opponent to join
    STARTING:  'STARTING',   // opponent joined, candles loading
    ACTIVE:    'ACTIVE',     // game in progress, candles streaming
    FINISHED:  'FINISHED',   // game ended normally
    ABANDONED: 'ABANDONED',  // opponent disconnected
});

/* ──────── default options ──────── */
const DEFAULTS = {
    reconnectDelay:   5000,
    heartbeatIn:      10000,
    heartbeatOut:     10000,
    maxCandleHistory: 500,
    maxTradeLog:      50,
    debug:            false,
};

/**
 * Custom hook that manages the full WebSocket lifecycle for a match.
 *
 * @param {Object}  opts
 * @param {string}  opts.gameId    – match ID (from URL param)
 * @param {number}  opts.userId    – logged-in user ID
 * @param {boolean} opts.enabled   – master on/off (set false to skip connect)
 * @param {Object}  [opts.options] – override defaults above
 *
 * @returns {Object} state & helpers (see bottom of hook)
 */
export default function useGameSocket({ gameId, userId, enabled = true, options = {} }) {
    const opts = { ...DEFAULTS, ...options };

    /* ── refs (survive re-renders, no extra renders) ── */
    const clientRef        = useRef(null);
    const subsRef          = useRef([]);   // active STOMP subscriptions
    const mountedRef       = useRef(true);
    const connectedOnceRef = useRef(false);

    /* ── connection state ── */
    const [socketState, setSocketState] = useState(SocketState.DISCONNECTED);

    /* ── game phase (derived from server events) ── */
    const [gamePhase, setGamePhase] = useState(null);

    /* ── candle stream ── */
    const [currentCandle, setCurrentCandle]   = useState(null);
    const [candleHistory, setCandleHistory]    = useState([]);
    const [candleIndex, setCandleIndex]        = useState(0);
    const [remaining, setRemaining]            = useState(0);

    /* ── trade feed ── */
    const [tradeLog, setTradeLog] = useState([]);

    /* ── auxiliary ── */
    const [lastError, setLastError]           = useState(null);
    const [disconnectInfo, setDisconnectInfo] = useState(null);
    const [statusMessage, setStatusMessage]   = useState('');

    /* ── scoreboard (WebSocket-pushed positions for both players) ── */
    const [scoreboard, setScoreboard] = useState(null);

    /* ── reconnection state (opponent temporarily disconnected) ── */
    const [reconnecting, setReconnecting] = useState(null);

    // ─────────────────────────────────────────────────────────
    // SAFE state setter — only updates if component is mounted
    // ─────────────────────────────────────────────────────────
    const safeSet = useCallback((setter) => {
        if (mountedRef.current) setter();
    }, []);

    // ─────────────────────────────────────────────────────────
    // UNSUBSCRIBE all — called before re-subscribe & on unmount
    // ─────────────────────────────────────────────────────────
    const unsubscribeAll = useCallback(() => {
        subsRef.current.forEach((sub) => {
            try { sub.unsubscribe(); } catch (_) { /* already closed */ }
        });
        subsRef.current = [];
    }, []);

    // ─────────────────────────────────────────────────────────
    // SUBSCRIBE helper — guards against duplicate destinations
    // ─────────────────────────────────────────────────────────
    const subscribeTo = useCallback((client, destination, handler) => {
        // Prevent subscribing twice to the same destination
        const alreadySubbed = subsRef.current.some(
            (s) => s.id && s.destination === destination
        );
        if (alreadySubbed) return;

        const sub = client.subscribe(destination, handler);
        // Attach destination for dedup bookkeeping
        sub.destination = destination;
        subsRef.current.push(sub);
        return sub;
    }, []);

    // ─────────────────────────────────────────────────────────
    // PUBLISH (emit) a STOMP message
    // ─────────────────────────────────────────────────────────
    const publish = useCallback((destination, body) => {
        const client = clientRef.current;
        if (!client?.connected) {
            console.warn('[useGameSocket] publish failed — not connected');
            return false;
        }
        client.publish({
            destination,
            body: typeof body === 'string' ? body : JSON.stringify(body),
        });
        return true;
    }, []);

    // ─────────────────────────────────────────────────────────
    // High-level EMIT helpers (match your requirement names)
    // ─────────────────────────────────────────────────────────

    /** Emit a trade to the game channel */
    const emitTrade = useCallback((tradePayload) => {
        if (!gameId) return false;
        return publish(`/app/game/${gameId}/trade`, tradePayload);
    }, [gameId, publish]);

    // ─────────────────────────────────────────────────────────
    // CONNECT + SUBSCRIBE — the main effect
    // ─────────────────────────────────────────────────────────
    useEffect(() => {
        // Gate: don't connect unless we have the minimum info
        if (!enabled || !gameId || !userId) return;

        mountedRef.current = true;
        safeSet(() => setSocketState(SocketState.CONNECTING));
        safeSet(() => setStatusMessage('Connecting…'));

        const client = new Client({
            webSocketFactory: () => {
                const token = getToken();
                const wsUrl = token
                    ? `${wsBase()}/ws?token=${encodeURIComponent(token)}`
                    : `${wsBase()}/ws`;
                return new SockJS(wsUrl);
            },
            reconnectDelay: opts.reconnectDelay,
            heartbeatIncoming: opts.heartbeatIn,
            heartbeatOutgoing: opts.heartbeatOut,
            debug: opts.debug ? (str) => console.log('[STOMP]', str) : () => {},

            // ── ON CONNECT ──────────────────────────────────
            onConnect: () => {
                if (!mountedRef.current) return;
                connectedOnceRef.current = true;

                safeSet(() => setSocketState(SocketState.CONNECTED));
                safeSet(() => setStatusMessage('Connected'));
                safeSet(() => setLastError(null));

                // Clear old subscriptions (reconnect scenario)
                unsubscribeAll();

                // ── 1. GAME STARTED (opponent joined → creator notified) ──
                subscribeTo(client, `/topic/game/${gameId}/started`, () => {
                    safeSet(() => {
                        setGamePhase(GamePhase.STARTING);
                        setStatusMessage('Opponent joined! Game starting…');
                    });
                });

                // ── 2. CANDLE PROGRESSION (server pushes every ~5 s) ──
                subscribeTo(client, `/topic/game/${gameId}/candle`, (msg) => {
                    const payload = JSON.parse(msg.body);
                    const { candle, index, remaining: rem } = payload;

                    safeSet(() => {
                        setCurrentCandle(candle);
                        setCandleIndex(index);
                        setRemaining(rem);
                        setGamePhase(GamePhase.ACTIVE);

                        setCandleHistory((prev) => {
                            // Deduplicate: never push twice for same index
                            if (prev.length > index) return prev;
                            const next = [...prev, candle];
                            // Cap history to avoid unbounded memory
                            return next.length > opts.maxCandleHistory
                                ? next.slice(next.length - opts.maxCandleHistory)
                                : next;
                        });
                    });
                });

                // ── 3. PLAYER DISCONNECTED ──
                subscribeTo(client, `/topic/game/${gameId}/player-disconnected`, (msg) => {
                    const data = JSON.parse(msg.body);
                    safeSet(() => {
                        setGamePhase(GamePhase.ABANDONED);
                        setDisconnectInfo(data);
                        setStatusMessage(
                            `${data.disconnectedUsername || 'Opponent'} disconnected. Game abandoned.`
                        );
                    });
                });

                // ── 4. GAME FINISHED ──
                subscribeTo(client, `/topic/game/${gameId}/finished`, () => {
                    safeSet(() => {
                        setGamePhase(GamePhase.FINISHED);
                        setStatusMessage('Game finished!');
                    });
                });

                // ── 5. TRADE FEED ──
                subscribeTo(client, `/topic/game/${gameId}/trade`, (msg) => {
                    const trade = JSON.parse(msg.body);
                    safeSet(() =>
                        setTradeLog((prev) => [trade, ...prev].slice(0, opts.maxTradeLog))
                    );
                });

                // ── 6. PER-PLAYER ERROR CHANNEL ──
                subscribeTo(client, `/topic/game/${gameId}/error/${userId}`, (msg) => {
                    const data = JSON.parse(msg.body);
                    safeSet(() => {
                        setLastError(data.error || 'Trade error');
                        setStatusMessage(`⚠ ${data.error || 'Trade error'}`);
                    });
                });

                // ── 7. SCOREBOARD (real-time position updates for both players) ──
                subscribeTo(client, `/topic/game/${gameId}/scoreboard`, (msg) => {
                    const data = JSON.parse(msg.body);
                    safeSet(() => setScoreboard(data));
                });

                // ── 8. PLAYER RECONNECTING (grace period started) ──
                subscribeTo(client, `/topic/game/${gameId}/player-reconnecting`, (msg) => {
                    const data = JSON.parse(msg.body);
                    safeSet(() => {
                        setReconnecting(data);
                        setStatusMessage(
                            `${data.disconnectedUsername || 'Opponent'} disconnected. Waiting for reconnection...`
                        );
                    });
                });

                // ── 9. PLAYER RECONNECTED (grace period ended, player is back) ──
                subscribeTo(client, `/topic/game/${gameId}/player-reconnected`, (msg) => {
                    safeSet(() => {
                        setReconnecting(null);
                        setStatusMessage('Opponent reconnected!');
                    });
                });

                // ── 10. REJOIN: tell server we're (re)joining this game ──
                client.publish({
                    destination: `/app/game/${gameId}/rejoin`,
                    body: JSON.stringify({ userId }),
                });
            },

            // ── ON STOMP ERROR ──────────────────────────────
            onStompError: (frame) => {
                console.error('[useGameSocket] STOMP error:', frame);
                safeSet(() => {
                    setSocketState(SocketState.ERROR);
                    setLastError(frame.headers?.message || 'STOMP error');
                    setStatusMessage('Connection error');
                });
            },

            // ── ON WS ERROR ─────────────────────────────────
            onWebSocketError: (evt) => {
                console.error('[useGameSocket] WebSocket error:', evt);
                safeSet(() => {
                    setSocketState(SocketState.ERROR);
                    setStatusMessage('WebSocket failed');
                });
            },

            // ── ON DISCONNECT ────────────────────────────────
            onDisconnect: () => {
                safeSet(() => {
                    setSocketState(SocketState.DISCONNECTED);
                    setStatusMessage(connectedOnceRef.current ? 'Reconnecting…' : 'Disconnected');
                });
            },
        });

        client.activate();
        clientRef.current = client;

        // ── CLEANUP ──────────────────────────────────────────
        return () => {
            mountedRef.current = false;
            unsubscribeAll();
            if (clientRef.current) {
                clientRef.current.deactivate();
                clientRef.current = null;
            }
        };
        // Intentionally stable deps — gameId + userId don't change mid-game
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled, gameId, userId]);

    // ─────────────────────────────────────────────────────────
    // Manual phase setter (for REST-derived status, e.g. on
    // initial fetch — "game.status === WAITING")
    // ─────────────────────────────────────────────────────────
    const syncPhaseFromRest = useCallback((serverStatus) => {
        const map = {
            WAITING:   GamePhase.WAITING,
            ACTIVE:    GamePhase.ACTIVE,
            FINISHED:  GamePhase.FINISHED,
            ABANDONED: GamePhase.ABANDONED,
        };
        setGamePhase(map[serverStatus] || null);
    }, []);

    /** Seed candle state from the initial REST fetch */
    const seedCandle = useCallback((candle, index, rem) => {
        if (candle) {
            setCurrentCandle(candle);
            setCandleHistory([candle]);
        }
        if (typeof index === 'number') setCandleIndex(index);
        if (typeof rem   === 'number') setRemaining(rem);
    }, []);

    /** Reset all state (e.g. when navigating away) */
    const reset = useCallback(() => {
        setSocketState(SocketState.DISCONNECTED);
        setGamePhase(null);
        setCurrentCandle(null);
        setCandleHistory([]);
        setCandleIndex(0);
        setRemaining(0);
        setTradeLog([]);
        setLastError(null);
        setDisconnectInfo(null);
        setStatusMessage('');
        setScoreboard(null);
        setReconnecting(null);
    }, []);

    // ─────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────
    return {
        /* connection */
        socketState,
        isConnected: socketState === SocketState.CONNECTED,
        isConnecting: socketState === SocketState.CONNECTING,

        /* game lifecycle */
        gamePhase,
        syncPhaseFromRest,

        /* candle stream */
        currentCandle,
        candleHistory,
        candleIndex,
        remaining,
        seedCandle,

        /* trades */
        tradeLog,
        emitTrade,

        /* messages & errors */
        statusMessage,
        lastError,
        disconnectInfo,

        /* scoreboard (real-time position data) */
        scoreboard,

        /* reconnection */
        reconnecting,

        /* utilities */
        publish,
        reset,
    };
}
