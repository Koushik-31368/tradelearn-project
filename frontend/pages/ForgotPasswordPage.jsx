// src/pages/ForgotPasswordPage.jsx
import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import './AuthForm.css';

const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [isSubmitted, setIsSubmitted] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    // For now, we'll just simulate the action.
    // The real backend logic will be added later.
    console.log('Password reset request for:', email);
    setIsSubmitted(true);
  };

  return (
    <div className="auth-container">
      <div className="auth-form">
        {/* We use conditional rendering to show the form or a success message */}
        {!isSubmitted ? (
          <form onSubmit={handleSubmit}>
            <h2>Reset Password</h2>
            <p className="form-description">
              Enter your email address and we will send you a link to reset your password.
            </p>
            <div className="form-group">
              <label htmlFor="email">Email</label>
              <input 
                type="email" 
                id="email" 
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required 
              />
            </div>
            <button type="submit" className="auth-button">Send Reset Link</button>
          </form>
        ) : (
          <div className="success-message">
            <h2>Check Your Email</h2>
            <p>If an account with that email exists, we have sent a password reset link to it.</p>
          </div>
        )}
        
        <p className="auth-switch">
          <Link to="/login">Back to Sign In</Link>
        </p>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;