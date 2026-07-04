/**
 * features/auth/AuthContext.js
 *
 * Token architecture (Issue 1):
 *   - The access token (short-lived JWT) is held ONLY in React state.
 *     It is never written to localStorage or sessionStorage.
 *   - The refresh token (long-lived JWT) lives in an httpOnly Secure
 *     SameSite=None cookie set by the backend.  JS can't read it.
 *   - On every page load, AuthContext silently calls /api/auth/refresh to
 *     rehydrate the in-memory access token from the cookie. If the cookie
 *     has expired or is absent, the user is treated as logged out.
 *   - The Axios client (client.js) registers the logout callback here so
 *     a 401 that survives the silent refresh also clears React state.
 */
import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
} from 'react';
import apiClient, { setToken, registerLogoutCallback } from '../../api/client';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  // ── user profile (non-sensitive, safe to store) ──────────────────────────
  const [user, setUser] = useState(() => {
    try {
      const raw = localStorage.getItem('tradelearn_user');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  });

  // ── access token lives in React state only ───────────────────────────────
  // undefined = not yet checked (hydration pending)
  // null      = checked, no valid token (logged out)
  // string    = valid access token
  const [accessToken, setAccessToken] = useState(undefined);

  // Guard: prevent running the silent refresh more than once per mount
  const hydratedRef = useRef(false);

  // ── persist non-sensitive profile to localStorage ────────────────────────
  useEffect(() => {
    if (user) {
      localStorage.setItem('tradelearn_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('tradelearn_user');
    }
  }, [user]);

  // ── sync access token to the Axios client module ────────────────────────
  useEffect(() => {
    setToken(accessToken ?? null);
  }, [accessToken]);

  // ── logout helper ─────────────────────────────────────────────────────────
  const logout = useCallback(async () => {
    try {
      // Ask backend to clear the httpOnly cookie
      await apiClient.post('/api/auth/logout');
    } catch {
      // Ignore — cookie will expire on its own
    }
    setAccessToken(null);
    setUser(null);
    setToken(null);
    localStorage.removeItem('tradelearn_user');
  }, []);

  // ── register logout callback so client.js can trigger it on 401 ──────────
  useEffect(() => {
    registerLogoutCallback(logout);
  }, [logout]);

  // ── silent refresh on mount (rehydrate from httpOnly cookie) ─────────────
  useEffect(() => {
    if (hydratedRef.current) return;
    hydratedRef.current = true;

    apiClient
      .post('/api/auth/refresh')
      .then((res) => {
        const token = res.data?.token;
        if (token) {
          setAccessToken(token);
        } else {
          setAccessToken(null);
        }
      })
      .catch(() => {
        // No valid cookie — user needs to log in
        setAccessToken(null);
        setUser(null);
      });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── login — called by LoginPage after a successful /api/auth/login ────────
  /**
   * Expects the full API response body: { token, id, username, email, rating, ... }
   * The access token goes into state; everything else is the user profile.
   */
  const login = useCallback((responseData) => {
    const { token: jwt, ...userObj } = responseData;
    setAccessToken(jwt);
    setUser(userObj);
    localStorage.setItem('tradelearn_user', JSON.stringify(userObj));
  }, []);

  // ── updateUser — for in-place profile updates (XP, rating, etc.) ─────────
  const updateUser = useCallback((updates) => {
    setUser((prev) => {
      const next = prev ? { ...prev, ...updates } : null;
      if (next) {
        localStorage.setItem('tradelearn_user', JSON.stringify(next));
      }
      return next;
    });
  }, []);

  // ── derived: isAuthenticated ──────────────────────────────────────────────
  // undefined while hydration is pending — callers can show a loading spinner
  const isAuthenticated =
    accessToken === undefined ? undefined : !!accessToken && !!user;

  return (
    <AuthContext.Provider
      value={{
        user,
        token: accessToken,          // expose as `token` for backward compat
        isAuthenticated,
        isHydrating: accessToken === undefined,
        login,
        logout,
        updateUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);