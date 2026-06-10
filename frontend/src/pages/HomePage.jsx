// src/pages/HomePage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { backendUrl } from '../utils/api';
import './HomePage.css';

/* ── Subtle SVG chart line for hero background ─────────── */
const HeroChart = () => (
  <div className="hp-hero-chart" aria-hidden="true">
    <svg viewBox="0 0 480 220" preserveAspectRatio="none">
      <polyline
        fill="none"
        stroke="#00ff88"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        points="
          0,180 30,170 60,160 90,140 120,150 150,120
          180,130 210,90 240,100 270,70 300,80 330,50
          360,60 390,40 420,55 450,30 480,20
        "
      />
    </svg>
  </div>
);

/* ── Section 2 data ────────────────────────────────────── */
const STEPS = [
  {
    num: 1,
    title: 'Learn Foundations',
    desc: 'Build core market knowledge through structured lessons on candlestick patterns, indicators, and risk management.',
  },
  {
    num: 2,
    title: 'Apply Real Strategies',
    desc: 'Practice proven trading strategies in a realistic simulator with live price action and scoring feedback.',
  },
  {
    num: 3,
    title: 'Compete in Ranked Matches',
    desc: 'Enter head-to-head matches where skill determines your ELO rating and leaderboard position.',
  },
];

/* ── Section 3 data ────────────────────────────────────── */
const FEATURES = [
  {
    title: 'Skill-Based Scoring',
    desc: 'Every match is scored on 60% Profit, 20% Risk Management, and 20% Accuracy — rewarding disciplined trading, not gambling.',
  },
  {
    title: 'Strategy-Driven Simulator',
    desc: 'Apply documented strategies against real market data. Decisions are graded, not just outcomes.',
  },
  {
    title: 'Competitive Ranking System',
    desc: 'ELO-based progression places you against equally skilled traders. Rise through tiers as your skill improves.',
  },
];

/* ── Component ─────────────────────────────────────────── */
const HomePage = () => {
  const navigate = useNavigate();
  const [metrics, setMetrics] = useState({
    totalMatches: 0,
    activeTraders: 0,
    totalTrades: 0,
    avgAccuracy: 0,
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(backendUrl('/api/users/leaderboard'));
        if (!res.ok) return;
        const data = await res.json();
        if (cancelled) return;

        const totalMatches = data.reduce((s, u) => s + (u.totalMatches || 0), 0);
        const totalTrades = data.reduce((s, u) => s + (u.totalTrades || 0), 0);
        const accuracies = data.filter(u => u.avgAccuracy > 0).map(u => u.avgAccuracy);
        const avgAccuracy =
          accuracies.length > 0
            ? Math.round(accuracies.reduce((a, b) => a + b, 0) / accuracies.length)
            : 0;

        setMetrics({
          totalMatches,
          activeTraders: data.length,
          totalTrades,
          avgAccuracy,
        });
      } catch {
        /* silent — metrics are non-critical */
      }
    })();
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="hp">
      {/* ── Section 1 — Hero ───────────────────────────── */}
      <section className="hp-hero">
        <div className="hp-inner">
          <div className="hp-hero-text">
            <h1 className="hp-hero-title">
              Master Market Skill Through Competition
            </h1>
            <p className="hp-hero-sub">Learn. Apply. Compete. Improve.</p>
            <div className="hp-hero-ctas">
              <button
                className="hp-btn-primary"
                onClick={() => navigate('/learn')}
              >
                Start Learning
              </button>
              <button
                className="hp-btn-secondary"
                onClick={() => navigate('/leaderboard')}
              >
                View Leaderboard
              </button>
            </div>
          </div>
          <HeroChart />
        </div>
      </section>

      {/* ── Section 2 — How it works ───────────────────── */}
      <section className="hp-how">
        <div className="hp-inner">
          <h2 className="hp-section-title">How TradeLearn Works</h2>
          <div className="hp-steps">
            {STEPS.map((s) => (
              <div key={s.num} className="hp-step">
                <span className="hp-step-num">{s.num}</span>
                <h3 className="hp-step-title">{s.title}</h3>
                <p className="hp-step-desc">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Section 3 — Why different ──────────────────── */}
      <section className="hp-why">
        <div className="hp-inner">
          <h2 className="hp-section-title">Why TradeLearn Is Different</h2>
          <div className="hp-features">
            {FEATURES.map((f) => (
              <div key={f.title} className="hp-feature">
                <h3 className="hp-feature-title">{f.title}</h3>
                <p className="hp-feature-desc">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Section 4 — Live platform metrics ──────────── */}
      <section className="hp-metrics">
        <div className="hp-inner">
          <h2 className="hp-section-title">Live Platform Metrics</h2>
          <div className="hp-stats">
            <div className="hp-stat">
              <p className="hp-stat-value">{metrics.totalMatches.toLocaleString()}</p>
              <p className="hp-stat-label">Total Matches Played</p>
            </div>
            <div className="hp-stat">
              <p className="hp-stat-value">{metrics.activeTraders.toLocaleString()}</p>
              <p className="hp-stat-label">Active Traders</p>
            </div>
            <div className="hp-stat">
              <p className="hp-stat-value">{metrics.totalTrades.toLocaleString()}</p>
              <p className="hp-stat-label">Total Trades Executed</p>
            </div>
            <div className="hp-stat">
              <p className="hp-stat-value">{metrics.avgAccuracy}%</p>
              <p className="hp-stat-label">Avg Accuracy</p>
            </div>
          </div>
        </div>
      </section>

      {/* ── Section 5 — Final CTA ──────────────────────── */}
      <section className="hp-final">
        <div className="hp-inner">
          <h2 className="hp-final-title">Ready to Compete Like a Professional?</h2>
          <button
            className="hp-btn-primary"
            onClick={() => navigate('/simulator')}
          >
            Enter Simulator
          </button>
        </div>
      </section>
    </div>
  );
};

export default HomePage;