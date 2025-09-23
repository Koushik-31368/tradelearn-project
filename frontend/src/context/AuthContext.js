// src/context/AuthContext.js
import React, { createContext, useState, useContext } from 'react';

// Create the context
const AuthContext = createContext(null);

// Create the provider component
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null); // Will hold user data after login

  // Mock login function
  const login = (email) => {
    // In a real app, you'd get user details from the backend.
    // For now, we'll create a dummy user.
    const dummyUser = { email, name: 'Koushik' };
    setUser(dummyUser);
  };

  // Logout function
  const logout = () => {
    setUser(null);
  };

  const value = {
    user,
    isAuthenticated: !!user, // a user object exists, they are authenticated
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

// Create a custom hook to easily use the context
export const useAuth = () => {
  return useContext(AuthContext);
};