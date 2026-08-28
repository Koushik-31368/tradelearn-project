// src/features/leaderboard/pages/LeaderboardPage.jsx
import React, { useState, useEffect } from 'react';
import apiClient from '../../../api/client';
import { useAuth } from '../../auth/AuthContext';
import TierBadge from '../components/TierBadge';
import './LeaderboardPage.css';

const TAB_MULTI    = 'multiplayer';
const TAB_LEAGUES  = 'leagues';
const TAB_PRACTICE = 'practice';

const ALL_TIERS = ['Grandmaster', 'Master', 'Diamond', 'Platinum', 'Gold', 'Silver', 'Bronze'];

const TIER_RATING = {
  Grandmaster: 2600, Master: 2100, Diamond: 1600,
  Platinum: 1200, Gold: 900, Silver: 600, Bronze: 100,
};

/* ── Rank badge with medal for top 3 ── */
const RankBadge = ({ rank }) => {
    if (rank === 1) return <span className="lb-medal">🥇</span>;
    if (rank === 2) return <span className="lb-medal">🥈</span>;
    if (rank === 3) return <span className="lb-medal">🥉</span>;
    return <span className="lb-rank-num">{rank}</span>;
};

/* ── Shared table body ── */
const LeaderTable = ({ entries, colSpan, user, rankOffset = 0, extraCol }) => (
    <tbody>
        {entries.map((e, i) => {
            const rank  = (e.rank ?? i + 1 + rankOffset);
            const isMe  = user && (
                String(e.userId) === String(user.id) || e.username === user.username
            );
            const topCls = rank <= 3 ? ` lb-top-${rank}` : '';
            const meCls  = isMe ? ' lb-row-me' : '';
            return (
                <tr key={e.userId ?? e.id ?? i} className={`lb-row${topCls}${meCls}`}>
                    <td className="lb-cell lb-cell-rank"><RankBadge rank={rank} /></td>
                    <td className="lb-cell lb-cell-user">
                        <span className="lb-username">{e.username}</span>
                        {isMe && <span className="lb-you-tag">YOU</span>}
                    </td>
                    <td className="lb-cell lb-cell-rating">
                        <span className="lb-rating-value">{e.rating}</span>
                    </td>
                    {extraCol && (
                        <td className="lb-cell lb-cell-tier">
                            <TierBadge rating={e.rating} />
                        </td>
                    )}
                    <td className="lb-cell lb-cell-matches">
                        {e.totalMatches ?? e.gamesPlayed ?? '—'}
                    </td>
                </tr>
            );
        })}
        {entries.length === 0 && (
            <tr>
                <td colSpan={colSpan} className="lb-empty">
                    No players yet. Be the first!
                </td>
            </tr>
        )}
    </tbody>
);

