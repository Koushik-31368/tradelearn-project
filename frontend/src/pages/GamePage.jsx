// src/pages/GamePage.jsx
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import './GamePage.css';
import { useAuth } from '../context/AuthContext';
import { backendUrl, wsBase } from '../utils/api';
import StockChart from '../components/StockChart';
import LiveScoreboard from '../components/LiveScoreboard';

const GamePage = () => {
    const stompClientRef = useRef(null);
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();

    // ── Server-driven candle state ──
    const [game, setGame] = useState(null);
    const [currentCandle, setCurrentCandle] = useState(null);
    const [candleHistory, setCandleHistory] = useState([]);
    const [candleIndex, setCandleIndex] = useState(0);
    const [totalCandles, setTotalCandles] = useState(0);
    const [remaining, setRemaining] = useState(0);

    // ── Trade controls ──
    const [tradeAmount, setTradeAmount] = useState(1);
    const [tradeCooldown, setTradeCooldown] = useState(false);
    const lastTradeRef = useRef(0);

    // ── Candle countdown ──
    const CANDLE_INTERVAL_S = 5;
    const [candleCountdown, setCandleCountdown] = useState(CANDLE_INTERVAL_S);
    const countdownRef = useRef(null);

    // ── UI state ──
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [gameOver, setGameOver] = useState(false);
    const [wsMessage, setWsMessage] = useState('');
    const [tradeLog, setTradeLog] = useState([]);

    // ─────────────────────────────────────────────
    // Fetch match + initial candle from REST
    // ─────────────────────────────────────────────
    const fetchGameData = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const res = await fetch(backendUrl(`/api/match/${gameId}`));
            if (!res.ok) throw new Error(await res.text() || 'Failed to load match');
            const data = await res.json();
            setGame(data);
            setTotalCandles(data.totalCandles || 0);
            setCandleIndex(data.currentCandleIndex || 0);

            if (data.status === 'FINISHED') {
                setGameOver(true);
                return;               // skip candle / position fetch
            }

            if (data.status === 'ACTIVE') {
                // Seed with current candle from server
                const [candleRes, remainRes] = await Promise.all([
                    fetch(backendUrl(`/api/match/${gameId}/candle`)),
                    fetch(backendUrl(`/api/match/${gameId}/candle/remaining`)),
                ]);

                if (candleRes.ok) {
                    const c = await candleRes.json();
                    setCurrentCandle(c);
                    setCandleHistory([c]);
                }
                if (remainRes.ok) {
                    const r = await remainRes.json();
                    setRemaining(r.remaining ?? r);
                }

            }
        } catch (err) {
            console.error('Fetch game error:', err);
            setError(`Load failed: ${err.message}`);
            setTimeout(() => navigate('/multiplayer'), 3000);
        } finally {
            setIsLoading(false);
        }
    }, [gameId, navigate, user]);

    useEffect(() => { fetchGameData(); }, [fetchGameData]);

    // ─────────────────────────────────────────────
    // WebSocket — all price / candle data from server
    // ─────────────────────────────────────────────
    useEffect(() => {
        if (!game || !user || game.status === 'FINISHED') return;

        const client = new Client({
            webSocketFactory: () => new SockJS(`${wsBase()}/ws`),
            reconnectDelay: 5000,
            debug: () => {},                      // silent in prod
            onConnect: () => {
                setWsMessage('Connected');

                // ── Candle progression (server pushes every ~5 s) ──
                client.subscribe(`/topic/game/${gameId}/candle`, (msg) => {
                    const { candle, index, remaining: rem, price } = JSON.parse(msg.body);
                    setCurrentCandle(candle);
                    setCandleIndex(index);
                    setRemaining(rem);
                    setCandleCountdown(CANDLE_INTERVAL_S);   // reset countdown
                    setCandleHistory(prev => {
                        // Deduplicate by index
                        if (prev.length > index) return prev;
                        return [...prev, candle];
                    });
                });

                // ── Trade feed ──
                client.subscribe(`/topic/game/${gameId}/trade`, (msg) => {
                    const trade = JSON.parse(msg.body);
                    setTradeLog(prev => [trade, ...prev].slice(0, 30));
                });

                // ── Game finished ──
                client.subscribe(`/topic/game/${gameId}/finished`, (msg) => {
                    setGameOver(true);
                    setWsMessage('Game finished!');
                    navigate(`/match/${gameId}/result`);
                });

                // ── Per-player error channel ──
                client.subscribe(`/topic/game/${gameId}/error/${user.id}`, (msg) => {
                    const data = JSON.parse(msg.body);
                    setWsMessage(`⚠ ${data.error || 'Trade error'}`);
                });
            },
            onStompError: (frame) => { setWsMessage('WS Error'); console.error('STOMP Error:', frame); },
            onWebSocketError: () => setWsMessage('WS Failed'),
            onDisconnect: () => setWsMessage('Disconnected'),
        });

        client.activate();
        stompClientRef.current = client;
        return () => { if (stompClientRef.current) stompClientRef.current.deactivate(); };
    }, [gameId, game, user]);

    // ─────────────────────────────────────────────
    // Countdown timer — ticks once per second
    // ─────────────────────────────────────────────
    useEffect(() => {
        if (gameOver || !game || game.status !== 'ACTIVE') return;
        countdownRef.current = setInterval(() => {
            setCandleCountdown(prev => (prev > 0 ? prev - 1 : 0));
        }, 1000);
        return () => clearInterval(countdownRef.current);
    }, [gameOver, game]);

    // ─────────────────────────────────────────────
    // Trading — price is NEVER sent from frontend
    // ─────────────────────────────────────────────
    const TRADE_COOLDOWN_MS = 800;

    const handleTrade = (type) => {
        if (gameOver || !tradeAmount || tradeAmount <= 0 || !stompClientRef.current?.connected || !user) return;
        if (game?.status !== 'ACTIVE' || remaining <= 0) return;

        // ── Rapid-click guard ──
        const now = Date.now();
        if (now - lastTradeRef.current < TRADE_COOLDOWN_MS) return;
        lastTradeRef.current = now;
        setTradeCooldown(true);
        setTimeout(() => setTradeCooldown(false), TRADE_COOLDOWN_MS);

        stompClientRef.current.publish({
            destination: `/app/game/${gameId}/trade`,
            body: JSON.stringify({
                type,
                amount: tradeAmount,
                playerId: user.id,
                symbol: game.stockSymbol,
            }),
        });
        setWsMessage(`Sent ${type} order…`);
    };

    // ─────────────────────────────────────────────
    // Helpers — all derived from server positions
    // ─────────────────────────────────────────────
    const serverPrice = currentCandle?.close || 0;

    /** Format candleHistory for lightweight-charts (needs `time` key) */
    const chartData = candleHistory.map((c) => ({
        time: c.date,                      // "2024-01-15" format
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
    }));

    // ─────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────
    if (isLoading) return <div className="game-page-grid"><p>Loading Match…</p></div>;
    if (error)     return <div style={{ color: 'red', padding: 20 }}>{error}</div>;
    if (!user)     return <div>Please log in to play.</div>;
    if (!game)     return <div>Waiting for match data…</div>;

    // ── WAITING screen ──
    if (game.status === 'WAITING') {
        return (
            <div className="game-page-grid">
                <header className="game-header">
                    <h2>Waiting for Opponent</h2>
                    <div className="ws-status">Stock: {game.stockSymbol}</div>
                </header>
                <main className="chart-container">
                    <div className="price-display">
                        <h2>Match #{game.id}</h2>
                        <p>Waiting for another player to join…</p>
                        <p>Starting Balance: ₹{(game.startingBalance || 1000000).toLocaleString()}</p>
                    </div>
                </main>
            </div>
        );
    }

    // ── FINISHED — redirect to dedicated results page ──
    if (gameOver) {
        navigate(`/match/${gameId}/result`);
        return <div className="game-page-grid"><p>Loading results…</p></div>;
    }

    // ── ACTIVE game ──
    const isUserCreator = user.id === game.creator?.id;
    const myName       = isUserCreator ? (game.creator?.username || 'Player 1') : (game.opponent?.username || 'Player 2');
    const oppName      = isUserCreator ? (game.opponent?.username || 'Player 2') : (game.creator?.username || 'Player 1');
    const opponentId   = isUserCreator ? game.opponent?.id : game.creator?.id;
    const progressPct  = totalCandles > 0 ? ((candleIndex + 1) / totalCandles) * 100 : 0;

    return (
        <div className="game-page-grid">
            {/* ── Header with candle index + progress ── */}
            <header className="game-header">
                <h2>
                    <span className="candle-badge">Candle {candleIndex + 1}/{totalCandles}</span>
                    {' '}{currentCandle?.date || ''}
                </h2>
                <div className="ws-status">{wsMessage}</div>
                <div className="candle-remaining">
                    {remaining} remaining
                    {remaining > 0 && (
                        <span className="candle-countdown">Next in {candleCountdown}s</span>
                    )}
                </div>
            </header>

            {/* ── Candle progress bar ── */}
            <div className="candle-progress-wrapper">
                <div className="candle-progress-bar" style={{ width: `${progressPct}%` }} />
            </div>

            {/* ── Player dashboard (you) ── */}
            <aside className="player-dashboard player1">
                <LiveScoreboard
                    gameId={gameId}
                    userId={user.id}
                    currentPrice={serverPrice}
                    startingBalance={game.startingBalance || 1000000}
                    label={`You — ${myName}`}
                    accent="green"
                    gameOver={gameOver}
                />
            </aside>

            {/* ── Chart + price (100 % server-driven) ── */}
            <main className="chart-container">
                {/* Live OHLCV */}
                {currentCandle ? (
                    <div className="price-display">
                        <div className="price-header">
                            <span className="symbol-label">{game.stockSymbol}</span>
                            <span className={`price-value ${currentCandle.close >= currentCandle.open ? 'positive' : 'negative'}`}>
                                ₹{currentCandle.close.toFixed(2)}
                            </span>
                        </div>
                        <div className="ohlc-data">
                            <span>O {currentCandle.open.toFixed(2)}</span>
                            <span>H {currentCandle.high.toFixed(2)}</span>
                            <span>L {currentCandle.low.toFixed(2)}</span>
                            <span>C {currentCandle.close.toFixed(2)}</span>
                            {currentCandle.volume != null && <span>V {currentCandle.volume.toLocaleString()}</span>}
                        </div>
                    </div>
                ) : (
                    <div className="price-display"><p>Waiting for server candle…</p></div>
                )}

                {/* Candlestick chart */}
                {chartData.length > 0 && <StockChart data={chartData} />}

                {/* Trade log */}
                {tradeLog.length > 0 && (
                    <div className="trade-log">
                        <h4>Recent Trades</h4>
                        {tradeLog.slice(0, 5).map((t, i) => (
                            <p key={i}>
                                <span className={t.type === 'BUY' || t.type === 'COVER' ? 'positive' : 'negative'}>
                                    {t.type}
                                </span>{' '}
                                {t.quantity}× @ ₹{t.price?.toFixed(2)}
                            </p>
                        ))}
                    </div>
                )}
            </main>

            {/* ── Opponent dashboard ── */}
            <aside className="player-dashboard player2">
                <LiveScoreboard
                    gameId={gameId}
                    userId={opponentId}
                    currentPrice={serverPrice}
                    startingBalance={game.startingBalance || 1000000}
                    label={`Opponent — ${oppName}`}
                    accent="red"
                    gameOver={gameOver}
                />
            </aside>

            {/* ── Trade panel ── */}
            <footer className="trade-panel">
                {(() => {
                    const tradesDisabled = gameOver || game?.status !== 'ACTIVE' || remaining <= 0 || tradeCooldown;
                    return (
                        <>
                            <div className="trade-input-group">
                                <label>Shares</label>
                                <input
                                    type="number"
                                    value={tradeAmount}
                                    onChange={(e) => setTradeAmount(Math.max(1, parseInt(e.target.value, 10) || 1))}
                                    min="1"
                                    disabled={tradesDisabled}
                                />
                            </div>
                            <button className={`trade-btn buy${tradeCooldown ? ' cooldown' : ''}`}   onClick={() => handleTrade('BUY')}   disabled={tradesDisabled}>Buy</button>
                            <button className={`trade-btn sell${tradeCooldown ? ' cooldown' : ''}`}   onClick={() => handleTrade('SELL')}  disabled={tradesDisabled}>Sell</button>
                            <button className={`trade-btn short${tradeCooldown ? ' cooldown' : ''}`}  onClick={() => handleTrade('SHORT')} disabled={tradesDisabled}>Short</button>
                            <button className={`trade-btn cover${tradeCooldown ? ' cooldown' : ''}`}  onClick={() => handleTrade('COVER')} disabled={tradesDisabled}>Cover</button>
                            {tradeCooldown && <span className="cooldown-indicator">⏳</span>}
                        </>
                    );
                })()}
            </footer>
        </div>
    );
};

export default GamePage;