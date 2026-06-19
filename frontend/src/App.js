import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import { AuthProvider } from './features/auth/AuthContext';
import Navbar from './layout/components/Navbar';
import StockTicker from './layout/components/StockTicker';
import HomePage from './features/dashboard/pages/HomePage';
import LoginPage from './features/auth/pages/LoginPage';
import RegisterPage from './features/auth/pages/RegisterPage';
import ForgotPasswordPage from './features/auth/pages/ForgotPasswordPage';
import LobbyPage from './features/matchmaking/pages/LobbyPage';
import GamePage from './features/game/pages/GamePage';
import SimulatorPage from './features/simulator/pages/SimulatorPage';
import PracticePage from './features/practice/pages/PracticePage';
import MissionSelectionPage from './features/simulator/pages/MissionSelectionPage';
import MissionDashboard from './features/simulator/components/MissionDashboard';
import StrategiesPage from './features/strategies/pages/StrategiesPage';
import MatchResultPage from './features/game/pages/MatchResultPage';
import LeaderboardPage from './features/leaderboard/pages/LeaderboardPage';
import ProfilePage from './features/dashboard/pages/ProfilePage';
import MatchHistoryPage from './features/dashboard/pages/MatchHistoryPage';
import TermsPage from './features/legal/pages/TermsPage';
import PrivacyPage from './features/legal/pages/PrivacyPage';
import RiskDisclosurePage from './features/legal/pages/RiskDisclosurePage';
import Footer from './layout/components/Footer';
import DailyCheckinModal from './features/dashboard/components/DailyCheckinModal';
import ChallengeListener from './features/social/components/ChallengeListener';

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
        <Route path="/missions" element={<MissionSelectionPage />} />
        <Route path="/mission-dashboard/:missionId" element={<MissionDashboard />} />
        <Route path="/strategies" element={<StrategiesPage />} />
        <Route path="/simulator" element={<SimulatorPage />} />
        <Route path="/practice" element={<PracticePage />} />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route path="/risk-disclosure" element={<RiskDisclosurePage />} />
      </Routes>
      {!hideTickerOnAuth && <StockTicker />}
      {!hideTickerOnAuth && <Footer />}
      <DailyCheckinModal />
      <ChallengeListener />
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