// src/features/simulator/pages/MissionSelectionPage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MISSIONS } from '../utils/missions';
import './MissionSelectionPage.css';

const GRADE_COLORS = { A: '#39FF88', B: '#FFD400', C: '#FF9500', D: '#FF3B5C', F: '#FF3B5C' };

const MissionSelectionPage = () => {
  const navigate = useNavigate();
  const [completedMissions, setCompletedMissions] = useState({});

  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('tl_missions') || '{}');
      setCompletedMissions(saved);
    } catch { /* empty */ }
  }, []);

  const isUnlocked = (idx) => {
    if (idx === 0) return true;
    const prev = MISSIONS[idx - 1];
    return completedMissions[prev.id]?.status === 'PASS';
  };

  return (
    <div className="msn-page">
      {/* Header */}
      <div className="msn-hero">
        <h1 className="msn-hero__title">Flight School</h1>
        <p className="msn-hero__sub">
          Complete historical trading missions to prove your discipline before risking real capital.
          Each mission teaches one critical lesson.
        </p>
        {/* Progress */}
        <div className="msn-progress">
          <div className="msn-progress__bar">
            <div
              className="msn-progress__fill"
              style={{ width: `${(Object.values(completedMissions).filter(v => v.status === 'PASS').length / MISSIONS.length) * 100}%` }}
            />
          </div>
          <span className="msn-progress__text">
            {Object.values(completedMissions).filter(v => v.status === 'PASS').length} / {MISSIONS.length} completed
          </span>
        </div>
      </div>

      {/* Mission Roadmap */}
      <div className="msn-roadmap">
        {MISSIONS.map((mission, idx) => {
          const unlocked = isUnlocked(idx);
          const completed = completedMissions[mission.id];
          const passed = completed?.status === 'PASS';

          return (
            <div key={mission.id} className="msn-roadmap__step">
              {/* Connector line */}
              {idx > 0 && (
                <div className={`msn-connector${passed || unlocked ? ' msn-connector--active' : ''}`} />
              )}

              <div className={`msn-card${!unlocked ? ' msn-card--locked' : ''}${passed ? ' msn-card--passed' : ''}`}>
                {/* Number badge */}
                <div className={`msn-card__num${passed ? ' msn-card__num--done' : ''}`}>
                  {passed ? '✓' : idx + 1}
                </div>

                {/* Content */}
                <div className="msn-card__body">
                  <div className="msn-card__top">
                    <div className="msn-card__meta">
                      <span className={`msn-card__diff msn-card__diff--${mission.difficulty.toLowerCase()}`}>
                        {mission.difficulty}
                      </span>
                      <span className="msn-card__lesson">{mission.lesson}</span>
                    </div>
                    <span className="msn-card__icon">{mission.icon}</span>
                  </div>

                  <h3 className="msn-card__title">{mission.title}</h3>
                  <p className="msn-card__subtitle">{mission.subtitle}</p>
                  <p className="msn-card__desc">{mission.description}</p>

                  {/* Constraints */}
                  <div className="msn-card__constraints">
                    <span className="msn-card__tag">📊 {mission.dataset.length} candles</span>
                    <span className="msn-card__tag">🔄 Max {mission.constraints.maxTrades} trades</span>
                    {mission.constraints.maxDrawdownPercent && (
                      <span className="msn-card__tag msn-card__tag--warn">⚠️ Max {mission.constraints.maxDrawdownPercent}% DD</span>
                    )}
                    <span className="msn-card__tag">💰 ₹{(mission.startingBalance / 100000).toFixed(0)}L capital</span>
                  </div>

                  {/* Grade badge if completed */}
                  {completed && (
                    <div className="msn-card__result">
                      <span className="msn-card__grade" style={{ color: GRADE_COLORS[completed.grade] || '#fff' }}>
                        Grade: {completed.grade}
                      </span>
                      <span className="msn-card__result-title">{completed.title}</span>
                    </div>
                  )}

                  {/* Action */}
                  <button
                    className={`msn-card__btn${passed ? ' msn-card__btn--replay' : ''}`}
                    disabled={!unlocked}
                    onClick={() => navigate(`/mission-dashboard/${mission.id}`)}
                  >
                    {!unlocked ? '🔒 Locked' : passed ? '↺ Replay' : '▶ Launch Mission'}
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default MissionSelectionPage;
