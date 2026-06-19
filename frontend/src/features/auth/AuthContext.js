import React, { createContext, useContext, useEffect, useState, useCallback } from "react";

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    try {
      const raw = localStorage.getItem("tradelearn_user");
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  });

  const [token, setToken] = useState(() => {
    return localStorage.getItem("tradelearn_token") || null;
  });

  useEffect(() => {
    if (user) {
      localStorage.setItem("tradelearn_user", JSON.stringify(user));
    } else {
      localStorage.removeItem("tradelearn_user");
    }
  }, [user]);

  useEffect(() => {
    if (token) {
      localStorage.setItem("tradelearn_token", token);
    } else {
      localStorage.removeItem("tradelearn_token");
    }
  }, [token]);

  /**
   * Login — expects the full API response: { token, id, username, email, rating }
   */
  const login = useCallback((responseData) => {
    const { token: jwt, ...userObj } = responseData;
    setToken(jwt);
    setUser(userObj);
  }, []);

  const logout = useCallback(() => {
    setUser(null);
    setToken(null);
  }, []);

  const updateUser = useCallback((updates) => {
    setUser(prev => prev ? { ...prev, ...updates } : null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, token, isAuthenticated: !!user && !!token, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);