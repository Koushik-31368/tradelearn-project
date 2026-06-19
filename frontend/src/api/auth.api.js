/**
 * api/auth.api.js — Authentication API module
 *
 * All authentication-related HTTP calls: register, login, password reset.
 * Components should import from here instead of inlining fetch() calls.
 */
import apiClient from './client';

/**
 * Register a new user account.
 * @param {{ username: string, email: string, password: string }} data
 * @returns {Promise<{ token: string, user: object }>}
 */
export async function register(data) {
  const res = await apiClient.post('/api/auth/register', data);
  return res.data;
}

/**
 * Login with username/email and password.
 * @param {{ username: string, password: string }} data
 * @returns {Promise<{ token: string, user: object }>}
 */
export async function login(data) {
  const res = await apiClient.post('/api/auth/login', data);
  return res.data;
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
