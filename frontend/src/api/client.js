/**
 * api/client.js — Canonical Axios HTTP client
 *
 * Token architecture (Issue 1):
 *   - Access token: short-lived, kept in React in-memory state (AuthContext).
 *     Injected into every request via the Authorization: Bearer header.
 *     NEVER stored in localStorage or cookies.
 *   - Refresh token: long-lived httpOnly Secure SameSite=None cookie set by the
 *     backend.  withCredentials: true causes the browser to send it automatically
 *     to /api/auth/refresh and /api/auth/logout.  No JS code ever reads it.
 *
 * Features:
 *   - withCredentials: true (so the httpOnly refresh cookie travels cross-domain)
 *   - Request interceptor: attaches in-memory access token as Authorization: Bearer
 *   - Response interceptor: on 401, attempts silent refresh via /api/auth/refresh;
 *     if that also fails, calls onLogout() and redirects to /login
 */
import axios from 'axios';

const API_URL = process.env.REACT_APP_API_URL || '';
const WS_URL = process.env.REACT_APP_WS_URL || '';

// ── URL helpers ──────────────────────────────────────────────────────────────

/**
 * Build a full URL for native fetch() callers that still use backendUrl().
 * Kept for backward-compat with feature components that haven't migrated to apiClient yet.
 */
export function backendUrl(path) {
  if (!path.startsWith('/')) path = '/' + path;
  return `${API_URL}${path}`;
}

/**
 * Derive the WebSocket base from the HTTP base URL, or use WS_URL if provided.
 */
export function wsBase() {
  if (WS_URL) return WS_URL;
  if (!API_URL) return '';
  if (API_URL.startsWith('https://'))
    return 'wss://' + API_URL.replace(/^https?:\/\//, '');
  if (API_URL.startsWith('http://'))
    return 'ws://' + API_URL.replace(/^https?:\/\//, '');
  
  // Fallback for relative API_URL (e.g. '/' or '')
  const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
  return protocol + window.location.host;
}

// ── In-memory access token (replaced localStorage) ───────────────────────────

/**
 * In-memory access token store.
 * This is the ONLY place the access token lives in the browser.
 * It is lost on page refresh — that is intentional; AuthContext
 * re-hydrates it via /api/auth/refresh on mount.
 */
let _accessToken = null;

/** Called by AuthContext to set the token after login / refresh. */
export function setToken(token) {
  _accessToken = token || null;
}

/** Read the current in-memory access token. */
export function getToken() {
  return _accessToken;
}

/**
 * @deprecated  Use getToken() — kept for files that haven't migrated yet.
 * Will always return null (localStorage is no longer used).
 */
export function authHeaders() {
  const token = getToken();
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

/** @deprecated — use authHeaders() */
export function jsonAuthHeaders() {
  return { 'Content-Type': 'application/json', ...authHeaders() };
}

// ── Axios instance ───────────────────────────────────────────────────────────

/**
 * Pre-configured axios instance — use this in all API modules.
 *
 * withCredentials: true  →  browser sends the httpOnly refresh cookie to
 *                           /api/auth/refresh and /api/auth/logout (cross-domain).
 *                           CORS must have allowCredentials(true) + explicit origins.
 */
const apiClient = axios.create({
  baseURL: API_URL,
  withCredentials: true,   // send httpOnly cookie to /api/auth/* cross-domain
});

// ── Logout callback registry (set by AuthContext) ────────────────────────────

let _onLogout = null;

/**
 * AuthContext calls this on mount so the interceptor can trigger React logout
 * without importing AuthContext (which would create a circular dep).
 */
export function registerLogoutCallback(fn) {
  _onLogout = fn;
}

// ── Request interceptor: attach in-memory access token ───────────────────────

apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Response interceptor: silent refresh on 401 ──────────────────────────────

let _isRefreshing = false;
let _pendingQueue = [];   // requests queued while refresh is in flight

function processQueue(error, token = null) {
  _pendingQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  _pendingQueue = [];
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Only attempt refresh for 401s that haven't already been retried,
    // and only when we actually had an access token (not public endpoints).
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/api/auth/')
    ) {
      if (_isRefreshing) {
        // Queue this request until the in-flight refresh completes
        return new Promise((resolve, reject) => {
          _pendingQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        }).catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      _isRefreshing = true;

      try {
        // POST to /api/auth/refresh — browser sends the httpOnly cookie automatically
        const res = await apiClient.post('/api/auth/refresh');
        const newToken = res.data.token;
        setToken(newToken);
        processQueue(null, newToken);
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        setToken(null);
        // Notify AuthContext so it can clear React state and redirect
        if (_onLogout) _onLogout();
        return Promise.reject(refreshError);
      } finally {
        _isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
