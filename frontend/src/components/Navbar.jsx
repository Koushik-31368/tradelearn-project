// src/components/Navbar.jsx
import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Navbar.css';

const Navbar = () => {
  const { isAuthenticated, logout } = useAuth();

  return (
    <nav className="navbar">
      <div className="navbar-logo">
        <NavLink to="/">TradeLearn</NavLink>
      </div>
      // ... other code
      <ul className="navbar-links">
        <li><NavLink to="/">Home</NavLink></li>
        <li><NavLink to="/simulator">Simulator</NavLink></li> {/* <-- Add this line */}
        <li><NavLink to="/multiplayer">Multiplayer</NavLink></li>
        <li><NavLink to="/strategies">Strategies</NavLink></li>
      </ul>
// ... rest of the code
      <ul className="navbar-links">
        <li><NavLink to="/">Home</NavLink></li>
        <li><NavLink to="/simulator">Simulator</NavLink></li>
        <li><NavLink to="/multiplayer">Multiplayer</NavLink></li>
        <li><NavLink to="/strategies">Strategies</NavLink></li>
      </ul>
      <div className="navbar-login">
        {isAuthenticated ? (
          <button onClick={logout} className="logout-button">Logout</button>
        ) : (
          <NavLink to="/login" className="login-button">Login</NavLink>
        )}
      </div>
    </nav>
  );
};

export default Navbar;