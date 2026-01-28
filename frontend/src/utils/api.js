// src/utils/api.js
const API_URL = process.env.REACT_APP_API_URL;


if (!API_URL) {
console.error("REACT_APP_API_URL is not defined");
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


export default API_URL;