// src/features/simulator/pages/MissionSelectionPage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MISSIONS } from '../utils/missions';
import './MissionSelectionPage.css';

const GRADE_COLORS = {
  A: '#39FF88', B: '#FFD400', C: '#FF9500', D: '#FF3B5C', F: '#FF3B5C',
};

const MissionSelectionPage = () => {
  const navigate = useNavigate();
  const [completedMissions, setCompletedMissions] = useState({});

  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('tl_missions') || '{}');
      setCompletedMissions(saved);
    } catch { /* empty */ }
  }, []);

  const passedCount = Object.values(completedMissions).filter(v => v.status === 'PASS').length;
  const allComplete = passedCount === MISSIONS.length;

  const isUnlocked = (idx) => {
    if (idx === 0) return true;
    const prev = MISSIONS[idx - 1];
    return completedMissions[prev.id]?.status === 'PASS';
  };

  // find the "current" mission index (first not-passed unlocked one)
  const currentIdx = MISSIONS.findIndex((m, i) => isUnlocked(i) && completedMissions[m.id]?.status !== 'PASS');

  return (
    <div className="msn-page">
      {/* ── Hero ── */}
      <div className="msn-hero">
        <div className="msn-hero__eyebrow">
          <div className="msn-hero__eyebrow-dot" />
          Trading Simulator
        </div>
        <h1 className="msn-hero__title">Flight School</h1>
        <p className="msn-hero__sub">
          Complete historical trading missions to build real discipline before risking real capital.
          Each mission teaches one critical lesson the market will test you on.
        </p>

        {/* XP / Progress ribbon */}
        <div className="msn-xp">
          <span className="msn-xp__icon">🏆</span>
          <span className="msn-xp__label">Progress</span>
          <div className="msn-xp__bar">
            <div
              className="msn-xp__fill"
              style={{ width: `${(passedCount / MISSIONS.length) * 100}%` }}
            />
          </div>
          <span className="msn-xp__count">{passedCount} / {MISSIONS.length} Completed</span>
        </div>
      </div>

      {/* ── Roadmap ── */}
      <div className="msn-roadmap">
        {MISSIONS.map((mission, idx) => {
          const unlocked  = isUnlocked(idx);
          const completed = completedMissions[mission.id];
          const passed    = completed?.status === 'PASS';
          const isCurrent = idx === currentIdx;

          const cardClass = [
            'msn-card',
            !unlocked ? 'msn-card--locked' : '',
            passed    ? 'msn-card--passed'  : '',
            isCurrent ? 'msn-card--current' : '',
          ].filter(Boolean).join(' ');

          return (
            <div key={mission.id} className="msn-roadmap__step">
              {/* Connector line */}
              {idx > 0 && (
                <div className={`msn-connector${passed || unlocked ? ' msn-connector--active' : ''}`} />
              )}

              <div className={cardClass}>
                {/* Lock overlay */}
                {!unlocked && (
                  <div className="msn-card__blur">
                    <div className="msn-card__lock-icon">🔒</div>
                    <div className="msn-card__lock-text">Complete previous mission</div>
                  </div>
                )}

                {/* NEXT badge */}
                {isCurrent && !passed && (
                  <div className="msn-card__next-badge">▸ NEXT</div>
                )}

                {/* Step number */}
                <div className={`msn-card__num${passed ? ' msn-card__num--done' : isCurrent ? ' msn-card__num--current' : ''}`}>
                  {passed ? '✓' : idx + 1}
                </div>

                {/* Content */}
                <div className="msn-card__body">
                  <div className="msn-card__top">
                    <div className="msn-card__meta">
                      <span className={`msn-card__diff msn-card__diff--${mission.difficulty.toLowerCase()}`}>
                        {mission.difficulty}
                      </span>
                      <span className="msn-card__lesson">📚 {mission.lesson}</span>
                    </div>
                    <span className="msn-card__icon">{mission.icon}</span>
                  </div>

                  <div>
                    <h3 className="msn-card__title">{mission.title}</h3>
                    <p className="msn-card__subtitle">{mission.subtitle}</p>
                  </div>

                  <p className="msn-card__desc">{mission.description}</p>

                  {/* Constraints */}
                  <div className="msn-card__tags">
                    <span className="msn-card__tag">📊 {mission.dataset.length} candles</span>
                    <span className="msn-card__tag">🔄 Max {mission.constraints.maxTrades} trades</span>
                    {mission.constraints.maxDrawdownPercent && (
                      <span className="msn-card__tag msn-card__tag--warn">
                        ⚠️ Max {mission.constraints.maxDrawdownPercent}% drawdown
                      </span>
                    )}
                    <span className="msn-card__tag">
                      💰 ₹{(mission.startingBalance / 100000).toFixed(0)}L capital
                    </span>
                  </div>

                  {/* Grade if completed */}
                  {completed && (
                    <div className="msn-card__result">
                      <span
                        className="msn-card__grade-letter"
                        style={{ color: GRADE_COLORS[completed.grade] || '#fff' }}
                      >
                        {completed.grade}
                      </span>
                      <div className="msn-card__result-info">
                        <span className="msn-card__result-label">Best result</span>
                        <span className="msn-card__result-title">{completed.title}</span>
                      </div>
                    </div>
                  )}

                  {/* Launch button */}
                  <button
                    id={`msn-launch-${mission.id}`}
                    className={`msn-card__btn${passed ? ' msn-card__btn--replay' : ''}`}
                    disabled={!unlocked}
                    onClick={() => navigate(`/mission-dashboard/${mission.id}`)}
                  >
                    {!unlocked
                      ? '🔒 Locked'
                      : passed
                      ? '↺ Replay Mission'
                      : '▶ Launch Mission'}
                  </button>
                </div>
              </div>
            </div>
          );
        })}

        {/* All complete banner */}
        {allComplete && (
          <div className="msn-complete">
            <div className="msn-complete__emoji">🎓</div>
            <div className="msn-complete__title">Flight School Graduate!</div>
            <div className="msn-complete__sub">
              You've completed all missions. You're ready for the live simulator.
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default MissionSelectionPage;
