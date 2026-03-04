// src/pages/LeaderboardPage.jsx
import React, { useState, useEffect } from 'react';
import { backendUrl } from '../utils/api';
import { useAuth } from '../context/AuthContext';
import TierBadge from '../components/TierBadge';
import './LeaderboardPage.css';

const TAB_MULTI    = 'multiplayer';
const TAB_PRACTICE = 'practice';

const LeaderboardPage = () => {
    const { user } = useAuth();
    const [activeTab,       setActiveTab]       = useState(TAB_MULTI);
    const [entries,         setEntries]         = useState([]);
    const [practiceEntries, setPracticeEntries] = useState([]);
    const [loading,         setLoading]         = useState(true);
    const [error,           setError]           = useState(null);

    // ── Load multiplayer leaderboard ──────────────────────────────────────
    useEffect(() => {
        (async () => {
            setLoading(true);
            setError(null);
            try {
                const res = await fetch(backendUrl('/api/users/leaderboard'));
                if (!res.ok) throw new Error('Failed to load leaderboard');
                const data = await res.json();
                setEntries(data);
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        })();
    }, []);

    // ── Load practice leaderboard ─────────────────────────────────────────
    useEffect(() => {
        if (activeTab !== TAB_PRACTICE) return;
        (async () => {
            try {
                const res = await fetch(backendUrl('/api/leaderboard'));
                if (!res.ok) throw new Error('Failed to load practice leaderboard');
                const data = await res.json();
                setPracticeEntries(data);
            } catch {
                // Non-critical; practice leaderboard might be empty
            }
        })();
    }, [activeTab]);

    if (loading) {
        return (
            <div className="lb-page">
                <div className="lb-loading">
                    <div className="lb-spinner" />
                    <p>Loading leaderboard…</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="lb-page">
                <p className="lb-error">{error}</p>
            </div>
        );
    }

    return (
        <div className="lb-page">
            {/* ── Header ── */}
            <div className="lb-header">
                <h1 className="lb-title">
                    <span className="lb-icon">🏆</span> Leaderboard
                </h1>
                <p className="lb-subtitle">Global skill rankings across all game modes</p>
            </div>

            {/* ── Mode tabs ── */}
            <div className="lb-tabs">
                <button
                    className={`lb-tab${activeTab === TAB_MULTI ? ' lb-tab--active' : ''}`}
                    onClick={() => setActiveTab(TAB_MULTI)}
                >
                    ⚔️ Multiplayer
                </button>
                <button
                    className={`lb-tab${activeTab === TAB_PRACTICE ? ' lb-tab--active' : ''}`}
                    onClick={() => setActiveTab(TAB_PRACTICE)}
                >
                    📈 Practice Mode
                </button>
            </div>

            {/* ── Multiplayer leaderboard table ── */}
            {activeTab === TAB_MULTI && (
                <div className="lb-table-wrapper">
                    <table className="lb-table">
                        <thead>
                            <tr>
                                <th className="lb-th lb-th-rank">#</th>
                                <th className="lb-th lb-th-user">Trader</th>
                                <th className="lb-th lb-th-rating">Rating</th>
                                <th className="lb-th lb-th-tier">Tier</th>
                                <th className="lb-th lb-th-matches">Matches</th>
                            </tr>
                        </thead>
                        <tbody>
                            {entries.map((e) => {
                                const isMe = user && String(e.userId) === String(user.id);
                                return (
                                    <tr
                                        key={e.userId}
                                        className={`lb-row ${isMe ? 'lb-row-me' : ''} ${e.rank <= 3 ? `lb-top-${e.rank}` : ''}`}
                                    >
                                        <td className="lb-cell lb-cell-rank">
                                            <RankBadge rank={e.rank} />
                                        </td>
                                        <td className="lb-cell lb-cell-user">
                                            <span className="lb-username">{e.username}</span>
                                            {isMe && <span className="lb-you-tag">YOU</span>}
                                        </td>
                                        <td className="lb-cell lb-cell-rating">
                                            <span className="lb-rating-value">{e.rating}</span>
                                        </td>
                                        <td className="lb-cell lb-cell-tier">
                                            <TierBadge rating={e.rating} />
                                        </td>
                                        <td className="lb-cell lb-cell-matches">
                                            {e.totalMatches}
                                        </td>
                                    </tr>
                                );
                            })}
                            {entries.length === 0 && (
                                <tr>
                                    <td colSpan={5} className="lb-empty">No players yet. Be the first!</td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}

            {/* ── Practice Mode ELO leaderboard ── */}
            {activeTab === TAB_PRACTICE && (
                <div className="lb-table-wrapper">
                    <p className="lb-practice-note">
                        Ratings earned by responding to strategy hints in Practice Mode.
                        Start at 1000 — gain +8 for correct decisions, lose 4 for wrong ones.
                    </p>
                    <table className="lb-table">
                        <thead>
                            <tr>
                                <th className="lb-th lb-th-rank">#</th>
                                <th className="lb-th lb-th-user">Player</th>
                                <th className="lb-th lb-th-rating">ELO</th>
                                <th className="lb-th lb-th-matches">Sessions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {practiceEntries.map((p, i) => {
                                const isMe = user && p.username === user.username;
                                return (
                                    <tr
                                        key={p.id}
                                        className={`lb-row ${isMe ? 'lb-row-me' : ''} ${i < 3 ? `lb-top-${i + 1}` : ''}`}
                                    >
                                        <td className="lb-cell lb-cell-rank">
                                            <RankBadge rank={i + 1} />
                                        </td>
                                        <td className="lb-cell lb-cell-user">
                                            <span className="lb-username">{p.username}</span>
                                            {isMe && <span className="lb-you-tag">YOU</span>}
                                        </td>
                                        <td className="lb-cell lb-cell-rating">
                                            <span className="lb-rating-value">{p.rating}</span>
                                        </td>
                                        <td className="lb-cell lb-cell-matches">
                                            {p.gamesPlayed}
                                        </td>
                                    </tr>
                                );
                            })}
                            {practiceEntries.length === 0 && (
                                <tr>
                                    <td colSpan={4} className="lb-empty">
                                        No practice scores yet. Head to Practice Mode and make your first trade!
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

// ── Rank badge with medal for top 3 ──
const RankBadge = ({ rank }) => {
    if (rank === 1) return <span className="lb-medal lb-gold">🥇</span>;
    if (rank === 2) return <span className="lb-medal lb-silver">🥈</span>;
    if (rank === 3) return <span className="lb-medal lb-bronze">🥉</span>;
    return <span className="lb-rank-num">{rank}</span>;
};

export default LeaderboardPage;
