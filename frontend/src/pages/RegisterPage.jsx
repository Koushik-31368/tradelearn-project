// src/pages/RegisterPage.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { backendUrl } from '../utils/api';
import './AuthForm.css';

const RegisterPage = () => {
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [isError, setIsError] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    setIsError(false);

    try {
      const response = await axios.post(
        backendUrl('/api/auth/register'),
        { email, username, password }
      );

      login(response.data);
      setMessage('Account created!');
      setTimeout(() => navigate('/learn'), 1000);

    } catch (err) {
      setIsError(true);
      const errData = err?.response?.data;
      setMessage(
        typeof errData === 'string' ? errData : errData?.message || 'Registration failed'
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

          <div className="auth-candles-track">
            <div className="auth-candles">
              <div className="auth-candle green" style={{ height: 28 }} />
              <div className="auth-candle red"   style={{ height: 18 }} />
              <div className="auth-candle green" style={{ height: 44 }} />
              <div className="auth-candle green" style={{ height: 36 }} />
              <div className="auth-candle red"   style={{ height: 22 }} />
              <div className="auth-candle green" style={{ height: 52 }} />
              <div className="auth-candle red"   style={{ height: 16 }} />
              <div className="auth-candle green" style={{ height: 40 }} />
              <div className="auth-candle green" style={{ height: 30 }} />
              <div className="auth-candle red"   style={{ height: 20 }} />
              <div className="auth-candle green" style={{ height: 46 }} />
              <div className="auth-candle green" style={{ height: 34 }} />
              {/* duplicate for seamless loop */}
              <div className="auth-candle green" style={{ height: 28 }} />
              <div className="auth-candle red"   style={{ height: 18 }} />
              <div className="auth-candle green" style={{ height: 44 }} />
              <div className="auth-candle green" style={{ height: 36 }} />
              <div className="auth-candle red"   style={{ height: 22 }} />
              <div className="auth-candle green" style={{ height: 52 }} />
              <div className="auth-candle red"   style={{ height: 16 }} />
              <div className="auth-candle green" style={{ height: 40 }} />
              <div className="auth-candle green" style={{ height: 30 }} />
              <div className="auth-candle red"   style={{ height: 20 }} />
              <div className="auth-candle green" style={{ height: 46 }} />
              <div className="auth-candle green" style={{ height: 34 }} />
            </div>
          </div>
          <div className="auth-price-line" />
        </div>
      </div>

      {/* ── form panel ── */}
      <div className="auth-form-panel">
        <div className="auth-card">
          <h2>Create Account</h2>
          <p className="auth-subtitle">Start your trading journey today</p>

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
              <span className="field-icon">👤</span>
              <input
                type="text"
                placeholder="Username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
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

            <label className="auth-agree">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(e) => setAgreed(e.target.checked)}
              />
              <span>
                I agree to the{' '}
                <Link to="/terms" target="_blank" rel="noopener noreferrer">Terms of Service</Link>{' '}
                and{' '}
                <Link to="/privacy" target="_blank" rel="noopener noreferrer">Privacy Policy</Link>
              </span>
            </label>

            <button type="submit" className="auth-btn" disabled={!agreed}>
              Create Account
            </button>
          </form>

          {message && (
            <p className={`auth-msg ${isError ? 'error' : 'success'}`}>{message}</p>
          )}

          <p className="auth-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;