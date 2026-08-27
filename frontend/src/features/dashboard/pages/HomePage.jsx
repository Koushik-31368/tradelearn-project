// src/pages/HomePage.jsx
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { backendUrl } from '../../../api/client';
import DashboardPanel from '../components/DashboardPanel';
import bgImage from '../../../assets/background.jpg';
import './HomePage.css';

/* ── useCountUp hook ───────────────────────────────── */
function useCountUp(target, duration = 1200) {
  const [value, setValue] = useState(0);
  const raf = useRef(null);
  const startTs = useRef(null);
  const startVal = useRef(0);

  useEffect(() => {
    if (target === 0) { setValue(0); return; }
    startVal.current = 0;
    startTs.current = null;
    const tick = (ts) => {
      if (!startTs.current) startTs.current = ts;
      const elapsed = ts - startTs.current;
      const progress = Math.min(elapsed / duration, 1);
      // ease-out cubic
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(startVal.current + (target - startVal.current) * eased));
      if (progress < 1) raf.current = requestAnimationFrame(tick);
    };
    raf.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf.current);
  }, [target, duration]);

  return value;
}

/* Small signature mark reused across sections — a hairline rule with tick
   marks, echoing the price-axis ticks visible in the hero background photo. */
const AxisMark = ({ center }) => (
  <span className={`hp-axis${center ? ' hp-axis--center' : ''}`} aria-hidden="true" />
);

/* ── Section 2 data ────────────────────────────────────── */
const STEPS = [
  {
    num: '01',
    title: 'Learn Foundations',
    desc: 'Build core market knowledge through structured lessons on candlestick patterns, indicators, and risk management.',
  },
  {
    num: '02',
    title: 'Apply Real Strategies',
    desc: 'Practice proven trading strategies in a realistic simulator with live price action and scoring feedback.',
  },
  {
    num: '03',
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

  const { user, isHydrating } = useAuth();

  /* Animated counters — only start once metrics arrive */
  const animatedMatches  = useCountUp(metrics.totalMatches);
  const animatedTraders  = useCountUp(metrics.activeTraders);
  const animatedTrades   = useCountUp(metrics.totalTrades);
  const animatedAccuracy = useCountUp(metrics.avgAccuracy);

  // Wait for the silent /api/auth/refresh call to resolve before deciding
  // which view to show. Without this, a stale `user` object read from
  // localStorage briefly renders the logged-in dashboard (and fires its
  // data fetches) before we know whether the session is actually valid.
  if (isHydrating) {
    return (
      <div className="hp hp-loading">
        <div className="hp-loading-spinner" />
      </div>
    );
  }

  if (user) {
    return (
      <div className="hp">
        <section className="hp-hero hp-hero--logged-in" style={{ backgroundImage: `url(${bgImage})` }}>
          <div className="hp-hero-overlay"></div>
          <div className="hp-inner hp-hero-content" style={{ textAlign: 'center', justifyContent: 'center' }}>
            <div className="hp-hero-text">
              <h1 className="hp-hero-title">Welcome back, {user.username}</h1>
              <p className="hp-hero-sub">Ready to conquer the market today?</p>
            </div>
          </div>
        </section>
        <DashboardPanel user={user} />
      </div>
    );
  }

  return (
    <div className="hp">
      {/* ── Section 1 — Hero ───────────────────────────── */}
      <section className="hp-hero hp-hero--split" style={{ backgroundImage: `url(${bgImage})` }}>
        <div className="hp-hero-overlay"></div>
        <div className="hp-hero-panel hp-hero-panel--copy fade-up-enter">
          <span className="hp-eyebrow">TRADELEARN / OPEN OUTCRY</span>
          <span className="hp-hero-kicker">LIVE MARKET ROUNDS / SKILL OVER LUCK</span>
          <h1 className="hp-hero-title">Learn the tape.<br /><em>Beat the market.</em></h1>
          <p className="hp-hero-sub">Synchronized trading rounds turn market knowledge into a competitive skill. Learn, make the call, and see your decisions scored.</p>
          <div className="hp-hero-ctas">
            <button className="hp-btn-primary" onClick={() => navigate('/learn')}>Enter the pit</button>
            <button className="hp-btn-secondary" onClick={() => navigate('/leaderboard')}>See the board</button>
          </div>
          <div className="hp-hero-stamp">EST. 2024<br /><strong>TRADE / LEARN / RISE</strong></div>
        </div>
        <div className="hp-hero-panel hp-hero-panel--market fade-up-enter">
          <div className="hp-market-widget">
            <div className="hp-market-widget__top"><span>LIVE / ROUND 0042</span><b>CONNECTED</b></div>
            <div className="hp-market-widget__symbol"><strong>NIFTY 50</strong><span>₹22,418.75</span><i>+1.84%</i></div>
            <div className="hp-candle-chart" aria-label="Illustration of rising market candles">
              {[34, 48, 38, 64, 52, 78, 62, 88, 74, 96, 84, 108].map((height, index) => <span key={index} style={{ '--bar-height': `${height}px`, '--bar-delay': `${index * 70}ms` }} />)}
            </div>
            <div className="hp-market-widget__axis"><span>09:15</span><span>12:30</span><span>15:30</span></div>
            <div className="hp-market-widget__footer"><span>ROUND CLOSES IN <b>00:18</b></span><span>RISK / REWARD 1:3.2</span></div>
          </div>
          <div className="hp-market-tag">CALL YOUR TRADE</div>
        </div>
      </section>

      {/* ── Section 2 — How it works ───────────────────── */}
      <section className="hp-how">
        <div className="hp-inner">
          <span className="hp-eyebrow">PROCESS</span>
          <AxisMark />
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
          <span className="hp-eyebrow">DIFFERENTIATORS</span>
          <AxisMark />
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
          <span className="hp-eyebrow">PLATFORM METRICS</span>
          <AxisMark center />
          <h2 className="hp-section-title hp-section-title--center">Live Platform Metrics</h2>
          <div className="hp-stats">
            <div className="hp-stat">
              <p className="hp-stat-value">{animatedMatches.toLocaleString()}</p>
              <p className="hp-stat-label">Total Matches Played</p>
            </div>
            <div className="hp-stat">
              <p className="hp-stat-value">{animatedTraders.toLocaleString()}</p>
              <p className="hp-stat-label">Active Traders</p>
            </div>
            <div className="hp-stat">
              <p className="hp-stat-value">{animatedTrades.toLocaleString()}</p>
              <p className="hp-stat-label">Total Trades Executed</p>
            </div>
            <div className="hp-stat">
              <p className="hp-stat-value">{animatedAccuracy}%</p>
              <p className="hp-stat-label">Avg Accuracy</p>
            </div>
          </div>
        </div>
      </section>

      {/* ── Section 5 — Final CTA ──────────────────────── */}
      <section className="hp-final">
        <div className="hp-inner">
          <AxisMark center />
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
