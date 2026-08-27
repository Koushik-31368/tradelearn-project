// src/features/simulator/components/MissionDebriefModal.jsx
import React from 'react';
import './MissionDebriefModal.css';

const GRADE_COLORS = { A: '#39FF88', B: '#FFD400', C: '#FF9500', D: '#FF3B5C', F: '#FF3B5C' };

const MissionDebriefModal = ({ assessment, onClose }) => {
  if (!assessment) return null;

  const isPass = assessment.status === 'PASS';

  return (
    <div className="msn-modal-overlay">
      <div className={`msn-modal${isPass ? ' msn-modal--pass' : ' msn-modal--fail'}`}>
        {/* Status */}
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

        {/* Debrief */}
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

        {/* Action */}
        <button className="msn-modal__btn" onClick={onClose}>
          {assessment.nextMission === 'completed' ? '🎉 All Missions Complete — Return' : isPass ? 'Next Mission →' : 'Try Again →'}
        </button>
      </div>
    </div>
  );
};

export default MissionDebriefModal;
