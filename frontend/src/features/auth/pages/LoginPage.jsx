// src/features/auth/pages/LoginPage.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import apiClient from '../../../api/client';
import { useAuth } from '../AuthContext';
import './AuthForm.css';

const CANDLES = [28,18,44,36,22,52,16,40,30,20,46,34,28,18,44,36,22,52,16,40,30,20,46,34];

const LoginPage = () => {
  const [email,     setEmail]     = useState('');
  const [password,  setPassword]  = useState('');
  const [message,   setMessage]   = useState('');
  const [isError,   setIsError]   = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    setIsError(false);
    setIsLoading(true);
    try {
      const res = await apiClient.post('/api/auth/login', { email, password });
      login(res.data);
      setMessage('Login successful!');
      setTimeout(() => navigate('/'), 800);
    } catch (err) {
      setIsError(true);
      const d = err?.response?.data;
      setMessage(typeof d === 'string' ? d : d?.error || d?.message || 'Login failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-page">
      {/* ── Left brand panel ── */}
      <div className="auth-brand">
        <div className="auth-brand__top">
          <div className="auth-brand-logo">Trade<span>Learn</span></div>
          <p className="auth-brand-tagline">
            Trade.<br /><em>Learn.</em><br />Rise.
          </p>
          <p className="auth-brand-sub">
            The only platform where every trade teaches you something.
            Compete in ranked battles, complete missions, and climb the leaderboard.
          </p>
          <div className="auth-brand-features">
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">🎯</span>
              5 historical market missions to complete
            </div>
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">⚔️</span>
              ELO-ranked head-to-head multiplayer
            </div>
            <div className="auth-brand-feat">
              <span className="auth-brand-feat__icon">📈</span>
              Real NIFTY data, no fake markets
            </div>
          </div>
        </div>
        <div className="auth-brand__bottom">
          <div className="auth-candles-track">
            <div className="auth-candles">
              {CANDLES.map((h, i) => (
                <div
                  key={i}
                  className={`auth-candle ${i % 3 === 1 ? 'red' : 'green'}`}
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
          <h2>Welcome Back</h2>
          <p className="auth-subtitle">Sign in to continue your trading journey</p>

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-field">
              <span className="field-icon">✉</span>
              <input
                id="login-email"
                type="email"
                placeholder="Email address"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>

            <div className="auth-field">
              <span className="field-icon">🔒</span>
              <input
                id="login-password"
                type="password"
                placeholder="Password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />
            </div>

            <div className="auth-forgot">
              <Link to="/forgot-password">Forgot password?</Link>
            </div>

            <button id="login-submit" type="submit" className="auth-btn" disabled={isLoading}>
              {isLoading ? <span className="auth-spinner" /> : 'Sign In →'}
            </button>
          </form>

          {message && (
            <p className={`auth-msg ${isError ? 'error' : 'success'}`}>{message}</p>
          )}

          <p className="auth-footer">
            New to TradeLearn?{' '}
            <Link to="/register">Create an account</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;