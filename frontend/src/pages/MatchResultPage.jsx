// src/pages/MatchResultPage.jsx
import React, { useState, useEffect, useCallback } from 'react';
import useGameSocket from '../hooks/useGameSocket';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { backendUrl, authHeaders } from '../utils/api';
import './MatchResultPage.css';

const MatchResultPage = () => {
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();

    const [game, setGame] = useState(null);
    const [stats, setStats] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [revealed, setRevealed] = useState(false);
    // Rematch UX
    const [waiting, setWaiting] = useState(false);
    const [countdown, setCountdown] = useState(null);
    const [pendingMatchId, setPendingMatchId] = useState(null);

    // WebSocket hook
    const socket = useGameSocket({
        gameId,
        userId: user?.id,
        enabled: !!user && !!gameId,
    });
    // Rematch event subscription
    useEffect(() => {
        if (!socket.isConnected) return;
        // Subscribe only once per mount
        const handler = (msg) => {
            const newMatchId = msg.body || (msg && msg.body);
            setPendingMatchId(newMatchId);
            setCountdown(3);
        };
        // Subscribe to rematch event
        const sub = socket.publish ? socket.publish : null;
        let subscription;
        if (socket.clientRef && socket.clientRef.current) {
            subscription = socket.clientRef.current.subscribe(
                `/user/queue/rematch`, handler
            );
        }
        return () => {
            if (subscription) subscription.unsubscribe();
        };
    }, [socket.isConnected]);

    // Countdown before redirect
    useEffect(() => {
        if (countdown === null || pendingMatchId === null) return;
        if (countdown === 0) {
            navigate(`/match/${pendingMatchId}`);
            setCountdown(null);
            setPendingMatchId(null);
            setWaiting(false);
            return;
        }
        const timer = setTimeout(() => {
            setCountdown((c) => c - 1);
        }, 1000);
        return () => clearTimeout(timer);
    }, [countdown, pendingMatchId, navigate]);

    // Rematch request
    const requestRematch = async () => {
        try {
            setWaiting(true);
            const res = await fetch(backendUrl(`/api/match/${gameId}/rematch`), {
                method: 'POST',
                credentials: 'include',
                headers: authHeaders(),
            });
            if (!res.ok) throw new Error('Rematch failed');
        } catch (err) {
            setWaiting(false);
            alert('Something went wrong. Try again.');
        }
    };

    // ── Fetch game + stats in parallel ──
    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const hdrs = authHeaders();
            const [gameRes, statsRes] = await Promise.all([
                fetch(backendUrl(`/api/match/${gameId}`)),
                fetch(backendUrl(`/api/match/${gameId}/stats`), { headers: hdrs }),
            ]);

            if (!gameRes.ok) throw new Error('Failed to load match');
            const gameData = await gameRes.json();
            setGame(gameData);

            if (statsRes.ok) {
                const statsData = await statsRes.json();
                if (Array.isArray(statsData)) setStats(statsData);
            }
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
            // Trigger reveal animation after a short delay
            setTimeout(() => setRevealed(true), 300);
        }
    }, [gameId]);

    useEffect(() => { fetchData(); }, [fetchData]);

    // ── Loading / error states ──
    if (loading) {
        return (
            <div className="mr-page">
                <div className="mr-loading">
                    <div className="mr-spinner" />
                    <p>Loading results…</p>
                </div>
            </div>
        );
    }
    if (error) return <div className="mr-page"><p className="mr-error">{error}</p></div>;
    if (!game) return <div className="mr-page"><p>No match data.</p></div>;
    if (!user) return <div className="mr-page"><p>Please log in.</p></div>;

    // ── Derive player data ──
    const isCreator = user.id === game.creator?.id;
    const startBal  = game.startingBalance || 1000000;

    const me = {
        id:       isCreator ? game.creator?.id       : game.opponent?.id,
        name:     isCreator ? game.creator?.username  : game.opponent?.username,
        balance:  isCreator ? game.creatorFinalBalance : game.opponentFinalBalance,
        score:    isCreator ? game.creatorFinalScore   : game.opponentFinalScore,
        eloDelta: isCreator ? game.creatorRatingDelta  : game.opponentRatingDelta,
        rating:   isCreator ? game.creator?.rating     : game.opponent?.rating,
    };
    const opp = {
        id:       isCreator ? game.opponent?.id       : game.creator?.id,
        name:     isCreator ? game.opponent?.username  : game.creator?.username,
        balance:  isCreator ? game.opponentFinalBalance : game.creatorFinalBalance,
        score:    isCreator ? game.opponentFinalScore   : game.creatorFinalScore,
        eloDelta: isCreator ? game.opponentRatingDelta  : game.creatorRatingDelta,
        rating:   isCreator ? game.opponent?.rating     : game.creator?.rating,
    };

    // Attach stats from the stats array
    const myStats  = stats.find(s => String(s.userId) === String(me.id)) || {};
    const oppStats = stats.find(s => String(s.userId) === String(opp.id)) || {};

    const winnerId = game.winner?.id;
    const isDraw   = !winnerId;
    const iWon     = String(winnerId) === String(user.id);

    const profitPct = (bal) => startBal > 0 ? ((bal - startBal) / startBal) * 100 : 0;
    const accuracy  = (s) => s.totalTrades > 0 ? (s.profitableTrades / s.totalTrades) * 100 : 0;

    return (
        <div className="mr-page">
            {/* ── Winner banner ── */}
            <div className={`mr-banner ${revealed ? 'mr-revealed' : ''} ${isDraw ? 'mr-draw' : iWon ? 'mr-win' : 'mr-lose'}`}>
                <div className="mr-banner-icon">
                    {isDraw ? '🤝' : iWon ? '🏆' : '😞'}
                </div>
                <h1 className="mr-banner-text">
                    {isDraw ? 'DRAW' : iWon ? 'VICTORY' : 'DEFEAT'}
                </h1>
                <p className="mr-banner-sub">{game.stockSymbol} · Match #{game.id}</p>
            </div>

            {/* ── Player cards ── */}
            <div className={`mr-cards ${revealed ? 'mr-revealed' : ''}`}>
                <PlayerCard
                    label="YOU"
                    name={me.name}
                    balance={me.balance}
                    profit={profitPct(me.balance)}
                    score={me.score}
                    eloDelta={me.eloDelta}
                    rating={me.rating}
                    stats={myStats}
                    accuracy={accuracy(myStats)}
                    isWinner={String(winnerId) === String(me.id)}
                    isDraw={isDraw}
                    startBal={startBal}
                />
                <div className="mr-vs">VS</div>
                <PlayerCard
                    label="OPPONENT"
                    name={opp.name}
                    balance={opp.balance}
                    profit={profitPct(opp.balance)}
                    score={opp.score}
                    eloDelta={opp.eloDelta}
                    rating={opp.rating}
                    stats={oppStats}
                    accuracy={accuracy(oppStats)}
                    isWinner={String(winnerId) === String(opp.id)}
                    isDraw={isDraw}
                    startBal={startBal}
                />
            </div>

            {/* ── Actions ── */}
            <div className={`mr-actions ${revealed ? 'mr-revealed' : ''}`}>
                <button className="mr-btn mr-btn-primary" onClick={requestRematch} disabled={waiting || countdown !== null}>
                    🔄 Rematch
                </button>
                <button className="mr-btn mr-btn-ghost" onClick={() => navigate('/')} disabled={countdown !== null}>
                    Home
                </button>
                {waiting && countdown === null && <p>Waiting for opponent...</p>}
                {countdown !== null && (
                    <p>Rematch starting in {countdown}...</p>
                )}
            </div>
        </div>
    );
};

