// src/utils/api.js
import axios from 'axios';

const API_URL = process.env.REACT_APP_API_URL || '';


if (!API_URL) {
console.warn("REACT_APP_API_URL is not defined – API calls will use relative paths");
}


export function backendUrl(path) {
if (!path.startsWith("/")) path = "/" + path;
return `${API_URL}${path}`;
}


export function wsBase() {
if (!API_URL) return "";
if (API_URL.startsWith("https://"))
return "wss://" + API_URL.replace(/^https?:\/\//, "");
if (API_URL.startsWith("http://"))
return "ws://" + API_URL.replace(/^https?:\/\//, "");
return API_URL;
}


/**
 * Get the stored JWT token from localStorage.
 */
export function getToken() {
return localStorage.getItem("tradelearn_token");
}


/**
 * Build Authorization headers for authenticated API requests.
 * Returns an object with the Authorization header if a token exists,
 * or an empty object if not authenticated.
 */
export function authHeaders() {
const token = getToken();
if (!token) return {};
return { Authorization: `Bearer ${token}` };
}


/**
 * Build a complete headers object for JSON requests with auth.
 */
export function jsonAuthHeaders() {
return {
  "Content-Type": "application/json",
  ...authHeaders(),
};
}


/**
 * Pre-configured axios instance that automatically attaches
 * the JWT Authorization header and handles 401 responses.
 */
export const api = axios.create({
  baseURL: API_URL,
});

// Request interceptor — attach JWT token
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — handle 401 (token expired/invalid)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Token expired or invalid — clear stored auth and redirect to login
      const currentPath = window.location.pathname;
      if (currentPath !== '/login' && currentPath !== '/register') {
        localStorage.removeItem("tradelearn_token");
        localStorage.removeItem("tradelearn_user");
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);


export default API_URL;