// src/features/simulator/components/MissionDebriefModal.jsx
import React, { useMemo } from 'react';
import './MissionDebriefModal.css';

const GRADE_COLORS = {
  A: '#39FF88', B: '#FFD400', C: '#FF9500', D: '#FF3B5C', F: '#FF3B5C',
};

const CONFETTI_COLORS = [
  '#39FF88', '#7B2FF7', '#FFD400', '#FF3B5C', '#00cfff', '#ff88cc', '#fff',
];

/* ── CSS Confetti ─────────────────────────────────────────── */
const Confetti = () => {
  const pieces = useMemo(() => {
    return Array.from({ length: 60 }).map((_, i) => ({
      id: i,
      left:     `${Math.random() * 100}%`,
      color:    CONFETTI_COLORS[i % CONFETTI_COLORS.length],
      delay:    `${Math.random() * 1.5}s`,
      duration: `${2.5 + Math.random() * 2}s`,
      size:     `${6 + Math.random() * 6}px`,
      rotate:   `${Math.random() * 360}deg`,
    }));
  }, []);

  return (
    <div className="msn-confetti-wrap" aria-hidden>
      {pieces.map(p => (
        <div
          key={p.id}
          className="msn-confetti-piece"
          style={{
            left:             p.left,
            background:       p.color,
            width:            p.size,
            height:           p.size,
            animationDuration: p.duration,
            animationDelay:    p.delay,
            transform:        `rotate(${p.rotate})`,
          }}
        />
      ))}
    </div>
  );
};

/* ── Main Modal ───────────────────────────────────────────── */
const MissionDebriefModal = ({ assessment, onClose }) => {
  if (!assessment) return null;

  const isPass    = assessment.status === 'PASS';
  const isComplete = assessment.nextMission === 'completed';
  const stats     = assessment.stats;
  const grade     = assessment.grade;
  const gradeColor = GRADE_COLORS[grade] || '#fff';

  const formatInr = (n) => {
    const abs = Math.abs(n || 0);
    return abs.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  };

  const btnClass = isComplete
    ? 'msn-debrief__btn msn-debrief__btn--complete'
    : isPass
    ? 'msn-debrief__btn msn-debrief__btn--pass'
    : 'msn-debrief__btn msn-debrief__btn--fail';

  const btnLabel = isComplete
    ? '🎉 All Missions Complete — Return to Hub'
    : isPass
    ? `▶ Next Mission →`
    : '↺ Try Again';

  return (
    <>
      {isPass && <Confetti />}
      <div className="msn-debrief-overlay">
        <div className={`msn-debrief msn-debrief--${isPass ? 'pass' : 'fail'}`}>

          {/* ── Status ── */}
          <div className="msn-debrief__status">
            <span className="msn-debrief__emoji">
              {isComplete ? '🎓' : isPass ? '🏆' : '💀'}
            </span>
            <h2
              className="msn-debrief__verdict"
              style={{ color: isPass ? '#39FF88' : '#FF3B5C' }}
            >
              MISSION {assessment.status}
            </h2>
            {grade && (
              <div className="msn-debrief__grade-wrap">
                <span
                  className="msn-debrief__grade"
                  data-grade={grade}
                  style={{ color: gradeColor, textShadow: `0 0 30px ${gradeColor}88` }}
                >
                  {grade}
                </span>
                {assessment.title && (
                  <span className="msn-debrief__title-text">{assessment.title}</span>
                )}
              </div>
            )}
          </div>

          {/* ── Stats ── */}
          {stats && (
            <div className="msn-debrief__stats">
              <div className="msn-debrief__stat">
                <span className="msn-debrief__stat-label">Final Equity</span>
                <span className="msn-debrief__stat-val">₹{formatInr(stats.equity)}</span>
              </div>
              <div className="msn-debrief__stat">
                <span className="msn-debrief__stat-label">P&amp;L</span>
                <span
                  className="msn-debrief__stat-val"
                  style={{ color: (stats.pnlAmt || 0) >= 0 ? '#39FF88' : '#FF3B5C' }}
                >
                  {(stats.pnlAmt || 0) >= 0 ? '+' : '-'}₹{formatInr(stats.pnlAmt)}
                  &nbsp;({(stats.pnlAmt || 0) >= 0 ? '+' : ''}{stats.pnlPct}%)
                </span>
              </div>
              <div className="msn-debrief__stat">
                <span className="msn-debrief__stat-label">Trades Made</span>
                <span className="msn-debrief__stat-val">{stats.tradeCount}</span>
              </div>
              <div className="msn-debrief__stat">
                <span className="msn-debrief__stat-label">Max Drawdown</span>
                <span
                  className="msn-debrief__stat-val"
                  style={{ color: (stats.maxDrawdown || 0) > 10 ? '#FF9500' : '#9A9AB0' }}
                >
                  {(stats.maxDrawdown || 0).toFixed(1)}%
                </span>
              </div>
            </div>
          )}

          <div className="msn-debrief__divider" />

          {/* ── Debrief sections ── */}
          <div className="msn-debrief__sections">
            <div className="msn-debrief__section">
              <h4 className="msn-debrief__section-title msn-debrief__section-title--good">
                ✓ What Went Well
              </h4>
              <p className="msn-debrief__section-text">{assessment.wentWell}</p>
            </div>
            <div className="msn-debrief__section">
              <h4 className="msn-debrief__section-title msn-debrief__section-title--bad">
                ✗ What Went Wrong
              </h4>
              <p className="msn-debrief__section-text">{assessment.wentWrong}</p>
            </div>
            {assessment.lesson && (
              <div className="msn-debrief__section msn-debrief__section--lesson">
                <h4 className="msn-debrief__section-title msn-debrief__section-title--lesson">
                  💡 Key Lesson
                </h4>
                <p className="msn-debrief__section-text msn-debrief__section-text--lesson">
                  {assessment.lesson}
                </p>
              </div>
            )}
          </div>

          {/* ── CTA ── */}
          <button id="msn-debrief-cta" className={btnClass} onClick={onClose}>
            {btnLabel}
          </button>
        </div>
      </div>
    </>
  );
};

export default MissionDebriefModal;
