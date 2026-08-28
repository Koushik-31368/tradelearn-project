// src/features/auth/pages/RegisterPage.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import apiClient from '../../../api/client';
import { useAuth } from '../AuthContext';
import './AuthForm.css';

const CANDLES = [22,40,28,52,18,44,32,60,24,36,48,20,22,40,28,52,18,44,32,60,24,36,48,20];

const RegisterPage = () => {
  const [email,     setEmail]     = useState('');
  const [username,  setUsername]  = useState('');
  const [password,  setPassword]  = useState('');
  const [message,   setMessage]   = useState('');
  const [isError,   setIsError]   = useState(false);
  const [agreed,    setAgreed]    = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    setIsError(false);
    try {
      const res = await apiClient.post('/api/auth/register', { email, username, password });
      login(res.data);
      setMessage('Account created! Welcome to TradeLearn 🎉');
      setTimeout(() => navigate('/'), 1000);
    } catch (err) {
      setIsError(true);
      const d = err?.response?.data;
      setMessage(typeof d === 'string' ? d : d?.error || d?.message || 'Registration failed');
    }
  };

  return (
    <div className="auth-page">
      {/* ── Left brand panel ── */}
      <div className="auth-brand">
        <div className="auth-brand__top">
          <div className="auth-brand-logo">Trade<span>Learn</span></div>
          <p className="auth-brand-tagline">
            Join the<br /><em>Arena.</em>
          </p>
          <p className="auth-brand-sub">
            Create your free account and start competing in ranked trading battles,
            completing missions, and building real market intuition.
          </p>
          <div className="auth-brand-features">
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">🚀</span>
              Free to join — no credit card needed
            </div>
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">🏆</span>
              Start at Bronze, climb to Grandmaster
            </div>
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">📊</span>
              Learn from every trade you make
            </div>
          </div>
        </div>
        <div className="auth-brand__bottom">
          <div className="auth-candles-track">
            <div className="auth-candles">
              {CANDLES.map((h, i) => (
                <div
                  key={i}
                  className={`auth-candle ${i % 4 === 2 ? 'red' : 'green'}`}
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
        <div className={`auth-card${isError ? ' auth-card--shake' : ''}`}>
          <h2>Create Account</h2>
          <p className="auth-subtitle">Start your trading journey today — it's free</p>

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-field">
              <span className="field-icon">✉</span>
              <input
                id="reg-email"
                type="email"
                placeholder="Email address"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>

            <div className="auth-field">
              <span className="field-icon">👤</span>
              <input
                id="reg-username"
                type="text"
                placeholder="Username (shown on leaderboard)"
                value={username}
                onChange={e => setUsername(e.target.value)}
                required
                autoComplete="username"
              />
            </div>

            <div className="auth-field">
              <span className="field-icon">🔒</span>
              <input
                id="reg-password"
                type="password"
                placeholder="Password (min 8 characters)"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                autoComplete="new-password"
                minLength={8}
              />
            </div>

            <label className="auth-agree">
              <input
                id="reg-agree"
                type="checkbox"
                checked={agreed}
                onChange={e => setAgreed(e.target.checked)}
              />
              <span>
                I agree to the{' '}
                <Link to="/terms" target="_blank" rel="noopener noreferrer">Terms of Service</Link>
                {' '}and{' '}
                <Link to="/privacy" target="_blank" rel="noopener noreferrer">Privacy Policy</Link>
              </span>
            </label>

            <button id="reg-submit" type="submit" className="auth-btn" disabled={!agreed}>
              Create Account →
            </button>
          </form>

          {message && (
            <p className={`auth-msg ${isError ? 'error' : 'success'}`}>{message}</p>
          )}

          <p className="auth-footer">
            Already have an account?{' '}
            <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;