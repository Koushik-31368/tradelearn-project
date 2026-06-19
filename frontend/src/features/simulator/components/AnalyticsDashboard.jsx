// src/components/simulator/AnalyticsDashboard.jsx
import React, { useState, useEffect } from 'react';
import './AnalyticsDashboard.css';

const AnalyticsDashboard = ({ userId = 1 }) => {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAnalytics = async () => {
      try {
        const res = await fetch(`/api/analytics/user/${userId}`);
        if (res.ok) {
          const data = await res.json();
          setAnalytics(data);
        }
      } catch (err) {
        console.error("Failed to load analytics", err);
      } finally {
        setLoading(false);
      }
    };
    fetchAnalytics();
  }, [userId]);

  if (loading) {
    return <div className="analytics-loading">Loading Analytics...</div>;
  }

  if (!analytics) {
    return <div className="analytics-error">Could not load analytics. Place some trades first!</div>;
  }

  const {
    disciplineScore,
    learningScore,
    tradingScore,
    metrics,
    thesisAnalytics,
    strongestStrategy,
    weakestStrategy
  } = analytics;

  return (
    <div className="analytics-dashboard">
      <h2 className="analytics-header">Performance Analytics</h2>

      {/* Primary Scores Row */}
      <div className="analytics-scores-row">
        <div className="analytics-score-card primary-score">
          <h3>Discipline Score</h3>
          <div className="score-value">{disciplineScore}</div>
          <div className="score-subtitle">Process & Risk Rules</div>
        </div>
        <div className="analytics-score-card secondary-score">
          <h3>Learning Score</h3>
          <div className="score-value">{learningScore}</div>
          <div className="score-subtitle">Path Progress</div>
        </div>
        <div className="analytics-score-card tertiary-score">
          <h3>Trading Score</h3>
          <div className="score-value">{tradingScore}</div>
          <div className="score-subtitle">Market Outcomes</div>
        </div>
      </div>

      <div className="analytics-details-grid">
        {/* Discipline Details */}
        <div className="analytics-section">
          <h3 className="section-title">Discipline Breakdown</h3>
          <div className="metric-row">
            <span>Journal Compliance</span>
            <span>{metrics.journalCompliance.toFixed(0)}%</span>
          </div>
          <div className="metric-row">
            <span>Stop Loss Usage</span>
            <span>{metrics.stopLossUsage.toFixed(0)}%</span>
          </div>
          <div className="metric-row">
            <span>Reflection Rate</span>
            <span>{metrics.reflectionRate.toFixed(0)}%</span>
          </div>
          <div className="metric-row">
            <span>Average Risk / Trade</span>
            <span style={{ color: metrics.avgRisk > 5 ? '#ff4444' : '#00C851' }}>
              {metrics.avgRisk.toFixed(2)}%
            </span>
          </div>
        </div>

        {/* Trading Details */}
        <div className="analytics-section">
          <h3 className="section-title">Trading Outcomes</h3>
          <div className="metric-row">
            <span>Win Rate</span>
            <span>{metrics.winRate.toFixed(1)}%</span>
          </div>
          <div className="metric-row">
            <span>Profit Factor</span>
            <span>{metrics.profitFactor.toFixed(2)}</span>
          </div>
          <div className="metric-row">
            <span>Strongest Setup</span>
            <span className="setup-badge">{strongestStrategy}</span>
          </div>
          <div className="metric-row">
            <span>Weakest Setup</span>
            <span className="setup-badge weak">{weakestStrategy}</span>
          </div>
        </div>
      </div>

      {/* Thesis Breakdown */}
      <div className="analytics-section thesis-breakdown">
        <h3 className="section-title">Thesis Performance</h3>
        {thesisAnalytics.length > 0 ? (
          <table className="thesis-table">
            <thead>
              <tr>
                <th>Strategy Category</th>
                <th>Total Trades</th>
                <th>Win Rate</th>
              </tr>
            </thead>
            <tbody>
              {thesisAnalytics.map((t, i) => (
                <tr key={i}>
                  <td>{t.category}</td>
                  <td>{t.totalTrades}</td>
                  <td>
                    <div className="progress-bar-bg">
                      <div className="progress-bar-fill" style={{ width: `${t.winRate}%`, backgroundColor: t.winRate >= 50 ? '#00C851' : '#ff4444' }}></div>
                      <span className="progress-label">{t.winRate.toFixed(1)}%</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty-state">No trades journaled yet.</div>
        )}
      </div>
    </div>
  );
};

export default AnalyticsDashboard;
