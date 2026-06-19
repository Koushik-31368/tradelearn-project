import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import TierBadge from '../../features/leaderboard/components/TierBadge';
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
        <li><NavLink to="/missions">Missions</NavLink></li>
        <li><NavLink to="/multiplayer">Multiplayer</NavLink></li>
        <li><NavLink to="/leaderboard">Leaderboard</NavLink></li>
      </ul>
      <div className="navbar-login">
        {isAuthenticated ? (
          <>
            {user && (
              <div className="nav-stats">
                <span className="nav-xp">🌟 {user.xp || 0} XP</span>
                <span className="nav-streak">🔥 {user.loginStreak || 0}</span>
                <TierBadge rating={user.rating} />
              </div>
            )}
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
