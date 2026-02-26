// src/pages/ProfilePage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { backendUrl, authHeaders } from '../utils/api';
import './ProfilePage.css';

const ProfilePage = () => {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!user) return;
        (async () => {
            try {
                const res = await fetch(backendUrl(`/api/users/${user.id}/profile`), { headers: authHeaders() });
                if (!res.ok) throw new Error('Failed to load profile');
                setProfile(await res.json());
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        })();
    }, [user]);

    if (!user) {
        return (
            <div className="pf-page">
                <p className="pf-login-prompt">Please <span className="pf-link" onClick={() => navigate('/login')}>log in</span> to view your profile.</p>
            </div>
        );
    }

    if (loading) {
        return (
            <div className="pf-page">
                <div className="pf-loading">
                    <div className="pf-spinner" />
                    <p>Loading profile…</p>
                </div>
            </div>
        );
    }

    if (error) return <div className="pf-page"><p className="pf-error">{error}</p></div>;
    if (!profile) return <div className="pf-page"><p>No profile data.</p></div>;

    const winRate = profile.totalFinished > 0
        ? ((profile.wins / profile.totalFinished) * 100).toFixed(1)
        : '0.0';

    return (
        <div className="pf-page">
            {/* ── Identity card ── */}
            <section className="pf-identity">
                <div className="pf-avatar">{profile.username?.charAt(0).toUpperCase()}</div>
                <div className="pf-identity-text">
                    <h1 className="pf-username">{profile.username}</h1>
                    <div className="pf-rating-row">
                        <span className="pf-rating-badge">{profile.rating}</span>
                        <span className="pf-rating-label">ELO</span>
                        {profile.rank > 0 && (
                            <span className="pf-rank-badge">#{profile.rank}</span>
                        )}
                    </div>
                </div>
            </section>

            {/* ── Stat cards ── */}
            <section className="pf-stats-grid">
                <StatCard title="Record" accent="green">
                    <div className="pf-record">
                        <span className="pf-record-w">{profile.wins}W</span>
                        <span className="pf-record-sep">/</span>
                        <span className="pf-record-l">{profile.losses}L</span>
                        <span className="pf-record-sep">/</span>
                        <span className="pf-record-d">{profile.draws}D</span>
                    </div>
                    <div className="pf-stat-sub">Win Rate: <strong>{winRate}%</strong></div>
                </StatCard>

                <StatCard title="Avg Drawdown" accent={profile.avgDrawdown > 0.1 ? 'red' : 'green'}>
                    <div className="pf-stat-big">{(profile.avgDrawdown * 100).toFixed(1)}%</div>
                    <div className="pf-stat-sub">Lower is better</div>
                </StatCard>

                <StatCard title="Avg Accuracy" accent={profile.avgAccuracy >= 50 ? 'green' : 'amber'}>
                    <div className="pf-stat-big">{profile.avgAccuracy.toFixed(1)}%</div>
                    <div className="pf-acc-bar-track">
                        <div
                            className="pf-acc-bar-fill"
                            style={{ width: `${Math.min(profile.avgAccuracy, 100)}%` }}
                        />
                    </div>
                </StatCard>

                <StatCard title="Avg Score" accent="blue">
                    <div className="pf-stat-big">{profile.avgScore.toFixed(1)}</div>
                    <div className="pf-stat-sub">Hybrid composite (0–100)</div>
                </StatCard>
            </section>

            {/* ── Recent matches ── */}
            <section className="pf-recent">
                <h2 className="pf-section-title">Recent Matches</h2>
                {profile.recentMatches.length === 0 ? (
                    <p className="pf-empty">No matches played yet.</p>
                ) : (
                    <div className="pf-match-list">
                        {profile.recentMatches.map((m) => (
                            <MatchRow key={m.gameId} match={m} startBal={m.startingBalance || 1000000} navigate={navigate} />
                        ))}
                    </div>
                )}
            </section>
        </div>
    );
};

// ── Stat card wrapper ──
const StatCard = ({ title, accent, children }) => (
    <div className="pf-stat-card">
        <div className="pf-stat-card-top">
            <span className={`pf-stat-dot pf-dot-${accent}`} />
            <span className="pf-stat-title">{title}</span>
        </div>
        {children}
    </div>
);

// ── Single match row ──
const MatchRow = ({ match, startBal, navigate }) => {
    const m = match;
    const profitPct = m.finalBalance != null && startBal > 0
        ? ((m.finalBalance - startBal) / startBal * 100)
        : null;

    const resultClass = m.result === 'WIN' ? 'pf-res-win'
        : m.result === 'LOSS' ? 'pf-res-loss'
        : m.result === 'DRAW' ? 'pf-res-draw'
        : 'pf-res-active';

    const resultIcon = m.result === 'WIN' ? '🏆'
        : m.result === 'LOSS' ? '😞'
        : m.result === 'DRAW' ? '🤝'
        : '⏳';

    const clickable = m.status === 'FINISHED';

    return (
        <div
            className={`pf-match-row ${clickable ? 'pf-match-clickable' : ''}`}
            onClick={() => clickable && navigate(`/match/${m.gameId}/result`)}
        >
            <div className="pf-match-result">
                <span className="pf-match-icon">{resultIcon}</span>
                <span className={`pf-match-tag ${resultClass}`}>{m.result}</span>
            </div>
            <div className="pf-match-info">
                <span className="pf-match-symbol">{m.stockSymbol}</span>
                <span className="pf-match-vs">vs {m.opponentName}</span>
            </div>
            <div className="pf-match-stats">
                {profitPct !== null && (
                    <span className={`pf-match-profit ${profitPct >= 0 ? 'pf-positive' : 'pf-negative'}`}>
                        {profitPct >= 0 ? '+' : ''}{profitPct.toFixed(2)}%
                    </span>
                )}
                {m.eloDelta != null && (
                    <span className={`pf-match-elo ${m.eloDelta >= 0 ? 'pf-positive' : 'pf-negative'}`}>
                        {m.eloDelta >= 0 ? '+' : ''}{m.eloDelta}
                    </span>
                )}
            </div>
        </div>
    );
};

export default ProfilePage;
