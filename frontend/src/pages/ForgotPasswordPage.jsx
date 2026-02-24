// src/pages/ForgotPasswordPage.jsx
import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import './AuthForm.css';

const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [isSubmitted, setIsSubmitted] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log('Password reset request for:', email);
    setIsSubmitted(true);
  };

  return (
    <div className="auth-page">
      {/* ── branded left panel ── */}
      <div className="auth-brand">
        <div className="auth-brand-inner">
          <div className="auth-brand-logo">Trade<span>Learn</span></div>
          <p className="auth-brand-tagline">
            Don't worry — we'll help you get back into your account in no time.
          </p>

          <div className="auth-candles">
            <div className="auth-candle green" style={{ height: 52, animationDelay: '0s' }} />
            <div className="auth-candle red"   style={{ height: 30, animationDelay: '.4s' }} />
            <div className="auth-candle green" style={{ height: 68, animationDelay: '.8s' }} />
            <div className="auth-candle green" style={{ height: 44, animationDelay: '1.2s' }} />
            <div className="auth-candle red"   style={{ height: 36, animationDelay: '1.6s' }} />
            <div className="auth-candle green" style={{ height: 80, animationDelay: '2s' }} />
          </div>
          <div className="auth-price-line" />

          <ul className="auth-features">
            <li><span className="feat-icon">🔐</span> Secure password reset via email</li>
            <li><span className="feat-icon">⚡</span> Back to trading in seconds</li>
          </ul>
        </div>
      </div>

      {/* ── form panel ── */}
      <div className="auth-form-panel">
        <div className="auth-card">
          {!isSubmitted ? (
            <>
              <h2>Reset Password</h2>
              <p className="auth-subtitle">
                Enter your email and we'll send you a reset link.
              </p>

              <form onSubmit={handleSubmit}>
                <div className="auth-field">
                  <span className="field-icon">✉</span>
                  <input
                    type="email"
                    placeholder="Email address"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>

                <button type="submit" className="auth-btn">Send Reset Link</button>
              </form>
            </>
          ) : (
            <div style={{ textAlign: 'center' }}>
              <h2>Check Your Email</h2>
              <p className="auth-subtitle" style={{ marginBottom: 0 }}>
                If an account with that email exists, we've sent a password
                reset link to it.
              </p>
            </div>
          )}

          <p className="auth-footer">
            <Link to="/login">Back to Sign In</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;