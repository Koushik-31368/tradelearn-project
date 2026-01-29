import React, { useState } from "react";
import axios from "axios";
import "./RegisterPage.css";

const API_URL = process.env.REACT_APP_API_URL;

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const handleRegister = async (e) => {
    e.preventDefault();
    setError("");

    try {
      await axios.post(
        `${API_URL}/api/auth/register`,
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

      alert("Registration successful!");
      window.location.href = "/login";

    } catch (err) {
      setError(
        err.response?.data || "Registration failed. Server might be sleeping."
      );
    }
  };

  return (
    <div className="register-container">
      <h2>Create Account</h2>

      <form onSubmit={handleRegister}>
        <input
          type="email"
          placeholder="Email"
          value={email}
          required
          onChange={(e) => setEmail(e.target.value)}
        />

        <input
          type="text"
          placeholder="Username"
          value={username}
          required
          onChange={(e) => setUsername(e.target.value)}
        />

        <input
          type="password"
          placeholder="Password"
          value={password}
          required
          onChange={(e) => setPassword(e.target.value)}
        />

        <button type="submit">Create Account</button>
      </form>

      {error && <p className="error-text">{error}</p>}

      <p className="login-link">
        Already have an account? <a href="/login">Login</a>
      </p>
    </div>
  );
}