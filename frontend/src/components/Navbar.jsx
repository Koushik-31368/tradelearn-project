import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import TierBadge from './TierBadge';
import './Navbar.css';

const Navbar = () => {
  const { isAuthenticated, user, logout } = useAuth();

  return (
    <nav className="navbar">
      <div className="navbar-logo">
        <NavLink to="/">TradeLearn</NavLink>
      </div>
      <ul className="navbar-links">
        <li><NavLink to="/">Home</NavLink></li>
        <li><NavLink to="/learn">Learn</NavLink></li>
        <li><NavLink to="/strategies">Strategies</NavLink></li>
        <li><NavLink to="/simulator">Simulator</NavLink></li>
        <li><NavLink to="/practice">Practice</NavLink></li>
        <li><NavLink to="/multiplayer">Multiplayer</NavLink></li>
        <li><NavLink to="/leaderboard">Leaderboard</NavLink></li>
      </ul>
      <div className="navbar-login">
        {isAuthenticated ? (
          <>
            {user && <TierBadge rating={user.rating} />}
            <NavLink to="/profile" className="nav-profile-link">Profile</NavLink>
            <NavLink to="/history" className="nav-profile-link">History</NavLink>
            <button onClick={logout} className="logout-button">Logout</button>
          </>
        ) : (
          <NavLink to="/login" className="login-button">Login</NavLink>
        )}
      </div>
    </nav>
  );
};

export default Navbar;
