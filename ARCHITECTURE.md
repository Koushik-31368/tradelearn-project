# TradeLearn — System Architecture

> **Version:** 2.0 (Post-Refactor)
> **Stack:** React 19 (CRA) + Spring Boot 3.2 (Java 21) + MySQL/PostgreSQL + Redis + WebSocket (STOMP)
> **Deployment:** Vercel (frontend) + Render/Docker (backend) + Redis Cloud

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Frontend Architecture](#3-frontend-architecture)
4. [Backend Architecture](#4-backend-architecture)
5. [Data Architecture](#5-data-architecture)
6. [Real-Time Architecture](#6-real-time-architecture)
7. [Infrastructure Architecture](#7-infrastructure-architecture)
8. [Security Architecture](#8-security-architecture)
9. [Feature Domain Map](#9-feature-domain-map)
10. [Dependency Graph](#10-dependency-graph)

---

## 1. System Overview

TradeLearn is a **multiplayer stock trading simulator** built as a SaaS platform. Players compete in real-time trading games using historical Indian stock market data (NSE), earn XP/ranks through quests and achievements, and improve through guided learning modules and strategy analysis.

### Core Pillars

| Pillar | Technology |
|---|---|
| Multiplayer real-time trading | Spring WebSocket + STOMP + Redis Pub/Sub |
| Historical market data | Yahoo Finance API + local candle JSON files |
| User progression | MySQL/PostgreSQL (JPA/Hibernate) + Flyway migrations |
| Solo practice/simulator | React-side simulation engine + backend persistence |
| Ranking & matchmaking | ELO rating system + Redis ZSET queue |

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        CLIENTS                               │
│  Browser (React SPA)  ←──── Vercel CDN ────────────────────  │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTPS / WSS
┌──────────────────────────▼───────────────────────────────────┐
│                  BACKEND (Spring Boot 3.2)                    │
│                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │  REST API   │  │  WebSocket   │  │  Scheduled Tasks   │  │
│  │ (HTTP/REST) │  │ (STOMP/SockJS│  │  (Scheduler Config)│  │
│  └──────┬──────┘  └──────┬───────┘  └─────────┬──────────┘  │
│         │                │                     │             │
│  ┌──────▼────────────────▼─────────────────────▼──────────┐  │
│  │              Feature Domain Services                    │  │
│  │  auth │ game │ matchmaking │ market │ leaderboard │...  │  │
│  └──────────────────────────────────┬───────────────────── │  │
│                                     │                       │
│  ┌──────────────────────────────────▼─────────────────────┐ │
│  │                   Infrastructure                        │ │
│  │  Redis (room/queue/cache) │ Resilience │ Scheduling     │ │
│  └─────────────────────────────────────────────────────── ┘ │
└────────────────────┬───────────────┬─────────────────────────┘
                     │               │
         ┌───────────▼──┐     ┌──────▼──────┐
         │  PostgreSQL  │     │    Redis     │
         │  (primary DB)│     │  (realtime) │
         └──────────────┘     └─────────────┘
```

---

## 3. Frontend Architecture

### 3.1 Directory Structure (Target State)

```
frontend/src/
├── app/
│   ├── App.js                    ← Root router, providers
│   ├── index.js                  ← ReactDOM entry
│   ├── index.css                 ← Global reset only
│   └── reportWebVitals.js
│
├── features/                     ← Domain-first feature slices
│   ├── auth/
│   │   ├── AuthContext.js        ← AuthProvider + useAuth hook
│   │   ├── components/           ← (empty — no auth-only reusable components yet)
│   │   └── pages/
│   │       ├── LoginPage.jsx + AuthForm.css
│   │       ├── RegisterPage.jsx
│   │       └── ForgotPasswordPage.jsx
│   │
│   ├── dashboard/
│   │   ├── components/
│   │   │   ├── DashboardPanel.jsx + .css
│   │   │   └── DailyCheckinModal.jsx + .css
│   │   └── pages/
│   │       ├── HomePage.jsx + .css
│   │       ├── ProfilePage.jsx + .css
│   │       └── MatchHistoryPage.jsx + .css
│   │
│   ├── game/
│   │   ├── components/
│   │   │   ├── LiveScoreboard.jsx + .css
│   │   │   └── StockChart.jsx
│   │   └── pages/
│   │       ├── GamePage.jsx + .css
│   │       └── MatchResultPage.jsx + .css
│   │
│   ├── matchmaking/
│   │   ├── components/
│   │   │   └── CreateGameForm.jsx + .css
│   │   └── pages/
│   │       └── LobbyPage.jsx + .css
│   │
│   ├── simulator/
│   │   ├── components/           ← 23 simulator components (chart, analytics, order ticket…)
│   │   ├── data/
│   │   │   └── simulatorData.js  ← Stock universe seed data
│   │   ├── pages/
│   │   │   ├── SimulatorPage.jsx + .css
│   │   │   └── MissionSelectionPage.jsx
│   │   └── utils/
│   │       └── missions.js
│   │
│   ├── practice/
│   │   ├── data/
│   │   │   └── historicalEvents.js
│   │   ├── pages/
│   │   │   └── PracticePage.jsx + .css
│   │   └── utils/
│   │       └── aiTrader.js
│   │
│   ├── leaderboard/
│   │   ├── components/
│   │   │   ├── TierBadge.jsx + .css
│   │   │   └── TopTraders.jsx + .css
│   │   ├── pages/
│   │   │   └── LeaderboardPage.jsx + .css
│   │   └── utils/
│   │       └── skillTier.js
│   │
│   ├── learn/
│   │   ├── components/           ← CandleDiagram, LearnCard, QuizCard, LearnSection
│   │   └── pages/
│   │       └── LearnPage.jsx + .css
│   │
│   ├── strategies/
│   │   ├── components/
│   │   │   ├── StrategyCard.jsx + .css
│   │   │   └── StrategyDetail.jsx + .css
│   │   ├── pages/
│   │   │   └── StrategiesPage.jsx + .css
│   │   └── utils/
│   │       └── strategyDetector.js
│   │
│   ├── social/
│   │   └── components/
│   │       ├── ChallengeListener.jsx + .css
│   │       └── FriendsPanel.jsx + .css
│   │
│   └── legal/
│       └── pages/
│           ├── TermsPage.jsx
│           ├── PrivacyPage.jsx
│           ├── RiskDisclosurePage.jsx
│           └── LegalPages.css
│
├── shared/                       ← Truly cross-feature reusables
│   ├── components/
│   │   ├── Navbar.jsx + Navbar.css
│   │   ├── Footer.jsx + Footer.css
│   │   ├── Modal.jsx + Modal.css
│   │   ├── Hero.jsx + Hero.css
│   │   └── StockTicker.jsx + StockTicker.css
│   └── styles/
│       └── theme.css             ← Design tokens, CSS vars
│
├── api/                          ← Consolidated HTTP layer
│   ├── client.js                 ← Axios instance + interceptors
│   ├── api.js                    ← Re-export barrel
│   ├── auth.api.js
│   ├── game.api.js
│   ├── market.api.js
│   ├── leaderboard.api.js
│   └── user.api.js
│
├── hooks/
│   └── useGameSocket.js          ← STOMP WebSocket hook (game-specific but shared by GamePage + LobbyPage)
│
└── assets/
    └── background.jpg
```

### 3.2 State Management Pattern

- **Auth state:** React Context (`AuthContext.js`) — JWT stored in `localStorage`
- **Server state:** Direct API calls via `axios` — no Redux/React Query (acceptable for current scale)
- **WebSocket state:** Custom hook `useGameSocket.js` — manages STOMP connection lifecycle
- **UI state:** Local `useState` per component

### 3.3 Routing

All routes defined in `App.js` using React Router v7. Route-level code splitting is **not yet implemented** (opportunity for optimization).

| Route | Component | Auth Required |
|---|---|---|
| `/` | `HomePage` | No |
| `/login` | `LoginPage` | No |
| `/register` | `RegisterPage` | No |
| `/forgot-password` | `ForgotPasswordPage` | No |
| `/multiplayer` | `LobbyPage` | Yes |
| `/game/:gameId` | `GamePage` | Yes |
| `/match/:gameId/result` | `MatchResultPage` | Yes |
| `/simulator` | `SimulatorPage` | Yes |
| `/missions` | `MissionSelectionPage` | Yes |
| `/mission-dashboard/:missionId` | `MissionDashboard` | Yes |
| `/practice` | `PracticePage` | No |
| `/strategies` | `StrategiesPage` | No |
| `/leaderboard` | `LeaderboardPage` | No |
| `/profile` | `ProfilePage` | Yes |
| `/history` | `MatchHistoryPage` | Yes |
| `/terms` | `TermsPage` | No |
| `/privacy` | `PrivacyPage` | No |
| `/risk-disclosure` | `RiskDisclosurePage` | No |

---

## 4. Backend Architecture

### 4.1 Package Structure (Target State)

```
com.tradelearn.server/
│
├── ServerApplication.java         ← Spring Boot entry point
│
├── auth/                          ← Authentication domain
│   ├── config/SecurityConfig.java
│   ├── controller/AuthController.java
│   └── security/
│       ├── JwtUtil.java
│       ├── JwtAuthenticationFilter.java
│       ├── JwtAuthenticationEntryPoint.java
│       ├── CustomUserDetailsService.java
│       ├── WebSocketAuthInterceptor.java
│       └── WebSocketChannelInterceptor.java
│
├── user/                          ← User domain
│   ├── controller/UserController.java
│   ├── model/User.java
│   ├── repository/UserRepository.java
│   └── service/UserService.java
│
├── game/                          ← Core multiplayer game domain
│   ├── controller/
│   │   ├── MatchController.java   ← lifecycle (create/join/start/end/cancel)
│   │   └── TradeController.java   ← in-game trade placement
│   ├── model/
│   │   ├── Game.java, Trade.java
│   │   ├── MatchStats.java, PlayerPosition.java
│   ├── repository/
│   │   ├── GameRepository.java, TradeRepository.java
│   │   └── MatchStatsRepository.java
│   └── service/
│       ├── MatchLifecycleService.java ← create/join/start/end/abandon
│       ├── MatchScoringService.java   ← ELO, scoring, persistStats
│       ├── MatchTradeService.java     ← trade execution in live game
│       ├── MatchQueryService.java     ← read-only queries
│       └── MatchService.java          ← thin orchestration facade
│
├── matchmaking/                   ← Ranked queue domain
│   ├── controller/MatchmakingController.java
│   └── service/MatchmakingService.java
│
├── market/                        ← Market data domain
│   ├── controller/MarketController.java
│   ├── provider/
│   │   ├── MarketDataProvider.java (interface)
│   │   └── YahooFinanceProvider.java
│   └── service/
│       ├── CandleService.java
│       ├── HistoricalCandleService.java
│       └── MarketDataService.java
│
├── leaderboard/                   ← Ranking domain
│   ├── controller/
│   │   ├── LeaderboardController.java         ← /api/leaderboard
│   │   └── PracticeLeaderboardController.java ← /api/leaderboard/practice
│   ├── model/LeaderboardEntry.java
│   ├── repository/LeaderboardRepository.java
│   └── service/
│       ├── LeaderboardService.java
│       └── RankService.java
│
├── profile/                       ← User profile domain
│   ├── controller/ProfileController.java
│   └── service/ProfileService.java
│
├── learning/                      ← Learning/education domain
│   ├── controller/LearningController.java
│   ├── model/UserLessonProgress.java
│   ├── repository/UserLessonProgressRepository.java
│   └── service/LearningService.java
│
├── quests/                        ← Quests & achievements domain
│   ├── controller/
│   │   ├── QuestController.java
│   │   └── AchievementController.java
│   ├── model/
│   │   ├── DailyQuest.java, UserDailyQuest.java
│   │   ├── WeeklyChallenge.java, UserWeeklyChallenge.java
│   │   └── Achievement.java, UserAchievement.java
│   ├── repository/ (6 quest/achievement repos)
│   └── service/
│       ├── QuestService.java
│       ├── QuestCleanupService.java
│       └── AchievementService.java
│
├── social/                        ← Social features domain
│   ├── controller/
│   │   ├── SocialController.java
│   │   └── ChallengeWebSocketController.java
│   ├── model/Friendship.java, GameChallenge.java
│   ├── repository/FriendshipRepository.java, GameChallengeRepository.java
│   └── service/SocialService.java
│
├── analytics/                     ← Analytics & strategy domain
│   ├── controller/
│   │   ├── AnalyticsController.java
│   │   └── StrategyController.java
│   └── service/
│       ├── AnalyticsService.java
│       ├── BacktestService.java
│       └── ReadinessScoreService.java
│
├── simulator/                     ← Solo simulator domain
│   ├── controller/
│   │   ├── SimulatorController.java
│   │   └── TradeJournalController.java
│   ├── model/Portfolio.java, Holding.java, TradeJournal.java
│   ├── repository/ (3 repos)
│   └── service/SimulatorService.java
│
├── websocket/                     ← WebSocket infrastructure
│   ├── GameWebSocketHandler.java
│   ├── GameWebSocketController.java
│   ├── GameBroadcaster.java
│   ├── RedisWebSocketRelay.java
│   └── config/
│       ├── WebSocketConfig.java
│       └── WebSocketEventListener.java
│
├── infrastructure/                ← Cross-cutting infrastructure
│   ├── redis/
│   │   ├── config/
│   │   │   ├── RedisConfig.java
│   │   │   └── RedissonConfig.java
│   │   ├── room/
│   │   │   ├── RoomManager.java
│   │   │   ├── RedisRoomStore.java
│   │   │   └── ResilientRedisRoomStore.java
│   │   └── store/
│   │       └── PositionSnapshotStore.java
│   ├── resilience/
│   │   ├── CircuitBreakerRegistry.java
│   │   ├── CrashRecoveryService.java
│   │   ├── DatabaseFailoverHandler.java
│   │   ├── GameFreezeService.java
│   │   ├── GracefulDegradationManager.java
│   │   ├── HeapPressureGuard.java
│   │   └── StateReconciliationService.java
│   ├── scheduling/
│   │   ├── GameCleanupService.java
│   │   ├── MatchSchedulerService.java
│   │   └── config/
│   │       ├── AsyncConfig.java
│   │       └── SchedulerConfig.java
│   ├── ratelimit/
│   │   ├── TradeProcessingPipeline.java
│   │   └── TradeRateLimiter.java
│   └── pipeline/
│       └── GameMetricsService.java
│
├── dto/                           ← Shared cross-domain DTOs
│   └── (20 DTO classes — Candle, MatchResult, PlayerTicket…)
│
└── common/                        ← Cross-cutting shared components
    ├── config/WebConfig.java
    ├── controller/HealthController.java
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   ├── GameNotFoundException.java
    │   ├── InvalidGameStateException.java
    │   ├── RoomFullException.java
    │   └── TradeValidationException.java
    ├── middleware/
    │   ├── RateLimitFilter.java
    │   ├── RequestCorrelationFilter.java
    │   └── SecurityHeadersFilter.java
    ├── util/
    │   ├── EloUtil.java
    │   ├── ScoringUtil.java
    │   └── GameLogger.java
    └── validation/
        ├── ValidStockSymbol.java, StockSymbolValidator.java
        └── ValidTradeType.java, TradeTypeValidator.java
```

### 4.2 Domain Dependency Rules

The following dependency directions are **enforced** — no cycles allowed:

```
common/ ← (no upstream dependencies — only standard library)
infrastructure/ ← common/
features/* ← common/, infrastructure/, dto/
features/* ←X→ features/* (features must NOT depend on each other)
websocket/ ← game/, infrastructure/redis/, common/
```

---

## 5. Data Architecture

### 5.1 Database Schema (Flyway Versioned)

| Migration | Contents |
|---|---|
| V1 (baseline) | `users`, `games`, `trades`, `match_stats` tables |
| V2 | `games.elo_change`, `games.opponent_id`, trade columns |
| V2.1 | `users.elo_rating` |
| V3 | `users.xp`, `users.streak_days`, `users.last_login` |
| V4 | `user_lesson_progress`, `achievements`, `user_achievements` |
| V5 | `friendships`, `game_challenges` |
| V6 | `daily_quests`, `user_daily_quests`, `weekly_challenges`, `user_weekly_challenges` |

### 5.2 Redis Key Patterns

| Key Pattern | Type | TTL | Purpose |
|---|---|---|---|
| `room:{gameId}` | Hash | Match duration + 1h | Game room state |
| `room:{gameId}:players` | Set | Match duration + 1h | Player session tracking |
| `matchmaking:queue` | ZSET | — | ELO-ranked matchmaking queue |
| `scheduler:lock:{gameId}` | String | 30s | Distributed scheduler ownership |
| `rematch:{gameId}:{userId}` | String | 30s | Rematch consent (Lua atomic) |

---

## 6. Real-Time Architecture

### 6.1 WebSocket Flow

```
Client (STOMP over SockJS)
  │
  ├─→ /app/game/{id}/trade        → GameWebSocketHandler.handleTrade()
  ├─→ /app/game/{id}/ready        → GameWebSocketHandler.handleReady()
  ├─→ /app/game/{id}/position     → GameWebSocketHandler.handlePositionQuery()
  └─→ /app/challenge/*            → ChallengeWebSocketController
  
Server → Client:
  ├─→ /topic/game/{id}            ← GameBroadcaster (game state updates)
  ├─→ /queue/game/{id}/errors     ← Per-user error messages
  └─→ /topic/challenge/*          ← Social challenge notifications
```

### 6.2 Multi-Instance Support

- `RedisWebSocketRelay` subscribes to Redis Pub/Sub channel `game:{id}`
- Any backend instance can receive a trade and publish to Redis
- All instances relay the broadcast to connected WebSocket clients
- Ensures horizontal scalability without sticky sessions

---

## 7. Infrastructure Architecture

### 7.1 Deployment

| Component | Platform | Notes |
|---|---|---|
| Frontend | Vercel | Auto-deploy on push to `main` |
| Backend | Render / Docker | Dockerfile at `backend/Dockerfile` |
| Database | Render PostgreSQL | Connection pooling via HikariCP |
| Redis | Redis Cloud (Upstash) | Lettuce + Redisson clients |
| Load Testing | k6 + k8s (Kubernetes) | Manifests in `loadtest/` |
| Monitoring | Prometheus + Grafana | Config in `loadtest/monitoring/` |

### 7.2 CI/CD

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | PR validation — compile + test |
| `.github/workflows/build.yml` | Build artifact on merge to `main` |

### 7.3 Docker

| File | Purpose |
|---|---|
| `docker-compose.yml` | Production-like compose |
| `docker-compose.dev.yml` | Local development with hot reload |
| `frontend/Dockerfile` | Nginx-served React build |
| `backend/Dockerfile` | Spring Boot JAR |
| `frontend/nginx.conf` | SPA routing config |

---

## 8. Security Architecture

| Layer | Mechanism |
|---|---|
| Authentication | JWT (JJWT 0.12.6), 24h expiry |
| HTTP auth | `JwtAuthenticationFilter` (Spring Security filter chain) |
| WebSocket auth | `WebSocketAuthInterceptor` (handshake-time token validation) |
| WebSocket channel auth | `WebSocketChannelInterceptor` (per-message auth) |
| Rate limiting | `RateLimitFilter` (Bucket4j, IP-based) |
| Security headers | `SecurityHeadersFilter` (CSP, HSTS, X-Frame-Options) |
| CORS | `WebConfig.java` (origin whitelist) |
| Request tracing | `RequestCorrelationFilter` (X-Correlation-ID header) |

---

## 9. Feature Domain Map

| Feature | Frontend Route | Backend Controller | Backend Service | DB Tables |
|---|---|---|---|---|
| Auth | `/login`, `/register` | `AuthController` | — (Spring Security) | `users` |
| Dashboard | `/` | `UserController` | `UserService` | `users`, `games` |
| Multiplayer Game | `/multiplayer`, `/game/:id` | `MatchController`, WS | `MatchLifecycleService`, `MatchTradeService` | `games`, `trades`, `match_stats` |
| Matchmaking | `/multiplayer` | `MatchmakingController` | `MatchmakingService` | `matchmaking:queue` (Redis) |
| Simulator | `/simulator`, `/missions` | `SimulatorController` | `SimulatorService` | `portfolios`, `holdings` |
| Practice | `/practice` | `MarketController` | `CandleService` | `candles` (resources) |
| Market Data | API only | `MarketController` | `MarketDataService` | Yahoo Finance API |
| Leaderboard | `/leaderboard` | `LeaderboardController` | `LeaderboardService`, `RankService` | `users` (elo/xp fields) |
| Profile | `/profile` | `ProfileController` | `ProfileService` | `users`, `games` |
| Learning | `/learn` | `LearningController` | `LearningService` | `user_lesson_progress` |
| Strategies | `/strategies` | `AnalyticsController`, `StrategyController` | `AnalyticsService`, `BacktestService` | — |
| Quests | Dashboard | `QuestController`, `AchievementController` | `QuestService`, `AchievementService` | `daily_quests`, `achievements` |
| Social | Dashboard | `SocialController`, WS | `SocialService` | `friendships`, `game_challenges` |
| Trade Journal | Simulator | `TradeJournalController` | — | `trade_journals` |

---

## 10. Dependency Graph

### Backend Domain Dependencies

```
                    ┌─────────┐
                    │ common/ │  (no upstream deps)
                    └────┬────┘
                         │ depended on by all
          ┌──────────────┼──────────────────┐
          │              │                  │
    ┌─────▼─────┐  ┌─────▼──────┐  ┌───────▼──────┐
    │infrastructure│  │  dto/    │  │  websocket/  │
    │   /redis   │  │ (shared)  │  │              │
    │ /resilience│  └──────┬────┘  └──────┬───────┘
    │ /scheduling│         │              │
    └─────┬──────┘    used by all    uses game/
          │
    ┌─────▼──────────────────────────────────────┐
    │          Feature Domains (isolated)         │
    │  auth │ user │ game │ matchmaking │ market  │
    │  leaderboard │ profile │ learning │ quests  │
    │  social │ analytics │ simulator             │
    └────────────────────────────────────────────┘
```

### Frontend Dependency Flow

```
App.js
  └── features/*/pages/  ←── features/*/components/
                          ←── api/*.api.js  ←── api/client.js
                          ←── hooks/useGameSocket.js
                          ←── shared/components/
```