// ══════════════════════════════════════════════
// Player stat card
// ══════════════════════════════════════════════
const PlayerCard = ({ label, name, balance, profit, score, eloDelta, rating, stats, accuracy, isWinner, isDraw, startBal }) => {
    const side = isWinner ? 'winner' : isDraw ? 'draw' : 'loser';
    const newRating = rating ?? 1000;
    const oldRating = newRating - (eloDelta ?? 0);

    return (
        <div className={`mr-card mr-card-${side}`}>
            {/* Glow border for winner */}
            {isWinner && <div className="mr-card-glow" />}

            <div className="mr-card-header">
                <span className="mr-card-label">{label}</span>
                <span className="mr-card-name">{name || 'Unknown'}</span>
                {isWinner && <span className="mr-crown">👑</span>}
            </div>

            {/* ── Stat grid ── */}
            <div className="mr-stat-grid">
                <StatCell
                    title="FINAL EQUITY"
                    value={`₹${(balance ?? 0).toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
                    accent={profit >= 0 ? 'green' : 'red'}
                    large
                />
                <StatCell
                    title="PROFIT"
                    value={`${profit >= 0 ? '+' : ''}${profit.toFixed(2)}%`}
                    accent={profit >= 0 ? 'green' : 'red'}
                />
                <StatCell
                    title="PEAK EQUITY"
                    value={`₹${(stats.peakEquity ?? startBal).toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
                />
                <StatCell
                    title="MAX DRAWDOWN"
                    value={`${((stats.maxDrawdown ?? 0) * 100).toFixed(1)}%`}
                    accent={(stats.maxDrawdown ?? 0) > 0.1 ? 'red' : 'green'}
                />
                <StatCell
                    title="ACCURACY"
                    value={`${accuracy.toFixed(0)}%`}
                    accent={accuracy >= 50 ? 'green' : 'amber'}
                    bar={accuracy}
                />
                <StatCell
                    title="HYBRID SCORE"
                    value={(score ?? 0).toFixed(1)}
                    accent="blue"
                    large
                />
            </div>

            {/* ── Animated ELO rating transition ── */}
            {eloDelta !== undefined && eloDelta !== null && (
                <div className="mr-elo-block">
                    <div className="mr-elo-header">
                        <span className="mr-elo-label">RATING</span>
                        <span className={`mr-elo-delta ${eloDelta >= 0 ? 'mr-delta-up' : 'mr-delta-down'}`}>
                            {eloDelta >= 0 ? '+' : ''}{eloDelta}
                        </span>
                    </div>
                    <div className="mr-elo-transition">
                        <AnimatedRating from={oldRating} to={newRating} delta={eloDelta} />
                    </div>
                    <div className="mr-elo-global">
                        Global Rating: <strong>{newRating}</strong>
                    </div>
                </div>
            )}

            {/* ── Trade breakdown ── */}
            <div className="mr-trades-row">
                <span>{stats.totalTrades ?? 0} trades</span>
                <span className="mr-trades-profitable">{stats.profitableTrades ?? 0} profitable</span>
            </div>
        </div>
    );
};

// ── Animated rating counter (old → new) ──
const AnimatedRating = ({ from, to, delta }) => {
    const [display, setDisplay] = useState(from);
    const [phase, setPhase] = useState('idle'); // idle → counting → done

    useEffect(() => {
        const startDelay = setTimeout(() => {
            setPhase('counting');
            const diff = to - from;
            if (diff === 0) { setPhase('done'); return; }
            const steps = Math.min(Math.abs(diff), 40);
            const stepSize = diff / steps;
            const intervalMs = 800 / steps; // finish count in ~800ms
            let current = from;
            let step = 0;

            const timer = setInterval(() => {
                step++;
                current = step >= steps ? to : Math.round(from + stepSize * step);
                setDisplay(current);
                if (step >= steps) {
                    clearInterval(timer);
                    setPhase('done');
                }
            }, intervalMs);

            return () => clearInterval(timer);
        }, 1200); // wait for card reveal animation

        return () => clearTimeout(startDelay);
    }, [from, to]);

    const isUp = delta >= 0;

    return (
        <div className="mr-rating-anim">
            <span className={`mr-rating-old ${phase !== 'idle' ? 'mr-faded' : ''}`}>
                {from}
            </span>
            <span className={`mr-rating-arrow ${phase === 'counting' ? 'mr-arrow-active' : ''} ${isUp ? 'mr-arrow-up' : 'mr-arrow-down'}`}>
                →
            </span>
            <span className={`mr-rating-new ${phase === 'done' ? 'mr-rating-pop' : ''} ${isUp ? 'mr-accent-green' : 'mr-accent-red'}`}>
                {display}
            </span>
        </div>
    );
};

// ── Single stat cell ──
const StatCell = ({ title, value, accent = '', large = false, bar }) => (
    <div className={`mr-stat ${large ? 'mr-stat-lg' : ''}`}>
        <span className="mr-stat-title">{title}</span>
        <span className={`mr-stat-value mr-accent-${accent}`}>{value}</span>
        {bar !== undefined && (
            <div className="mr-stat-bar-track">
                <div className="mr-stat-bar-fill" style={{ width: `${Math.min(bar, 100)}%` }} />
            </div>
        )}
    </div>
);

export default MatchResultPage;
