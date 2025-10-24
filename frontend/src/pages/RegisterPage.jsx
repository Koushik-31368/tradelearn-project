// src/pages/RegisterPage.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios'; // Import axios
import './AuthForm.css';

const RegisterPage = () => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [isError, setIsError] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    setIsError(false);

    try {
      const userDetails = { username, email, password };
      
      // --- FIX IS HERE: Using the live Railway HTTPS URL ---
      const response = await axios.post('https://tradelearn-project-production.up.railway.app/api/auth/register', userDetails);

      setMessage(response.data); 
      
      setTimeout(() => {
        navigate('/login');
      }, 2000);

    } catch (error) {
      setIsError(true);
      if (error.response && error.response.data) {
        // Handle database constraint errors, etc.
        setMessage(error.response.data.message || 'Registration failed');
      } else {
        setMessage('Failed to connect to the live server.');
      }
    }
  };

  return (
    <div className="auth-container">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2>Create an Account</h2>
        <p className="form-description">Join us to start your trading journey.</p>
        <div className="form-group">
          <label htmlFor="username">Username</label>
          <input type="text" id="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </div>
        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input type="email" id="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="form-group">
          <label htmlFor="password">Password</label>
          <input type="password" id="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </div>
        <button type="submit" className="auth-button">Create Account</button>
        {message && <p className={`auth-message ${isError ? 'error' : 'success'}`}>{message}</p>}
        <p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p>
      </form>
    </div>
  );
};

export default RegisterPage;