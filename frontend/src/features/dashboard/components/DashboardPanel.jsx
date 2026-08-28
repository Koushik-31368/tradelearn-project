// src/features/dashboard/components/DashboardPanel.jsx
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchDailyQuests, fetchWeeklyChallenges } from '../../../api/api';
import './DashboardPanel.css';

/* ── Tier helper ── */
const getTier = (rating) => {
  if (rating >= 2600) return { name: 'Grandmaster', next: null,      min: 2600, max: 3000 };
  if (rating >= 2100) return { name: 'Master',      next: 'Grandmaster', min: 2100, max: 2600 };
  if (rating >= 1600) return { name: 'Diamond',     next: 'Master',   min: 1600, max: 2100 };
  if (rating >= 1200) return { name: 'Platinum',    next: 'Diamond',  min: 1200, max: 1600 };
  if (rating >= 900)  return { name: 'Gold',        next: 'Platinum', min: 900,  max: 1200 };
  if (rating >= 600)  return { name: 'Silver',      next: 'Gold',     min: 600,  max: 900  };
  return               { name: 'Bronze',      next: 'Silver',   min: 0,    max: 600  };
};

/* ── Quick quest item ── */
const QuestItem = ({ item }) => {
  const pct = item.targetValue > 0
    ? Math.min(100, Math.round((item.progress / item.targetValue) * 100))
    : 0;
  return (
    <div className={`dp-quest-item${item.completed ? ' completed' : ''}`}>
      <div className="dp-quest-item__top">
        <span className="dp-quest-item__name">
          {item.completed ? '✓ ' : '○ '}{item.name}
        </span>
        {item.xpReward > 0 && (
          <span className="dp-quest-item__xp">+{item.xpReward} XP</span>
        )}
      </div>
      <p className="dp-quest-item__desc">
        {item.description}
        {!item.completed && item.targetValue > 0 && ` (${item.progress}/${item.targetValue})`}
      </p>
      {!item.completed && item.targetValue > 0 && (
        <div className="dp-quest-progress">
          <div className="dp-quest-progress__fill" style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  );
};

/* ══════════════════════════════════════════════════════════════ */
const DashboardPanel = ({ user }) => {
  const navigate = useNavigate();
  const [dailyQuests,       setDailyQuests]       = useState([]);
  const [weeklyChallenges,  setWeeklyChallenges]  = useState([]);
  const [loading,           setLoading]           = useState(true);

  useEffect(() => {
    if (!user) return;
    (async () => {
      try {
        const [d, w] = await Promise.all([fetchDailyQuests(), fetchWeeklyChallenges()]);
        setDailyQuests(Array.isArray(d) ? d : []);
        setWeeklyChallenges(Array.isArray(w) ? w : []);
      } catch {
        /* silent */
      } finally {
        setLoading(false);
      }
    })();
  }, [user]);

  const rating = user?.rating || 1000;
  const tier   = getTier(rating);
  const pct    = tier.max > tier.min
    ? Math.min(100, Math.round((rating - tier.min) / (tier.max - tier.min) * 100))
    : 100;

  if (loading) {
    return (
      <div className="dp-page">
        <div className="dp-loading-wrap">
          <div className="dp-spinner" />
          Loading your dashboard…
        </div>
      </div>
    );
  }

  return (
    <div className="dp-page">
      {/* ── Welcome hero strip ── */}
      <div className="dp-hero">
        <div>
          <div className="dp-hero__greet">Welcome back</div>
          <h1 className="dp-hero__name">{user?.username}</h1>
          <p className="dp-hero__sub">Ready to conquer the market today?</p>
        </div>
        <div className="dp-hero__badges">
          <span className="dp-badge dp-badge--streak">
            🔥 {user?.loginStreak ?? 0} Day Streak
          </span>
          <span className="dp-badge dp-badge--xp">
            ⭐ {user?.xp ?? 0} XP
          </span>
          <span className="dp-badge dp-badge--tier">
            🏆 {tier.name}
          </span>
        </div>
      </div>

      {/* ── Grid body ── */}
      <div className="dp-body">

        {/* Row 1: Quick action cards */}
        <div
          id="dp-go-missions"
          className="dp-action dp-action--missions"
          onClick={() => navigate('/missions')}
          role="button"
          tabIndex={0}
        >
          <span className="dp-action__icon">🎯</span>
          <h3 className="dp-action__title">Flight School</h3>
          <p className="dp-action__desc">
            Complete historical trading missions. Build discipline before risking real capital.
          </p>
          <span className="dp-action__cta">▶ Launch →</span>
        </div>

        <div
          id="dp-go-simulator"
          className="dp-action dp-action--simulator"
          onClick={() => navigate('/simulator')}
          role="button"
          tabIndex={0}
        >
          <span className="dp-action__icon">📈</span>
          <h3 className="dp-action__title">Simulator</h3>
          <p className="dp-action__desc">
            Free-form trading simulator. Practice strategies on real NIFTY data with no time pressure.
          </p>
          <span className="dp-action__cta">▶ Open →</span>
        </div>

        <div
          id="dp-go-multi"
          className="dp-action dp-action--multi"
          onClick={() => navigate('/multiplayer')}
          role="button"
          tabIndex={0}
        >
          <span className="dp-action__icon">⚔️</span>
          <h3 className="dp-action__title">Multiplayer</h3>
          <p className="dp-action__desc">
            Head-to-head ranked trading battles. Win to climb tiers. Lose to learn.
          </p>
          <span className="dp-action__cta">▶ Join match →</span>
        </div>

        {/* Row 2: Stats cards */}
        <div className="dp-stats">
          <div className="dp-stat-card">
            <div className="dp-stat-card__label">🏅 Rating</div>
            <div className="dp-stat-card__val dp-stat-card__val--gold">{rating}</div>
            <div className="dp-league-bar">
              <div className="dp-league-bar__fill" style={{ width: `${pct}%` }} />
            </div>
            {tier.next && (
              <div className="dp-league-bar__next">{pct}% → {tier.next}</div>
            )}
          </div>

          <div className="dp-stat-card">
            <div className="dp-stat-card__label">⭐ Total XP</div>
            <div className="dp-stat-card__val dp-stat-card__val--purple">
              {(user?.xp ?? 0).toLocaleString()}
            </div>
          </div>

          <div className="dp-stat-card">
            <div className="dp-stat-card__label">🔥 Login Streak</div>
            <div className="dp-stat-card__val dp-stat-card__val--green">
              {user?.loginStreak ?? 0}d
            </div>
          </div>

          <div className="dp-stat-card">
            <div className="dp-stat-card__label">🎮 Matches</div>
            <div className="dp-stat-card__val">
              {user?.totalMatches ?? 0}
            </div>
          </div>
        </div>

        {/* Row 3 left: Daily + Weekly quests */}
        <div className="dp-quests">
          {/* Daily quests */}
          <div className="dp-quest-panel">
            <div className="dp-quest-panel__header dp-quest-panel__header--daily">
              <span className="dp-quest-panel__header-icon">📅</span>
              <span className="dp-quest-panel__title">Today's Quests</span>
            </div>
            {dailyQuests.length === 0 ? (
              <p className="dp-quest-empty">No quests available. Check back tomorrow!</p>
            ) : (
              dailyQuests.map(q => <QuestItem key={q.id} item={q} />)
            )}
          </div>

          {/* Weekly challenges */}
          <div className="dp-quest-panel">
            <div className="dp-quest-panel__header dp-quest-panel__header--weekly">
              <span className="dp-quest-panel__header-icon">🎯</span>
              <span className="dp-quest-panel__title">Weekly Challenges</span>
            </div>
            {weeklyChallenges.length === 0 ? (
              <p className="dp-quest-empty">No challenges this week yet.</p>
            ) : (
              weeklyChallenges.map(c => <QuestItem key={c.id} item={c} />)
            )}
          </div>
        </div>

        {/* Row 3 right: Quick links */}
        <div className="dp-links">
          <div className="dp-links__header">⚡ Quick Links</div>
          {[
            { icon: '🏆', label: 'Leaderboard', path: '/leaderboard' },
            { icon: '👤', label: 'My Profile',  path: '/profile' },
            { icon: '📜', label: 'History',     path: '/history' },
            { icon: '🎮', label: 'Multiplayer', path: '/multiplayer' },
            { icon: '📚', label: 'Flight School (Missions)', path: '/missions' },
          ].map(l => (
            <div
              key={l.path}
              id={`dp-link-${l.path.replace('/', '')}`}
              className="dp-link-row"
              onClick={() => navigate(l.path)}
              role="button"
              tabIndex={0}
            >
              <span className="dp-link-row__icon">{l.icon}</span>
              <span className="dp-link-row__label">{l.label}</span>
              <span className="dp-link-row__arrow">›</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default DashboardPanel;
