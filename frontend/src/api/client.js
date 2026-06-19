/**
 * api/client.js — Canonical Axios HTTP client
 *
 * Single source of truth for the configured axios instance.
 * All domain-specific API modules import `apiClient` from here.
 *
 * Features:
 *   - Reads REACT_APP_API_URL for the base URL (falls back to relative paths)
 *   - Request interceptor: attaches JWT Bearer token from localStorage
 *   - Response interceptor: clears auth and redirects to /login on 401
 */
import axios from 'axios';

const API_URL = process.env.REACT_APP_API_URL || '';

/**
 * Build a full URL for native fetch() callers that still use backendUrl().
 * Kept for backward-compat with feature components that haven't migrated to apiClient yet.
 */
export function backendUrl(path) {
  if (!path.startsWith('/')) path = '/' + path;
  return `${API_URL}${path}`;
}

/**
 * Derive the WebSocket base from the HTTP base URL.
 */
export function wsBase() {
  if (!API_URL) return '';
  if (API_URL.startsWith('https://'))
    return 'wss://' + API_URL.replace(/^https?:\/\//, '');
  if (API_URL.startsWith('http://'))
    return 'ws://' + API_URL.replace(/^https?:\/\//, '');
  return API_URL;
}

/** Read the stored JWT from localStorage. */
export function getToken() {
  return localStorage.getItem('tradelearn_token');
}

/** Return Authorization header object (empty if no token). */
export function authHeaders() {
  const token = getToken();
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

/** Return Content-Type + Authorization headers for JSON requests. */
export function jsonAuthHeaders() {
  return { 'Content-Type': 'application/json', ...authHeaders() };
}

/** Pre-configured axios instance — use this in all new API modules. */
const apiClient = axios.create({ baseURL: API_URL });

// ── Request interceptor: attach JWT ──────────────────────────────────────────
apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Response interceptor: handle 401 ─────────────────────────────────────────
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      const currentPath = window.location.pathname;
      if (currentPath !== '/login' && currentPath !== '/register') {
        localStorage.removeItem('tradelearn_token');
        localStorage.removeItem('tradelearn_user');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
