// src/components/TopTraders.jsx
import React, { useState, useEffect } from 'react';
import { backendUrl } from '../utils/api';
import './TopTraders.css';

const MEDAL = ['🥇', '🥈', '🥉'];

const TopTraders = () => {
  const [traders, setTraders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(backendUrl('/api/users/leaderboard'));
        if (!res.ok) throw new Error('Failed to load leaderboard');
        const data = await res.json();
        setTraders(data.slice(0, 5)); // top 5
      } catch (err) {
        console.error('TopTraders fetch error:', err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <section className="top-traders-section">
      <h2>Top Traders</h2>
      <div className="traders-container">
        {loading ? (
          <p>Loading…</p>
        ) : traders.length === 0 ? (
          <p>No traders yet. Be the first!</p>
        ) : (
          traders.map((t, i) => (
            <div key={t.userId} className="trader-card">
              <h3>{MEDAL[i] || `#${i + 1}`} {t.username}</h3>
              <p className="profit">{t.rating} ELO</p>
              <p className="matches">{t.totalMatches} matches</p>
            </div>
          ))
        )}
      </div>
    </section>
  );
};

export default TopTraders;