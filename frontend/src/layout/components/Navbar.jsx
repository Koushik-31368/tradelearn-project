import React, { useState, useEffect, useCallback } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import TierBadge from '../../features/leaderboard/components/TierBadge';
import { backendUrl, authHeaders } from '../../api/api';
import './Navbar.css';

/**
 * Polls /api/social/friends every 30 s while the user is logged in
 * and returns the count of incoming pending requests, so the navbar badge
 * stays live without requiring a page refresh.
 */
function usePendingRequestCount(isAuthenticated) {
  const [count, setCount] = useState(0);

  const poll = useCallback(async () => {
    if (!isAuthenticated) { setCount(0); return; }
    try {
      const res = await fetch(backendUrl('/api/social/friends'), { headers: authHeaders() });
      if (res.ok) {
        const list = await res.json();
        setCount(list.filter(f => f.status === 'PENDING' && !f.isSender).length);
      }
    } catch { /* silent — badge just won't update */ }
  }, [isAuthenticated]);

  useEffect(() => {
    poll();
    const id = setInterval(poll, 30_000);
    return () => clearInterval(id);
  }, [poll]);

  return count;
}

const Navbar = () => {
  const { isAuthenticated, user, logout, isHydrating } = useAuth();
  const [scrolled, setScrolled] = useState(false);
  const pendingCount = usePendingRequestCount(isAuthenticated);

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
        {isAuthenticated && (
          <li>
            <NavLink to="/social" className="navbar-social-link">
              Social
              {pendingCount > 0 && (
                <span className="navbar-badge" aria-label={`${pendingCount} pending friend requests`}>
                  {pendingCount}
                </span>
              )}
            </NavLink>
          </li>
        )}
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
