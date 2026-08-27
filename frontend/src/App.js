import { Suspense, lazy, useEffect } from 'react';
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import { AuthProvider } from './features/auth/AuthContext';
import Navbar from './layout/components/Navbar';
import MarketTicker from './layout/components/MarketTicker';
import Footer from './layout/components/Footer';
import DailyCheckinModal from './features/dashboard/components/DailyCheckinModal';
import ChallengeListener from './features/social/components/ChallengeListener';

// ── Lazy-loaded pages: each page's code is downloaded only when first visited ──
const HomePage          = lazy(() => import('./features/dashboard/pages/HomePage'));
const LoginPage         = lazy(() => import('./features/auth/pages/LoginPage'));
const RegisterPage      = lazy(() => import('./features/auth/pages/RegisterPage'));
const ForgotPasswordPage= lazy(() => import('./features/auth/pages/ForgotPasswordPage'));
const LobbyPage         = lazy(() => import('./features/matchmaking/pages/LobbyPage'));
const GamePage          = lazy(() => import('./features/game/pages/GamePage'));
const SimulatorPage     = lazy(() => import('./features/simulator/pages/SimulatorPage'));
const PracticePage      = lazy(() => import('./features/practice/pages/PracticePage'));
const MissionSelectionPage = lazy(() => import('./features/simulator/pages/MissionSelectionPage'));
const MissionDashboard  = lazy(() => import('./features/simulator/components/MissionDashboard'));
const StrategiesPage    = lazy(() => import('./features/strategies/pages/StrategiesPage'));
const MatchResultPage   = lazy(() => import('./features/game/pages/MatchResultPage'));
const LeaderboardPage   = lazy(() => import('./features/leaderboard/pages/LeaderboardPage'));
const ProfilePage       = lazy(() => import('./features/dashboard/pages/ProfilePage'));
const MatchHistoryPage  = lazy(() => import('./features/dashboard/pages/MatchHistoryPage'));
const TermsPage         = lazy(() => import('./features/legal/pages/TermsPage'));
const PrivacyPage       = lazy(() => import('./features/legal/pages/PrivacyPage'));
const RiskDisclosurePage= lazy(() => import('./features/legal/pages/RiskDisclosurePage'));
const LearnPage         = lazy(() => import('./features/learn/pages/LearnPage'));
const NotFoundPage      = lazy(() => import('./features/errors/pages/NotFoundPage'));

// ── Minimal loading spinner shown while a lazy page chunk downloads ──
function PageLoader() {
  return (
    <div className="page-loader" role="status" aria-live="polite">
      <span className="page-loader__mark" aria-hidden="true">TL</span>
      <span>Opening the market...</span>
    </div>
  );
}

const AUTH_PATHS = ['/login', '/register', '/forgot-password'];

function AppContent() {
  const location = useLocation();
  const hideTickerOnAuth = AUTH_PATHS.includes(location.pathname);

  useEffect(() => {
    const bar = document.getElementById('scroll-progress');
    if (!bar) return;
    const onScroll = () => {
      const scrolled = window.scrollY;
      const total = document.documentElement.scrollHeight - window.innerHeight;
      bar.style.width = total > 0 ? `${(scrolled / total) * 100}%` : '0%';
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [location.pathname]);

  return (
    <div className="App">
      {/* ── Skip-to-content link (a11y) ── */}
      <a href="#main-content" className="visually-hidden" style={{ position: 'absolute', top: 8, left: 8, zIndex: 10000, padding: '8px 16px', background: 'var(--gold)', color: 'var(--text-dark)', fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 700, borderRadius: 2, textDecoration: 'none' }}
        onFocus={e => e.target.classList.remove('visually-hidden')}
        onBlur={e => e.target.classList.add('visually-hidden')}
      >
        Skip to main content
      </a>
      <div id="scroll-progress" aria-hidden="true" />
      <Navbar />
      <MarketTicker />
      <Suspense fallback={<PageLoader />}>
        <main id="main-content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/learn" element={<LearnPage />} />
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
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        </main>
      </Suspense>
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
