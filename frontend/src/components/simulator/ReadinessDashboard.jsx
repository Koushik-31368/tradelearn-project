// src/components/simulator/ReadinessDashboard.jsx
import React, { useState, useEffect } from 'react';
import './ReadinessDashboard.css';

const ReadinessDashboard = ({ userId = 1 }) => {
  const [readiness, setReadiness] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchReadiness = async () => {
      try {
        const res = await fetch(`/api/analytics/user/${userId}/readiness`);
        if (res.ok) {
          const data = await res.json();
          setReadiness(data);
        }
      } catch (err) {
        console.error("Failed to load readiness data", err);
      } finally {
        setLoading(false);
      }
    };
    fetchReadiness();
  }, [userId]);

  if (loading) {
    return <div className="readiness-loading">Evaluating Readiness...</div>;
  }

  if (!readiness) {
    return <div className="readiness-error">Could not load readiness data.</div>;
  }

  if (readiness.status === "INSUFFICIENT_DATA") {
    return (
      <div className="readiness-dashboard insufficient-data">
        <h2 className="readiness-header">Readiness Evaluation Locked</h2>
        <p className="readiness-msg">{readiness.message}</p>
        <div className="requirements-box">
          <h4>Requirements to Unlock:</h4>
          <ul>
            {Object.values(readiness.requirements).map((req, i) => (
              <li key={i}>{req}</li>
            ))}
          </ul>
        </div>
      </div>
    );
  }

  const {
    educationalTier,
    strengths,
    needsImprovement,
    recommendation,
    disciplineScore,
    learningScore,
    tradingScore,
    resultsScore
  } = readiness;

  return (
    <div className="readiness-dashboard">
      <h2 className="readiness-header">Coaching & Readiness Report</h2>
      
      {/* Tier Banner */}
      <div className="tier-banner">
        <span className="tier-label">Current Educational Tier</span>
        <h1 className="tier-value">{educationalTier}</h1>
        {educationalTier === "Ready For Paper Trading" && (
          <div className="tier-subtitle">You have graduated the simulator. You are now ready for advanced paper trading.</div>
        )}
      </div>

      <div className="readiness-content-grid">
        {/* Left Column: Strengths & Weaknesses */}
        <div className="coaching-section">
          
          <div className="coaching-box strengths-box">
            <h3>Strengths</h3>
            <ul>
              {strengths.map((s, i) => (
                <li key={i}><span className="check-icon">✓</span> {s}</li>
              ))}
            </ul>
          </div>

          <div className="coaching-box weaknesses-box">
            <h3>Needs Improvement</h3>
            <ul>
              {needsImprovement.map((w, i) => (
                <li key={i}><span className="x-icon">✗</span> {w}</li>
              ))}
            </ul>
          </div>

          <div className="coaching-box recommendation-box">
            <h3>Next Milestone</h3>
            <p>{recommendation}</p>
          </div>

        </div>

        {/* Right Column: Pillar Breakdown */}
        <div className="pillars-section">
          <h3>Pillar Breakdown</h3>
          <p className="pillars-desc">Your tier is calculated based heavily on your Discipline and Learning progress, not just profits.</p>
          
          <div className="pillar-row">
            <div className="pillar-label">
              <span>Discipline</span>
              <span className="weight-label">(40% Weight)</span>
            </div>
            <div className="pillar-bar-bg">
              <div className="pillar-bar-fill discipline-fill" style={{ width: `${disciplineScore}%` }}></div>
            </div>
          </div>

          <div className="pillar-row">
            <div className="pillar-label">
              <span>Learning</span>
              <span className="weight-label">(30% Weight)</span>
            </div>
            <div className="pillar-bar-bg">
              <div className="pillar-bar-fill learning-fill" style={{ width: `${learningScore}%` }}></div>
            </div>
          </div>

          <div className="pillar-row">
            <div className="pillar-label">
              <span>Trading Process</span>
              <span className="weight-label">(20% Weight)</span>
            </div>
            <div className="pillar-bar-bg">
              <div className="pillar-bar-fill process-fill" style={{ width: `${tradingScore}%` }}></div>
            </div>
          </div>

          <div className="pillar-row">
            <div className="pillar-label">
              <span>Results (PnL)</span>
              <span className="weight-label">(10% Weight)</span>
            </div>
            <div className="pillar-bar-bg">
              <div className="pillar-bar-fill results-fill" style={{ width: `${resultsScore}%` }}></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ReadinessDashboard;
