// src/features/errors/pages/NotFoundPage.jsx
import React from 'react';
import { useNavigate } from 'react-router-dom';
import './NotFoundPage.css';

const NotFoundPage = () => {
  const navigate = useNavigate();

  return (
    <div className="nf-page">
      <div className="nf-inner">
        {/* Decorative candle chart */}
        <div className="nf-candles" aria-hidden="true">
          {[30, 55, 20, 70, 40, 85, 15, 60].map((h, i) => (
            <span
              key={i}
              className={`nf-candle ${i % 3 === 0 ? 'nf-candle--red' : 'nf-candle--green'}`}
              style={{ '--h': `${h}px`, '--delay': `${i * 80}ms` }}
            />
          ))}
        </div>

        <div className="nf-code">404</div>
        <h1 className="nf-title">Page Not Found</h1>
        <p className="nf-desc">
          This route does not exist — much like a trade with no thesis.<br />
          Head back to safety.
        </p>

        <div className="nf-actions">
          <button className="nf-btn-primary" onClick={() => navigate('/')}>
            Go Home
          </button>
          <button className="nf-btn-secondary" onClick={() => navigate(-1)}>
            Go Back
          </button>
        </div>
      </div>
    </div>
  );
};

export default NotFoundPage;
