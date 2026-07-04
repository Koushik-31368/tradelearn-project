/**
 * api/auth.api.js — Authentication API module
 *
 * All authentication-related HTTP calls: register, login, refresh, logout.
 * Components should import from here instead of inlining fetch() calls.
 *
 * Token architecture (Issue 1):
 *   - login() and register() return { token, id, username, email, ... }
 *     where `token` is the short-lived access token to store in React state.
 *   - The backend simultaneously sets an httpOnly refresh-token cookie.
 *     The browser sends it automatically; JS never reads it directly.
 *   - refresh() posts to /api/auth/refresh.  The browser sends the cookie
 *     automatically.  Returns { token } with the new access token.
 *   - logout() posts to /api/auth/logout which clears the cookie server-side.
 */
import apiClient from './client';

/**
 * Register a new user account.
 * @param {{ username: string, email: string, password: string }} data
 * @returns {Promise<{ token: string, id: number, username: string, email: string, rating: number, xp: number, loginStreak: number }>}
 */
export async function register(data) {
  const res = await apiClient.post('/api/auth/register', data);
  return res.data;
}

/**
 * Login with email and password.
 * @param {{ email: string, password: string }} data
 * @returns {Promise<{ token: string, id: number, username: string, email: string, rating: number, xp: number, loginStreak: number }>}
 */
export async function login(data) {
  const res = await apiClient.post('/api/auth/login', data);
  return res.data;
}

/**
 * Exchange the httpOnly refresh-token cookie for a fresh access token.
 * The browser sends the cookie automatically (withCredentials: true on apiClient).
 * @returns {Promise<{ token: string }>}
 */
export async function refreshToken() {
  const res = await apiClient.post('/api/auth/refresh');
  return res.data;
}

/**
 * Log out — asks the backend to clear the httpOnly refresh cookie.
 * The caller (AuthContext.logout) also clears in-memory state.
 */
export async function logout() {
  await apiClient.post('/api/auth/logout');
}

/**
 * Request a password reset email.
 * @param {{ email: string }} data
 * @returns {Promise<{ message: string }>}
 */
export async function forgotPassword(data) {
  const res = await apiClient.post('/api/auth/forgot-password', data);
  return res.data;
}

/**
 * Confirm password reset with token.
 * @param {{ token: string, newPassword: string }} data
 */
export async function resetPassword(data) {
  const res = await apiClient.post('/api/auth/reset-password', data);
  return res.data;
}
