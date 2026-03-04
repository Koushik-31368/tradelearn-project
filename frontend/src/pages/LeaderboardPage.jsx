// src/pages/LeaderboardPage.jsx
import React, { useState, useEffect } from 'react';
import { backendUrl } from '../utils/api';
import { useAuth } from '../context/AuthContext';
import TierBadge from '../components/TierBadge';
import './LeaderboardPage.css';

const LeaderboardPage = () => {
    const { user } = useAuth();
    const [entries, setEntries] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        (async () => {
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
                <p className="lb-subtitle">Top 50 traders ranked by ELO rating</p>
            </div>

            {/* ── Table ── */}
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
