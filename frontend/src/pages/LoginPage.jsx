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
    <div className="auth-container">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2>Sign In</h2>

        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <button type="submit">Sign In</button>

        {message && (
          <p className={isError ? 'error' : 'success'}>{message}</p>
        )}

        <p>
          New user? <Link to="/register">Create account</Link>
        </p>
      </form>
    </div>
  );
};

export default LoginPage;