// src/features/simulator/components/MissionDebriefModal.jsx
import React from 'react';
import './MissionDebriefModal.css';

const GRADE_COLORS = { A: '#39FF88', B: '#FFD400', C: '#FF9500', D: '#FF3B5C', F: '#FF3B5C' };

const MissionDebriefModal = ({ assessment, onClose }) => {
  if (!assessment) return null;

  const isPass = assessment.status === 'PASS';
  const stats = assessment.stats;

  const formatInr = (n) =>
    n?.toLocaleString('en-IN', { maximumFractionDigits: 0 }) ?? '—';

  const btnLabel = () => {
    if (assessment.nextMission === 'completed') return '🎉 All Missions Complete — Return';
    if (isPass) return `Next Mission →`;
    return 'Try Again →';
  };

  return (
    <div className="msn-modal-overlay">
      <div className={`msn-modal${isPass ? ' msn-modal--pass' : ' msn-modal--fail'}`}>

        {/* ── Status ── */}
        <div className="msn-modal__status">
          <span className="msn-modal__emoji">{isPass ? '🏆' : '💀'}</span>
          <h2 className="msn-modal__verdict" style={{ color: isPass ? '#39FF88' : '#FF3B5C' }}>
            MISSION {assessment.status}
          </h2>
          {assessment.grade && (
            <span className="msn-modal__grade" style={{ color: GRADE_COLORS[assessment.grade] }}>
              Grade {assessment.grade}
            </span>
          )}
          {assessment.title && (
            <span className="msn-modal__title-text">{assessment.title}</span>
          )}
        </div>

        {/* ── Trading Stats ── */}
        {stats && (
          <div className="msn-modal__stats-row">
            <div className="msn-modal__stat">
              <span className="msn-modal__stat-label">Final Equity</span>
              <span className="msn-modal__stat-val">₹{formatInr(stats.equity)}</span>
            </div>
            <div className="msn-modal__stat">
              <span className="msn-modal__stat-label">P&amp;L</span>
              <span
                className="msn-modal__stat-val"
                style={{ color: stats.pnlAmt >= 0 ? '#39FF88' : '#FF3B5C' }}
              >
                {stats.pnlAmt >= 0 ? '+' : ''}₹{formatInr(stats.pnlAmt)}
                &nbsp;({stats.pnlAmt >= 0 ? '+' : ''}{stats.pnlPct}%)
              </span>
            </div>
            <div className="msn-modal__stat">
              <span className="msn-modal__stat-label">Trades</span>
              <span className="msn-modal__stat-val">{stats.tradeCount}</span>
            </div>
            <div className="msn-modal__stat">
              <span className="msn-modal__stat-label">Max Drawdown</span>
              <span
                className="msn-modal__stat-val"
                style={{ color: stats.maxDrawdown > 10 ? '#FF9500' : '#ccc' }}
              >
                {stats.maxDrawdown.toFixed(1)}%
              </span>
            </div>
          </div>
        )}

        {/* ── Debrief ── */}
        <div className="msn-modal__debrief">
          <div className="msn-modal__section">
            <h4 className="msn-modal__section-title msn-modal__section-title--good">✓ What Went Well</h4>
            <p className="msn-modal__section-text">{assessment.wentWell}</p>
          </div>
          <div className="msn-modal__section">
            <h4 className="msn-modal__section-title msn-modal__section-title--bad">✗ What Went Wrong</h4>
            <p className="msn-modal__section-text">{assessment.wentWrong}</p>
          </div>
          {assessment.lesson && (
            <div className="msn-modal__section msn-modal__section--lesson">
              <h4 className="msn-modal__section-title msn-modal__section-title--lesson">💡 Key Lesson</h4>
              <p className="msn-modal__section-text msn-modal__section-text--lesson">{assessment.lesson}</p>
            </div>
          )}
        </div>

        {/* ── Action ── */}
        <button className="msn-modal__btn" onClick={onClose}>
          {btnLabel()}
        </button>
      </div>
    </div>
  );
};

export default MissionDebriefModal;
