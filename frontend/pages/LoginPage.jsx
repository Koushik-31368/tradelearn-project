// src/pages/LoginPage.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
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
      const response = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      const responseText = await response.text();

      if (!response.ok) {
        throw new Error(responseText);
      }

      login(email);
      setMessage('Login Successful! Welcome back.');

      setTimeout(() => {
        navigate('/multiplayer');
      }, 1500);

    } catch (error) {
      setMessage(error.message);
      setIsError(true);
    }
  };

  return (
    <div className="auth-container">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2>Sign In</h2>
        <p className="form-description">Welcome back! Please enter your details.</p>
        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input type="email" id="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="form-group">
          <label htmlFor="password">Password</label>
          <input type="password" id="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </div>
        <div className="form-options">
          <Link to="/forgot-password" className="forgot-password">Forgot password?</Link>
        </div>
        <button type="submit" className="auth-button">Sign In</button>
        {message && <p className={`auth-message ${isError ? 'error' : 'success'}`}>{message}</p>}
        <p className="auth-switch">New user? <Link to="/register">Create an account</Link></p>
      </form>
    </div>
  );
};

export default LoginPage;