// src/pages/LoginPage.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { backendUrl } from '../utils/api';
import './AuthForm.css';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [isError, setIsError] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    setIsError(false);

    try {
      const response = await axios.post(
        backendUrl('/api/auth/login'),
        { email, password }
      );

      login(response.data);
      setMessage('Login successful!');
      setTimeout(() => navigate('/multiplayer'), 1000);

    } catch (err) {
      setIsError(true);
      const errData = err?.response?.data;
      setMessage(
        typeof errData === 'string' ? errData : errData?.message || 'Login failed'
      );
    }
  };

  return (
    <div className="auth-page">
      {/* ── branded left panel ── */}
      <div className="auth-brand">
        <div className="auth-brand-inner">
          <div className="auth-brand-logo">Trade<span>Learn</span></div>
          <p className="auth-brand-tagline">
            Practice trading. Compete with friends. Learn real strategies.
          </p>

          <div className="auth-candles">
            <div className="auth-candle green" style={{ height: 28, animationDelay: '0s' }} />
            <div className="auth-candle red"   style={{ height: 18, animationDelay: '.4s' }} />
            <div className="auth-candle green" style={{ height: 44, animationDelay: '.8s' }} />
            <div className="auth-candle green" style={{ height: 36, animationDelay: '1.2s' }} />
            <div className="auth-candle red"   style={{ height: 22, animationDelay: '1.6s' }} />
            <div className="auth-candle green" style={{ height: 52, animationDelay: '2s' }} />
          </div>
          <div className="auth-price-line" />
        </div>
      </div>

      {/* ── form panel ── */}
      <div className="auth-form-panel">
        <div className="auth-card">
          <h2>Welcome Back</h2>
          <p className="auth-subtitle">Sign in to continue your trading journey</p>

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

            <div className="auth-field">
              <span className="field-icon">🔒</span>
              <input
                type="password"
                placeholder="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            <div className="auth-forgot">
              <Link to="/forgot-password">Forgot password?</Link>
            </div>

            <button type="submit" className="auth-btn">Sign In</button>
          </form>

          {message && (
            <p className={`auth-msg ${isError ? 'error' : 'success'}`}>{message}</p>
          )}

          <p className="auth-footer">
            New to TradeLearn? <Link to="/register">Create an account</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;