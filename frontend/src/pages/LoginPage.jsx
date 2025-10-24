// src/pages/LoginPage.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';
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
      // --- FIX IS HERE: Using the live Railway HTTPS URL ---
      const response = await axios.post('https://tradelearn-project-production.up.railway.app/api/auth/login', {
        email,
        password
      });
      
      login(response.data); 
      
      setMessage('Login Successful! Welcome back.');
      setTimeout(() => {
        navigate('/multiplayer');
      }, 1500);

    } catch (error) {
      setIsError(true);
      if (error.response && error.response.data) {
        setMessage(error.response.data.message || 'Login failed');
      } else {
        setMessage('Failed to connect to the live server.');
      }
    }
  };

  return (
    <div className="auth-container">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2>Sign In</h2>
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