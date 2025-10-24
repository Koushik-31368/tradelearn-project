import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
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
      const user = { username, email, password };
      const response = await axios.post('http://localhost:8080/api/auth/register', user);
      setMessage(response.data);
      
      setTimeout(() => {
        navigate('/login');
      }, 2000);

    } catch (error) {
      if (error.response) {
        setMessage(error.response.data);
      } else {
        setMessage(error.message);
      }
      setIsError(true);
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