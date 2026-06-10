import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
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
import SimulatorPage from './pages/SimulatorPage'; // 1. Import
import PracticePage from './pages/PracticePage';
import LearnPage from './pages/LearnPage';
import StrategiesPage from './pages/StrategiesPage';
import MatchResultPage from './pages/MatchResultPage';
import LeaderboardPage from './pages/LeaderboardPage';
import ProfilePage from './pages/ProfilePage';
import MatchHistoryPage from './pages/MatchHistoryPage';
import TermsPage from './pages/TermsPage';
import PrivacyPage from './pages/PrivacyPage';
import RiskDisclosurePage from './pages/RiskDisclosurePage';
import Footer from './components/Footer';

const AUTH_PATHS = ['/login', '/register', '/forgot-password'];

function AppContent() {
  const location = useLocation();
  const hideTickerOnAuth = AUTH_PATHS.includes(location.pathname);

  return (
    <div className="App">
      <Navbar />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/multiplayer" element={<LobbyPage />} />
        <Route path="/game/:gameId" element={<GamePage />} />
        <Route path="/match/:gameId/result" element={<MatchResultPage />} />
        <Route path="/leaderboard" element={<LeaderboardPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/history" element={<MatchHistoryPage />} />
        <Route path="/learn" element={<LearnPage />} />
        <Route path="/strategies" element={<StrategiesPage />} />
        <Route path="/simulator" element={<SimulatorPage />} />
        <Route path="/practice" element={<PracticePage />} />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route path="/risk-disclosure" element={<RiskDisclosurePage />} />
      </Routes>
      {!hideTickerOnAuth && <StockTicker />}
      {!hideTickerOnAuth && <Footer />}
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </AuthProvider>
  );
}
export default App;