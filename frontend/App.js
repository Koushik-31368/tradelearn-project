// src/App.js
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import './App.css';
import { AuthProvider } from './context/AuthContext';
import Navbar from './components/Navbar';
import StockTicker from './components/StockTicker';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import LobbyPage from './pages/LobbyPage';
import GamePage from './pages/GamePage'; // 1. Import the new page
import StrategiesPage from './pages/StrategiesPage';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <div className="App">
          <Navbar />
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/multiplayer" element={<LobbyPage />} />
            <Route path="/game/:gameId" element={<GamePage />} /> {/* 2. Add the new route */}
            <Route path="/strategies" element={<StrategiesPage />} />
          </Routes>
          <StockTicker />
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;