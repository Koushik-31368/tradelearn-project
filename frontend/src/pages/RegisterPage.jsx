import React, { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import axios from "axios";
import "./AuthForm.css";
import "./RegisterPage.css";
import { backendUrl } from '../utils/api';

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const navigate = useNavigate();

  const handleRegister = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");

    try {
      await axios.post(
        backendUrl('/api/auth/register'),
        {
          email,
          username,
          password,
        },
        {
          headers: {
            "Content-Type": "application/json",
          },
        }
      );

      setSuccess("Registration successful! Redirecting to login…");
      setTimeout(() => navigate("/login"), 1500);

    } catch (err) {
      setError(
        err.response?.data || "Registration failed. Server might be sleeping."
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
            Start your trading journey today. Practice with virtual money,
            learn from the best, and build confidence before going live.
          </p>

          {/* CSS-only candlestick chart */}
          <div className="auth-candles">
            <div className="auth-candle green" style={{ height: 40, animationDelay: '0s' }} />
            <div className="auth-candle green" style={{ height: 60, animationDelay: '.25s' }} />
            <div className="auth-candle red"   style={{ height: 34, animationDelay: '.5s' }} />
            <div className="auth-candle green" style={{ height: 78, animationDelay: '.75s' }} />
            <div className="auth-candle red"   style={{ height: 26, animationDelay: '1s' }} />
            <div className="auth-candle green" style={{ height: 90, animationDelay: '1.25s' }} />
            <div className="auth-candle green" style={{ height: 70, animationDelay: '1.5s' }} />
            <div className="auth-candle red"   style={{ height: 44, animationDelay: '1.75s' }} />
          </div>
          <div className="auth-price-line" />

          <ul className="auth-features">
            <li><span className="feat-icon">🚀</span> Zero-risk paper trading simulator</li>
            <li><span className="feat-icon">⚔️</span> Head-to-head multiplayer matches</li>
            <li><span className="feat-icon">📊</span> Track your performance over time</li>
          </ul>
        </div>
      </div>

      {/* ── form panel ── */}
      <div className="auth-form-panel">
        <div className="auth-card">
          <h2>Create Account</h2>
          <p className="auth-subtitle">Join thousands of aspiring traders</p>

          <form onSubmit={handleRegister}>
            <div className="auth-field">
              <span className="field-icon">✉</span>
              <input
                type="email"
                placeholder="Email address"
                value={email}
                required
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>

            <div className="auth-field">
              <span className="field-icon">👤</span>
              <input
                type="text"
                placeholder="Username"
                value={username}
                required
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>

            <div className="auth-field">
              <span className="field-icon">🔒</span>
              <input
                type="password"
                placeholder="Password"
                value={password}
                required
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            <button type="submit" className="auth-btn">Create Account</button>
          </form>

          {error && <p className="auth-msg error">{error}</p>}
          {success && <p className="auth-msg success">{success}</p>}

          <p className="auth-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}