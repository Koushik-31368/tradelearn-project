// src/pages/MatchHistoryPage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { backendUrl, authHeaders } from '../utils/api';
import './MatchHistoryPage.css';

const MatchHistoryPage = () => {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [matches, setMatches] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!user) return;
        (async () => {
            try {
                const res = await fetch(backendUrl(`/api/match/user/${user.id}`), { headers: authHeaders() });
                if (!res.ok) throw new Error('Failed to load match history');
                const data = await res.json();
                // Sort newest first
                data.sort((a, b) => {
                    const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
                    const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
                    return tb - ta;
                });
                setMatches(data);
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        })();
    }, [user]);

    if (!user) {
        return (
            <div className="mh-page">
                <p className="mh-login-prompt">
                    Please <span className="mh-link" onClick={() => navigate('/login')}>log in</span> to view your match history.
                </p>
            </div>
        );
    }

    if (loading) {
        return (
            <div className="mh-page">
                <div className="mh-loading">
                    <div className="mh-spinner" />
                    <p>Loading matches…</p>
                </div>
            </div>
        );
    }

    if (error) return <div className="mh-page"><p className="mh-error">{error}</p></div>;

    const finished = matches.filter(m => m.status === 'FINISHED');
    const active   = matches.filter(m => m.status === 'ACTIVE');
    const waiting  = matches.filter(m => m.status === 'WAITING');

    return (
        <div className="mh-page">
            <div className="mh-header">
                <h1 className="mh-title">📜 Match History</h1>
                <p className="mh-subtitle">{matches.length} matches · {finished.length} finished</p>
            </div>

            {/* Active / Waiting */}
            {(active.length > 0 || waiting.length > 0) && (
                <section className="mh-section">
                    <h2 className="mh-section-title">In Progress</h2>
                    <div className="mh-table-wrapper">
                        <table className="mh-table">
                            <thead>
                                <tr>
                                    <th className="mh-th">Status</th>
                                    <th className="mh-th">Stock</th>
                                    <th className="mh-th">Opponent</th>
                                    <th className="mh-th mh-th-right">Date</th>
                                </tr>
                            </thead>
                            <tbody>
                                {[...active, ...waiting].map(m => {
                                    const isCreator = m.creator?.id === user.id;
                                    const oppName = isCreator
                                        ? (m.opponent?.username || '—')
                                        : (m.creator?.username || '—');
                                    return (
                                        <tr
                                            key={m.id}
                                            className="mh-row mh-row-clickable"
                                            onClick={() => navigate(`/game/${m.id}`)}
                                        >
                                            <td className="mh-cell">
                                                <span className={`mh-tag ${m.status === 'ACTIVE' ? 'mh-tag-active' : 'mh-tag-waiting'}`}>
                                                    {m.status}
                                                </span>
                                            </td>
                                            <td className="mh-cell mh-cell-symbol">{m.stockSymbol}</td>
                                            <td className="mh-cell">{oppName}</td>
                                            <td className="mh-cell mh-cell-right mh-cell-date">
                                                {formatDate(m.createdAt)}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </section>
            )}

            {/* Finished matches */}
            <section className="mh-section">
                <h2 className="mh-section-title">Completed</h2>
                {finished.length === 0 ? (
                    <p className="mh-empty">No completed matches yet. Start one from the <span className="mh-link" onClick={() => navigate('/multiplayer')}>Lobby</span>!</p>
                ) : (
                    <div className="mh-table-wrapper">
                        <table className="mh-table">
                            <thead>
                                <tr>
                                    <th className="mh-th">Result</th>
                                    <th className="mh-th">Stock</th>
                                    <th className="mh-th">Opponent</th>
                                    <th className="mh-th mh-th-right">Profit</th>
                                    <th className="mh-th mh-th-right">Score</th>
                                    <th className="mh-th mh-th-right">ELO</th>
                                    <th className="mh-th mh-th-right">Date</th>
                                </tr>
                            </thead>
                            <tbody>
                                {finished.map(m => (
                                    <FinishedRow key={m.id} game={m} userId={user.id} navigate={navigate} />
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
};

// ── Single finished-match row ──
const FinishedRow = ({ game, userId, navigate }) => {
    const g = game;
    const isCreator = g.creator?.id === userId;
    const oppName   = isCreator ? (g.opponent?.username || '—') : (g.creator?.username || '—');
    const startBal  = g.startingBalance || 1000000;
    const myBal     = isCreator ? g.creatorFinalBalance : g.opponentFinalBalance;
    const myScore   = isCreator ? g.creatorFinalScore   : g.opponentFinalScore;
    const myElo     = isCreator ? g.creatorRatingDelta   : g.opponentRatingDelta;

    const profitPct = myBal != null && startBal > 0
        ? ((myBal - startBal) / startBal) * 100
        : null;

    let result, resultClass, resultIcon;
    if (g.winner == null) {
        result = 'DRAW'; resultClass = 'mh-tag-draw'; resultIcon = '🤝';
    } else if (g.winner.id === userId) {
        result = 'WIN'; resultClass = 'mh-tag-win'; resultIcon = '🏆';
    } else {
        result = 'LOSS'; resultClass = 'mh-tag-loss'; resultIcon = '😞';
    }

    return (
        <tr
            className="mh-row mh-row-clickable"
            onClick={() => navigate(`/match/${g.id}/result`)}
        >
            <td className="mh-cell">
                <span className="mh-result-cell">
                    <span className="mh-result-icon">{resultIcon}</span>
                    <span className={`mh-tag ${resultClass}`}>{result}</span>
                </span>
            </td>
            <td className="mh-cell mh-cell-symbol">{g.stockSymbol}</td>
            <td className="mh-cell">{oppName}</td>
            <td className="mh-cell mh-cell-right">
                {profitPct != null ? (
                    <span className={profitPct >= 0 ? 'mh-positive' : 'mh-negative'}>
                        {profitPct >= 0 ? '+' : ''}{profitPct.toFixed(2)}%
                    </span>
                ) : '—'}
            </td>
            <td className="mh-cell mh-cell-right mh-cell-score">
                {myScore != null ? myScore.toFixed(1) : '—'}
            </td>
            <td className="mh-cell mh-cell-right">
                {myElo != null ? (
                    <span className={myElo >= 0 ? 'mh-positive' : 'mh-negative'}>
                        {myElo >= 0 ? '+' : ''}{myElo}
                    </span>
                ) : '—'}
            </td>
            <td className="mh-cell mh-cell-right mh-cell-date">
                {formatDate(g.createdAt)}
            </td>
        </tr>
    );
};

// ── Date formatter ──
function formatDate(ts) {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export default MatchHistoryPage;
