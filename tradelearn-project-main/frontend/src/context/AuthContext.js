// src/context/AuthContext.js
import React, { createContext, useContext, useEffect, useState } from "react";

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  // Try to load user from localStorage (persisted login)
  const [user, setUser] = useState(() => {
    try {
      const raw = localStorage.getItem("tradelearn_user");
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      console.error("Failed to parse stored user", e);
      return null;
    }
  });

  useEffect(() => {
    if (user) localStorage.setItem("tradelearn_user", JSON.stringify(user));
    else localStorage.removeItem("tradelearn_user");
  }, [user]);

  const login = (userObj) => {
    // Expect a full object: { id, username, email } from backend
    setUser(userObj);
  };

  const logout = () => {
    setUser(null);
  };

  const value = {
    user,
    isAuthenticated: !!user,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => useContext(AuthContext);
