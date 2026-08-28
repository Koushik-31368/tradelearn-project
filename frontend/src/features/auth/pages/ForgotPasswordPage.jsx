// src/features/auth/pages/ForgotPasswordPage.jsx
import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import './AuthForm.css';

const CANDLES = [18,30,22,14,26,40,18,32,24,16,28,20,18,30,22,14,26,40,18,32,24,16,28,20];

const ForgotPasswordPage = () => {
  const [email,       setEmail]       = useState('');
  const [isSubmitted, setIsSubmitted] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    setIsSubmitted(true);
  };

  return (
    <div className="auth-page">
      {/* ── Left brand panel ── */}
      <div className="auth-brand">
        <div className="auth-brand__top">
          <div className="auth-brand-logo">Trade<span>Learn</span></div>
          <p className="auth-brand-tagline">
            We've got<br />you <em>covered.</em>
          </p>
          <p className="auth-brand-sub">
            Don't worry — it happens to everyone. Enter your email and we'll
            send you a secure link to reset your password.
          </p>
          <div className="auth-brand-features">
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">🔐</span>
              Secure password reset link
            </div>
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">📧</span>
              Email delivered in under 60 seconds
            </div>
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">🛡️</span>
              Link expires after 15 minutes
            </div>
          </div>
        </div>
        <div className="auth-brand__bottom">
          <div className="auth-candles-track">
            <div className="auth-candles">
              {CANDLES.map((h, i) => (
                <div
                  key={i}
                  className={`auth-candle ${i % 5 === 3 ? 'red' : 'green'}`}
                  style={{ height: h }}
                />
              ))}
            </div>
          </div>
          <div className="auth-price-line" />
        </div>
      </div>

      {/* ── Right form panel ── */}
      <div className="auth-form-panel">
        <div className="auth-card">
          {!isSubmitted ? (
            <>
              <h2>Reset Password</h2>
              <p className="auth-subtitle">
                Enter your email and we'll send you a reset link.
              </p>

              <form className="auth-form" onSubmit={handleSubmit}>
                <div className="auth-field">
                  <span className="field-icon">✉</span>
                  <input
                    id="forgot-email"
                    type="email"
                    placeholder="Email address"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    required
                    autoComplete="email"
                  />
                </div>

                <button id="forgot-submit" type="submit" className="auth-btn">
                  Send Reset Link →
                </button>
              </form>
            </>
          ) : (
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: '3rem', marginBottom: 16 }}>📬</div>
              <h2>Check Your Email</h2>
              <p className="auth-subtitle" style={{ marginBottom: 0, textAlign: 'center' }}>
                If an account with <strong style={{ color: '#9b6dff' }}>{email}</strong> exists,
                we've sent a password reset link to it. Check your spam folder if you don't see it.
              </p>
            </div>
          )}

          <p className="auth-footer">
            <Link to="/login" className="auth-back">← Back to Sign In</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;