import { BrowserRouter, Routes, Route } from 'react-router-dom';
import './App.css';
import { AuthProvider } from './context/AuthContext';
import Navbar from './components/Navbar';
import StockTicker from './components/StockTicker';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import LobbyPage from './pages/LobbyPage';
import GamePage from './pages/GamePage';
// ... other imports
import StrategiesPage from './pages/StrategiesPage';
import SimulatorPage from './pages/SimulatorPage'; // 1. Import

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
            <Route path="/game/:gameId" element={<GamePage />} />
            <Route path="/strategies" element={<StrategiesPage />} />
            <Route path="/simulator" element={<SimulatorPage />} /> {/* 2. Add route */}
          </Routes>
          <StockTicker />
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
}
export default App;