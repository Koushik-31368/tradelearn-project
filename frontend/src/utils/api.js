// src/utils/api.js
const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

export function backendUrl(path) {
  // ensure leading slash
  if (!path.startsWith("/")) path = "/" + path;
  return `${API_URL}${path}`;
}

// Derive WS base for SockJS: convert http(s) -> ws(s)
export function wsBase() {
  const url = API_URL;
  if (url.startsWith("https://")) return "wss://" + url.replace(/^https?:\/\//, "");
  if (url.startsWith("http://")) return "ws://" + url.replace(/^https?:\/\//, "");
  return url;
}

export default API_URL;
