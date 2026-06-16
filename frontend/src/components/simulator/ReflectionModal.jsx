import React, { useState, useEffect } from 'react';
import './TradingPanel.css'; // Reusing some base styles

const ReflectionModal = ({ userId, onReflectionsComplete }) => {
  const [pendingJournals, setPendingJournals] = useState([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [mistakesMade, setMistakesMade] = useState('');
  const [lessonsLearned, setLessonsLearned] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    fetchPending();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  const fetchPending = async () => {
    try {
      const res = await fetch(`/api/journals/user/${userId}/pending`);
      if (res.ok) {
        const data = await res.json();
        setPendingJournals(data);
      }
    } catch (err) {
      console.error('Failed to fetch pending journals', err);
    }
  };

  const currentJournal = pendingJournals[currentIndex];

  const handleSubmit = async () => {
    if (!mistakesMade || !lessonsLearned || !currentJournal) return;
    setIsSubmitting(true);
    try {
      const res = await fetch(`/api/journals/reflect/${currentJournal.id}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mistakesMade, lessonsLearned }),
      });
      if (res.ok) {
        setMistakesMade('');
        setLessonsLearned('');
        if (currentIndex + 1 < pendingJournals.length) {
          setCurrentIndex(currentIndex + 1);
        } else {
          setPendingJournals([]);
          if (onReflectionsComplete) onReflectionsComplete();
        }
      }
    } catch (err) {
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (pendingJournals.length === 0) return null;

  return (
    <div className="reflection-modal-overlay" style={overlayStyle}>
      <div className="reflection-modal" style={modalStyle}>
        <h3>Trade Reflection Required</h3>
        <p style={{ color: '#ffeb3b', fontSize: '13px' }}>
          You have {pendingJournals.length - currentIndex} closed position(s) that require review.
          Disciplined traders always review their performance.
        </p>

        <div style={{ marginTop: '15px', backgroundColor: '#21262d', padding: '15px', borderRadius: '6px' }}>
          <h4 style={{ margin: '0 0 10px 0' }}>Reviewing {currentJournal.symbol}</h4>
          <p style={{ fontSize: '12px', color: '#8b949e', marginBottom: '5px' }}>
            <strong>Original Thesis:</strong><br />
            {currentJournal.thesis}
          </p>
          <div style={{ display: 'flex', gap: '10px', fontSize: '12px', marginTop: '10px' }}>
            <span><strong>Entry:</strong> ₹{currentJournal.entryPrice}</span>
            <span><strong>Target:</strong> ₹{currentJournal.targetPrice}</span>
            <span><strong>Stop Loss:</strong> ₹{currentJournal.stopLoss}</span>
          </div>
        </div>

        <div style={{ marginTop: '20px' }}>
          <label style={labelStyle}>What mistakes did you make? (Required)</label>
          <textarea
            style={textareaStyle}
            value={mistakesMade}
            onChange={(e) => setMistakesMade(e.target.value)}
            placeholder="Did you FOMO? Did you size too big? Ignored the stop loss?"
          />
        </div>

        <div style={{ marginTop: '15px' }}>
          <label style={labelStyle}>What is the main lesson for next time? (Required)</label>
          <textarea
            style={textareaStyle}
            value={lessonsLearned}
            onChange={(e) => setLessonsLearned(e.target.value)}
            placeholder="I will wait for the candle to close next time..."
          />
        </div>

        <button
          onClick={handleSubmit}
          disabled={isSubmitting || !mistakesMade || !lessonsLearned}
          style={{
            marginTop: '20px',
            width: '100%',
            padding: '10px',
            backgroundColor: (isSubmitting || !mistakesMade || !lessonsLearned) ? '#30363d' : '#238636',
            color: '#fff',
            border: 'none',
            borderRadius: '6px',
            cursor: (isSubmitting || !mistakesMade || !lessonsLearned) ? 'not-allowed' : 'pointer',
            fontWeight: 'bold'
          }}
        >
          {isSubmitting ? 'Saving...' : 'Submit Reflection'}
        </button>
      </div>
    </div>
  );
};

// Inline styles for quick modal setup without breaking existing CSS structure
const overlayStyle = {
  position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
  backgroundColor: 'rgba(0,0,0,0.8)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 9999, backdropFilter: 'blur(5px)'
};

const modalStyle = {
  backgroundColor: '#0d1117', border: '1px solid #30363d',
  borderRadius: '12px', padding: '25px', width: '90%', maxWidth: '500px',
  boxShadow: '0 8px 24px rgba(0,0,0,0.5)'
};

const labelStyle = {
  display: 'block', fontSize: '13px', fontWeight: 'bold', marginBottom: '8px', color: '#c9d1d9'
};

const textareaStyle = {
  width: '100%', minHeight: '80px', padding: '10px',
  backgroundColor: '#010409', border: '1px solid #30363d',
  color: '#c9d1d9', borderRadius: '6px', fontSize: '13px',
  resize: 'vertical', boxSizing: 'border-box'
};

export default ReflectionModal;
