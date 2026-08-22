import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import TierBadge from '../../features/leaderboard/components/TierBadge';
import './Navbar.css';

const Navbar = () => {
  const { isAuthenticated, user, logout, isHydrating } = useAuth();
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 60);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <nav className={`navbar${scrolled ? ' navbar--scrolled' : ''}`} aria-label="Main navigation">
      <div className="navbar-logo">
        <NavLink to="/">TradeLearn</NavLink>
      </div>
      <ul className="navbar-links">
        <li><NavLink to="/">Home</NavLink></li>
        <li><NavLink to="/missions">Missions</NavLink></li>
        <li><NavLink to="/simulator">Simulator</NavLink></li>
        <li><NavLink to="/multiplayer">Multiplayer</NavLink></li>
        <li><NavLink to="/leaderboard">Leaderboard</NavLink></li>
      </ul>
      <div className="navbar-login">
        {isHydrating ? null : isAuthenticated ? (
          <>
            {user && (
              <div className="nav-stats">
                <span className="nav-xp">XP {user.xp || 0}</span>
                <span className="nav-streak">STREAK {user.loginStreak || 0}</span>
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