/* ══════════════════════════════════════════════════════════════ */
const LeaderboardPage = () => {
    const { user } = useAuth();
    const [activeTab,       setActiveTab]       = useState(TAB_MULTI);
    const [entries,         setEntries]         = useState([]);
    const [practiceEntries, setPracticeEntries] = useState([]);
    const [leagueEntries,   setLeagueEntries]   = useState([]);
    const [selectedTier,    setSelectedTier]    = useState('Gold');
    const [loading,         setLoading]         = useState(true);
    const [error,           setError]           = useState(null);

    // ── Load multiplayer leaderboard ──────────────────────────
    useEffect(() => {
        (async () => {
            setLoading(true);
            setError(null);
            try {
                const res = await apiClient.get('/api/users/leaderboard');
                setEntries(res.data);
            } catch (err) {
                setError(err.message ?? 'Failed to load leaderboard');
            } finally {
                setLoading(false);
            }
        })();
    }, []);

    // ── Load practice leaderboard ─────────────────────────────
    useEffect(() => {
        if (activeTab !== TAB_PRACTICE) return;
        (async () => {
            try {
                const res = await apiClient.get('/api/leaderboard');
                setPracticeEntries(res.data);
            } catch { /* non-critical */ }
        })();
    }, [activeTab]);

    // ── Load leagues leaderboard ──────────────────────────────
    useEffect(() => {
        if (activeTab !== TAB_LEAGUES) return;
        (async () => {
            setLoading(true);
            try {
                const res = await apiClient.get(`/api/users/leaderboard/tier/${encodeURIComponent(selectedTier)}`);
                setLeagueEntries(res.data);
            } catch {
                setLeagueEntries([]);
            } finally {
                setLoading(false);
            }
        })();
    }, [activeTab, selectedTier]);

    if (loading) {
        return (
            <div className="lb-page">
                <div className="lb-loading">
                    <div className="lb-spinner" />
                    <p>Loading rankings…</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="lb-page">
                <p className="lb-error">⚠️ {error}</p>
            </div>
        );
    }

    return (
        <div className="lb-page">
            {/* ── Hero ── */}
            <div className="lb-header">
                <div className="lb-eyebrow">
                    <div className="lb-eyebrow-dot" />
                    Global Rankings
                </div>
                <h1 className="lb-title">Leaderboard</h1>
                <p className="lb-subtitle">
                    Compete against traders worldwide. Climb the ranks. Earn your tier.
                </p>
            </div>

            {/* ── Body ── */}
            <div className="lb-body">
                {/* Mode tabs */}
                <div className="lb-tabs">
                    <button
                        id="lb-tab-global"
                        className={`lb-tab${activeTab === TAB_MULTI ? ' lb-tab--active' : ''}`}
                        onClick={() => setActiveTab(TAB_MULTI)}
                    >
                        🌍 Global
                    </button>
                    <button
                        id="lb-tab-leagues"
                        className={`lb-tab${activeTab === TAB_LEAGUES ? ' lb-tab--active' : ''}`}
                        onClick={() => setActiveTab(TAB_LEAGUES)}
                    >
                        🛡️ Leagues
                    </button>
                    <button
                        id="lb-tab-practice"
                        className={`lb-tab${activeTab === TAB_PRACTICE ? ' lb-tab--active' : ''}`}
                        onClick={() => setActiveTab(TAB_PRACTICE)}
                    >
                        📈 Practice
                    </button>
                </div>

                {/* ── Global multiplayer ── */}
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
                            <LeaderTable
                                entries={entries}
                                colSpan={5}
                                user={user}
                                extraCol={true}
                            />
                        </table>
                    </div>
                )}

                {/* ── Leagues ── */}
                {activeTab === TAB_LEAGUES && (
                    <div>
                        <div className="lb-tier-selector">
                            {ALL_TIERS.map(tier => (
                                <button
                                    key={tier}
                                    id={`lb-tier-${tier.toLowerCase()}`}
                                    className={`lb-tier-btn${selectedTier === tier ? ' lb-tier-btn--active' : ''}`}
                                    onClick={() => setSelectedTier(tier)}
                                >
                                    <TierBadge rating={TIER_RATING[tier]} />
                                </button>
                            ))}
                        </div>
                        <div className="lb-table-wrapper">
                            <table className="lb-table">
                                <thead>
                                    <tr>
                                        <th className="lb-th lb-th-rank">#</th>
                                        <th className="lb-th lb-th-user">Trader</th>
                                        <th className="lb-th lb-th-rating">Rating</th>
                                        <th className="lb-th lb-th-matches">Matches</th>
                                    </tr>
                                </thead>
                                <LeaderTable
                                    entries={leagueEntries}
                                    colSpan={4}
                                    user={user}
                                    extraCol={false}
                                />
                            </table>
                        </div>
                    </div>
                )}

                {/* ── Practice ── */}
                {activeTab === TAB_PRACTICE && (
                    <div>
                        <p className="lb-practice-note">
                            Ratings earned by responding to strategy hints in Practice Mode.
                            Start at 1000 — gain +8 for correct decisions, lose 4 for wrong ones.
                        </p>
                        <div className="lb-table-wrapper">
                            <table className="lb-table">
                                <thead>
                                    <tr>
                                        <th className="lb-th lb-th-rank">#</th>
                                        <th className="lb-th lb-th-user">Player</th>
                                        <th className="lb-th lb-th-rating">ELO</th>
                                        <th className="lb-th lb-th-matches">Sessions</th>
                                    </tr>
                                </thead>
                                <LeaderTable
                                    entries={practiceEntries}
                                    colSpan={4}
                                    user={user}
                                    extraCol={false}
                                />
                            </table>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default LeaderboardPage;
