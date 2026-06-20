# TradeLearn — Before & After Repository Tree

> **Purpose:** Exact visual diff of the repository structure before and after the refactor.
> Symbols: `[DELETE]` = remove, `[MOVE]` = relocated, `[NEW]` = created, `[KEEP]` = unchanged, `[FLATTEN]` = merged into parent

---

## BEFORE: Current Repository Tree

```
tradelearn/                                        ← Root
├── .git/
├── .github/
│   └── workflows/
│       ├── build.yml
│       └── ci.yml
├── .gitignore
├── .vscode/                                        ← IDE settings (should be gitignored)
├── CONTRIBUTING.md
├── README.md
├── docker-compose.dev.yml
├── docker-compose.yml
├── docs/
│   ├── api-reference.md
│   ├── architecture.md
│   ├── demo-accounts.md
│   ├── deployment.md
│   ├── developer-setup.md
│   ├── screenshot-guide.md
│   └── screenshots/
├── loadtest/
│   ├── LOAD_TEST_PLAN.md
│   ├── k6/
│   │   ├── seed-users.js
│   │   └── tradelearn-load.js
│   ├── k8s/
│   │   └── loadtest-infra.yaml
│   └── monitoring/
│       └── prometheus-grafana.yaml
├── tradelearn_architecture_audit.md                ← [MOVE] → docs/
├── vercel.json
│
├── backend/
│   ├── .env.example
│   ├── .gitattributes
│   ├── .gitignore
│   ├── .mvn/                                       ← DO NOT TOUCH (Maven wrapper)
│   ├── Dockerfile
│   ├── PRODUCTION_ARCHITECTURE.md                  ← [MOVE] → docs/
│   ├── logs/                                        ← [DELETE] + add to .gitignore
│   │   └── (runtime log files)
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/tradelearn/server/
│   │   │   │   ├── ServerApplication.java           ← [KEEP]
│   │   │   │   │
│   │   │   │   ├── analytics/                       ← [KEEP — already feature-based]
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── AnalyticsController.java
│   │   │   │   │   │   └── StrategyController.java
│   │   │   │   │   └── service/
│   │   │   │   │       ├── AnalyticsService.java
│   │   │   │   │       ├── BacktestService.java
│   │   │   │   │       └── ReadinessScoreService.java
│   │   │   │   │
│   │   │   │   ├── auth/                            ← [KEEP]
│   │   │   │   │   ├── config/
│   │   │   │   │   │   └── SecurityConfig.java
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   └── AuthController.java
│   │   │   │   │   └── security/
│   │   │   │   │       ├── CustomUserDetailsService.java
│   │   │   │   │       ├── JwtAuthenticationEntryPoint.java
│   │   │   │   │       ├── JwtAuthenticationFilter.java
│   │   │   │   │       ├── JwtUtil.java
│   │   │   │   │       ├── WebSocketAuthInterceptor.java
│   │   │   │   │       └── WebSocketChannelInterceptor.java
│   │   │   │   │
│   │   │   │   ├── common/                          ← [PARTIALLY FLATTEN]
│   │   │   │   │   ├── config/
│   │   │   │   │   │   └── WebConfig.java           ← [MOVE] → common/WebConfig.java
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   └── HealthController.java    ← [MOVE] → common/HealthController.java
│   │   │   │   │   ├── exception/
│   │   │   │   │   │   ├── GameNotFoundException.java
│   │   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   │   ├── InvalidGameStateException.java
│   │   │   │   │   │   ├── RoomFullException.java
│   │   │   │   │   │   └── TradeValidationException.java
│   │   │   │   │   ├── middleware/
│   │   │   │   │   │   ├── RateLimitFilter.java
│   │   │   │   │   │   ├── RequestCorrelationFilter.java
│   │   │   │   │   │   └── SecurityHeadersFilter.java
│   │   │   │   │   ├── util/
│   │   │   │   │   │   ├── EloUtil.java
│   │   │   │   │   │   ├── GameLogger.java
│   │   │   │   │   │   └── ScoringUtil.java
│   │   │   │   │   └── validation/
│   │   │   │   │       ├── StockSymbolValidator.java
│   │   │   │   │       ├── TradeTypeValidator.java
│   │   │   │   │       ├── ValidStockSymbol.java
│   │   │   │   │       └── ValidTradeType.java
│   │   │   │   │
│   │   │   │   ├── config/                          ← [DELETE — EMPTY]
│   │   │   │   ├── controller/                      ← [DELETE — EMPTY]
│   │   │   │   ├── dto/                             ← [KEEP — 20 DTOs]
│   │   │   │   │   ├── AchievementDTO.java
│   │   │   │   │   ├── BacktestRequest.java
│   │   │   │   │   ├── BacktestResult.java
│   │   │   │   │   ├── BatchBacktestRequest.java
│   │   │   │   │   ├── BatchBacktestResult.java
│   │   │   │   │   ├── Candle.java
│   │   │   │   │   ├── ChallengeDTO.java
│   │   │   │   │   ├── CreateGameRequest.java
│   │   │   │   │   ├── CreateMatchRequest.java
│   │   │   │   │   ├── EndMatchRequest.java
│   │   │   │   │   ├── EquityPointDto.java
│   │   │   │   │   ├── FriendDTO.java
│   │   │   │   │   ├── JoinGameRequest.java
│   │   │   │   │   ├── LeaderboardDTO.java
│   │   │   │   │   ├── MatchResult.java
│   │   │   │   │   ├── MatchTradeRequest.java
│   │   │   │   │   ├── PlayerTicket.java
│   │   │   │   │   ├── QuestDTO.java
│   │   │   │   │   ├── TradeDto.java
│   │   │   │   │   └── TradeRequest.java
│   │   │   │   │
│   │   │   │   ├── exception/                       ← [DELETE — EMPTY]
│   │   │   │   ├── game/                            ← [KEEP]
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── MatchController.java
│   │   │   │   │   │   └── TradeController.java
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Game.java
│   │   │   │   │   │   ├── MatchStats.java
│   │   │   │   │   │   ├── PlayerPosition.java
│   │   │   │   │   │   └── Trade.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── GameRepository.java
│   │   │   │   │   │   ├── MatchStatsRepository.java
│   │   │   │   │   │   └── TradeRepository.java
│   │   │   │   │   └── service/
│   │   │   │   │       ├── MatchLifecycleService.java
│   │   │   │   │       ├── MatchQueryService.java
│   │   │   │   │       ├── MatchScoringService.java
│   │   │   │   │       ├── MatchService.java
│   │   │   │   │       ├── MatchTradeService.java
│   │   │   │   │       └── TradeService.java
│   │   │   │   │
│   │   │   │   ├── infrastructure/                  ← [KEEP + CLEANUP]
│   │   │   │   │   ├── pipeline/
│   │   │   │   │   │   └── GameMetricsService.java  ← [MOVE] → infrastructure/scheduling/
│   │   │   │   │   ├── ratelimit/
│   │   │   │   │   │   ├── TradeProcessingPipeline.java
│   │   │   │   │   │   └── TradeRateLimiter.java
│   │   │   │   │   ├── redis/
│   │   │   │   │   │   ├── config/
│   │   │   │   │   │   │   ├── RedisConfig.java
│   │   │   │   │   │   │   └── RedissonConfig.java
│   │   │   │   │   │   ├── room/
│   │   │   │   │   │   │   ├── RedisRoomStore.java
│   │   │   │   │   │   │   ├── ResilientRedisRoomStore.java
│   │   │   │   │   │   │   └── RoomManager.java
│   │   │   │   │   │   └── store/
│   │   │   │   │   │       └── PositionSnapshotStore.java
│   │   │   │   │   ├── resilience/
│   │   │   │   │   │   ├── CircuitBreakerRegistry.java
│   │   │   │   │   │   ├── CrashRecoveryService.java
│   │   │   │   │   │   ├── DatabaseFailoverHandler.java
│   │   │   │   │   │   ├── GameFreezeService.java
│   │   │   │   │   │   ├── GracefulDegradationManager.java
│   │   │   │   │   │   ├── HeapPressureGuard.java
│   │   │   │   │   │   └── StateReconciliationService.java
│   │   │   │   │   └── scheduling/
│   │   │   │   │       ├── GameCleanupService.java
│   │   │   │   │       ├── MatchSchedulerService.java
│   │   │   │   │       └── config/
│   │   │   │   │           ├── AsyncConfig.java
│   │   │   │   │           └── SchedulerConfig.java
│   │   │   │   │
│   │   │   │   ├── leaderboard/                     ← [KEEP]
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── LeaderboardController.java
│   │   │   │   │   │   └── PracticeLeaderboardController.java
│   │   │   │   │   ├── model/
│   │   │   │   │   │   └── LeaderboardEntry.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── LeaderboardRepository.java
│   │   │   │   │   └── service/
│   │   │   │   │       ├── LeaderboardService.java
│   │   │   │   │       └── RankService.java
│   │   │   │   │
│   │   │   │   ├── learning/                        ← [KEEP]
│   │   │   │   │   ├── controller/LearningController.java
│   │   │   │   │   ├── model/UserLessonProgress.java
│   │   │   │   │   ├── repository/UserLessonProgressRepository.java
│   │   │   │   │   └── service/LearningService.java
│   │   │   │   │
│   │   │   │   ├── market/                          ← [KEEP]
│   │   │   │   │   ├── controller/MarketController.java
│   │   │   │   │   ├── provider/
│   │   │   │   │   │   ├── MarketDataProvider.java
│   │   │   │   │   │   └── YahooFinanceProvider.java
│   │   │   │   │   └── service/
│   │   │   │   │       ├── CandleService.java
│   │   │   │   │       ├── HistoricalCandleService.java
│   │   │   │   │       └── MarketDataService.java
│   │   │   │   │
│   │   │   │   ├── matchmaking/                     ← [KEEP]
│   │   │   │   │   ├── controller/MatchmakingController.java
│   │   │   │   │   └── service/MatchmakingService.java
│   │   │   │   │
│   │   │   │   ├── middleware/                      ← [DELETE — EMPTY]
│   │   │   │   ├── model/                           ← [DELETE — EMPTY]
│   │   │   │   ├── profile/                         ← [KEEP]
│   │   │   │   │   ├── controller/ProfileController.java
│   │   │   │   │   └── service/ProfileService.java
│   │   │   │   │
│   │   │   │   ├── quests/                          ← [KEEP]
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── AchievementController.java
│   │   │   │   │   │   └── QuestController.java
│   │   │   │   │   ├── model/ (6 files)
│   │   │   │   │   ├── repository/ (6 files)
│   │   │   │   │   └── service/
│   │   │   │   │       ├── AchievementService.java
│   │   │   │   │       ├── QuestCleanupService.java
│   │   │   │   │       └── QuestService.java
│   │   │   │   │
│   │   │   │   ├── repository/                      ← [DELETE — EMPTY]
│   │   │   │   ├── security/                        ← [DELETE — EMPTY]
│   │   │   │   ├── service/                         ← [DELETE — EMPTY]
│   │   │   │   ├── simulator/                       ← [KEEP]
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── SimulatorController.java
│   │   │   │   │   │   └── TradeJournalController.java
│   │   │   │   │   ├── model/ (3 files)
│   │   │   │   │   ├── repository/ (3 files)
│   │   │   │   │   └── service/SimulatorService.java
│   │   │   │   │
│   │   │   │   ├── social/                          ← [KEEP]
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── ChallengeWebSocketController.java
│   │   │   │   │   │   └── SocialController.java
│   │   │   │   │   ├── model/ (2 files)
│   │   │   │   │   ├── repository/ (2 files)
│   │   │   │   │   └── service/SocialService.java
│   │   │   │   │
│   │   │   │   ├── socket/                          ← [DELETE — EMPTY]
│   │   │   │   ├── user/                            ← [KEEP]
│   │   │   │   │   ├── controller/UserController.java
│   │   │   │   │   ├── model/User.java
│   │   │   │   │   ├── repository/UserRepository.java
│   │   │   │   │   └── service/UserService.java
│   │   │   │   │
│   │   │   │   ├── util/                            ← [DELETE — EMPTY]
│   │   │   │   ├── validation/                      ← [DELETE — EMPTY]
│   │   │   │   └── websocket/                       ← [KEEP]
│   │   │   │       ├── GameBroadcaster.java
│   │   │   │       ├── GameWebSocketController.java
│   │   │   │       ├── GameWebSocketHandler.java
│   │   │   │       ├── RedisWebSocketRelay.java
│   │   │   │       └── config/
│   │   │   │           ├── WebSocketConfig.java
│   │   │   │           └── WebSocketEventListener.java
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       ├── application-local.properties
│   │   │       ├── application-prod.properties
│   │   │       ├── logback-spring.xml
│   │   │       ├── candles/ (10 JSON files)
│   │   │       └── db/
│   │   │           ├── seed-demo.sql
│   │   │           └── migration/ (6 Flyway scripts)
│   │   │
│   │   └── test/java/com/tradelearn/server/
│   │       ├── ServerApplicationTests.java
│   │       ├── game/service/MatchLifecycleServiceTest.java
│   │       ├── leaderboard/service/RankServiceTest.java
│   │       ├── market/service/MarketDataServiceTest.java
│   │       ├── matchmaking/service/MatchmakingServiceTest.java
│   │       ├── profile/service/ProfileServiceTest.java
│   │       └── util/
│   │           ├── EloUtilTest.java
│   │           └── ScoringUtilTest.java
│   │
│   └── target/                                      ← Build output (gitignored)
│
└── frontend/
    ├── .dockerignore
    ├── .env                                         ← (verify no secrets)
    ├── .env.example
    ├── .gitignore
    ├── Dockerfile
    ├── README.md
    ├── fix_imports.js                               ← [DELETE]
    ├── jsconfig.json
    ├── logo.svg                                     ← [DELETE — orphaned]
    ├── nginx.conf
    ├── package.json
    ├── package-lock.json
    ├── vercel.json
    ├── public/
    │   ├── _redirects
    │   ├── index.html
    │   ├── manifest.json
    │   ├── service-worker.js
    │   └── sounds/
    └── src/
        ├── App.js
        ├── App.test.js
        ├── index.css
        ├── index.js
        ├── reportWebVitals.js
        ├── setupTests.js
        ├── api/
        │   ├── api.js
        │   ├── auth.api.js
        │   ├── client.js
        │   ├── game.api.js
        │   ├── leaderboard.api.js
        │   ├── market.api.js
        │   └── user.api.js
        ├── assets/
        │   └── background.jpg
        ├── data/
        │   └── historicalEvents.js                  ← [DELETE — duplicate]
        ├── hooks/
        │   └── useGameSocket.js
        ├── layout/
        │   └── components/
        │       ├── Footer.css
        │       ├── Footer.jsx
        │       ├── Hero.css
        │       ├── Hero.jsx
        │       ├── Modal.css
        │       ├── Modal.jsx
        │       ├── Navbar.css
        │       ├── Navbar.jsx
        │       ├── StockTicker.css
        │       └── StockTicker.jsx
        ├── styles/
        │   └── theme.css
        ├── utils/                                   ← [DELETE — EMPTY]
        └── features/
            ├── auth/
            │   ├── AuthContext.js
            │   ├── components/                      ← [DELETE — EMPTY]
            │   └── pages/
            │       ├── AuthForm.css
            │       ├── ForgotPasswordPage.jsx
            │       ├── LoginPage.jsx
            │       └── RegisterPage.jsx
            ├── dashboard/
            │   ├── components/
            │   │   ├── DailyCheckinModal.css
            │   │   ├── DailyCheckinModal.jsx
            │   │   ├── DashboardPanel.css
            │   │   └── DashboardPanel.jsx
            │   └── pages/
            │       ├── HomePage.css
            │       ├── HomePage.jsx
            │       ├── MatchHistoryPage.css
            │       ├── MatchHistoryPage.jsx
            │       ├── ProfilePage.css
            │       └── ProfilePage.jsx
            ├── game/
            │   ├── components/
            │   │   ├── LiveScoreboard.css
            │   │   ├── LiveScoreboard.jsx
            │   │   └── StockChart.jsx
            │   └── pages/
            │       ├── GamePage.css
            │       ├── GamePage.jsx
            │       ├── MatchResultPage.css
            │       └── MatchResultPage.jsx
            ├── leaderboard/
            │   ├── components/
            │   │   ├── TierBadge.css
            │   │   ├── TierBadge.jsx
            │   │   ├── TopTraders.css
            │   │   └── TopTraders.jsx
            │   ├── pages/
            │   │   ├── LeaderboardPage.css
            │   │   └── LeaderboardPage.jsx
            │   └── utils/
            │       └── skillTier.js
            ├── learn/
            │   ├── components/ (8 files — CandleDiagram, LearnCard, QuizCard, LearnSection)
            │   └── pages/
            │       ├── LearnPage.css
            │       └── LearnPage.jsx
            ├── legal/
            │   └── pages/
            │       ├── LegalPages.css
            │       ├── PrivacyPage.jsx
            │       ├── RiskDisclosurePage.jsx
            │       └── TermsPage.jsx
            ├── matchmaking/
            │   ├── components/
            │   │   ├── CreateGameForm.css
            │   │   └── CreateGameForm.jsx
            │   └── pages/
            │       ├── LobbyPage.css
            │       └── LobbyPage.jsx
            ├── practice/
            │   ├── data/
            │   │   └── historicalEvents.js          ← CANONICAL copy (keep)
            │   ├── pages/
            │   │   ├── PracticePage.css
            │   │   └── PracticePage.jsx
            │   └── utils/
            │       └── aiTrader.js
            ├── simulator/
            │   ├── components/ (23 files)
            │   ├── data/                            ← [DELETE — EMPTY]
            │   ├── pages/
            │   │   ├── MissionSelectionPage.jsx
            │   │   ├── SimulatorPage.css
            │   │   └── SimulatorPage.jsx
            │   └── utils/
            │       ├── missions.js
            │       └── simulatorData.js
            ├── social/
            │   ├── components/
            │   │   ├── ChallengeListener.css
            │   │   ├── ChallengeListener.jsx
            │   │   ├── FriendsPanel.css
            │   │   └── FriendsPanel.jsx
            │   └── pages/                           ← [DELETE — EMPTY]
            └── strategies/
                ├── components/
                │   ├── StrategyCard.css
                │   ├── StrategyCard.jsx
                │   ├── StrategyDetail.css
                │   └── StrategyDetail.jsx
                ├── pages/
                │   ├── StrategiesPage.css
                │   └── StrategiesPage.jsx
                └── utils/
                    └── strategyDetector.js
```

---

## AFTER: Target Repository Tree

```
tradelearn/                                         ← Root (CLEANER)
├── .git/
├── .github/
│   └── workflows/
│       ├── build.yml
│       └── ci.yml
├── .gitignore                                      ← [UPDATED: add .vscode/, backend/logs/]
├── ARCHITECTURE.md                                 ← [NEW]
├── BEFORE_AFTER_TREE.md                            ← [NEW — this file]
├── CONTRIBUTING.md
├── README.md
├── REFACTOR_PLAN.md                                ← [NEW]
├── docker-compose.dev.yml
├── docker-compose.yml
├── docs/
│   ├── api-reference.md
│   ├── architecture-audit-2026.md                  ← [MOVED from root]
│   ├── architecture.md
│   ├── demo-accounts.md
│   ├── deployment.md
│   ├── developer-setup.md
│   ├── PRODUCTION_ARCHITECTURE.md                  ← [MOVED from backend/]
│   ├── screenshot-guide.md
│   └── screenshots/
├── loadtest/                                       ← [KEEP unchanged]
│   ├── LOAD_TEST_PLAN.md
│   ├── k6/
│   │   ├── seed-users.js
│   │   └── tradelearn-load.js
│   ├── k8s/
│   │   └── loadtest-infra.yaml
│   └── monitoring/
│       └── prometheus-grafana.yaml
└── vercel.json
│
├── backend/                                        ← CLEANER (no stubs, no misplaced docs)
│   ├── .env.example
│   ├── .gitattributes
│   ├── .gitignore                                  ← [UPDATED: add logs/, *.log]
│   ├── .mvn/                                       ← DO NOT TOUCH
│   ├── Dockerfile
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/tradelearn/server/
│       │   │   ├── ServerApplication.java
│       │   │   │
│       │   │   ├── analytics/                      ← [KEEP]
│       │   │   │   ├── controller/ (2 files)
│       │   │   │   └── service/ (3 files)
│       │   │   │
│       │   │   ├── auth/                           ← [KEEP]
│       │   │   │   ├── config/SecurityConfig.java
│       │   │   │   ├── controller/AuthController.java
│       │   │   │   └── security/ (6 files)
│       │   │   │
│       │   │   ├── common/                         ← [FLATTENED: removed config/ and controller/ subdirs]
│       │   │   │   ├── WebConfig.java              ← [MOVED from common/config/]
│       │   │   │   ├── HealthController.java        ← [MOVED from common/controller/]
│       │   │   │   ├── exception/ (5 files)
│       │   │   │   ├── middleware/ (3 files)
│       │   │   │   ├── util/ (3 files)
│       │   │   │   └── validation/ (4 files)
│       │   │   │
│       │   │   ├── dto/                            ← [KEEP — 20 DTOs]
│       │   │   │
│       │   │   ├── game/                           ← [KEEP]
│       │   │   │   ├── controller/ (2 files)
│       │   │   │   ├── model/ (4 files)
│       │   │   │   ├── repository/ (3 files)
│       │   │   │   └── service/ (6 files)
│       │   │   │
│       │   │   ├── infrastructure/                 ← [CLEANED: pipeline/ merged into scheduling/]
│       │   │   │   ├── ratelimit/ (2 files)
│       │   │   │   ├── redis/
│       │   │   │   │   ├── config/ (2 files)
│       │   │   │   │   ├── room/ (3 files)
│       │   │   │   │   └── store/ (1 file)
│       │   │   │   ├── resilience/ (7 files)
│       │   │   │   └── scheduling/                 ← [UPDATED: +GameMetricsService.java]
│       │   │   │       ├── GameCleanupService.java
│       │   │   │       ├── GameMetricsService.java  ← [MOVED from infrastructure/pipeline/]
│       │   │   │       ├── MatchSchedulerService.java
│       │   │   │       └── config/ (2 files)
│       │   │   │
│       │   │   ├── leaderboard/                    ← [KEEP]
│       │   │   │   ├── controller/ (2 files)
│       │   │   │   ├── model/ (1 file)
│       │   │   │   ├── repository/ (1 file)
│       │   │   │   └── service/ (2 files)
│       │   │   │
│       │   │   ├── learning/                       ← [KEEP]
│       │   │   │   ├── controller/ (1 file)
│       │   │   │   ├── model/ (1 file)
│       │   │   │   ├── repository/ (1 file)
│       │   │   │   └── service/ (1 file)
│       │   │   │
│       │   │   ├── market/                         ← [KEEP]
│       │   │   │   ├── controller/ (1 file)
│       │   │   │   ├── provider/ (2 files)
│       │   │   │   └── service/ (3 files)
│       │   │   │
│       │   │   ├── matchmaking/                    ← [KEEP]
│       │   │   │   ├── controller/ (1 file)
│       │   │   │   └── service/ (1 file)
│       │   │   │
│       │   │   ├── profile/                        ← [KEEP]
│       │   │   │   ├── controller/ (1 file)
│       │   │   │   └── service/ (1 file)
│       │   │   │
│       │   │   ├── quests/                         ← [KEEP]
│       │   │   │   ├── controller/ (2 files)
│       │   │   │   ├── model/ (6 files)
│       │   │   │   ├── repository/ (6 files)
│       │   │   │   └── service/ (3 files)
│       │   │   │
│       │   │   ├── simulator/                      ← [KEEP]
│       │   │   │   ├── controller/ (2 files)
│       │   │   │   ├── model/ (3 files)
│       │   │   │   ├── repository/ (3 files)
│       │   │   │   └── service/ (1 file)
│       │   │   │
│       │   │   ├── social/                         ← [KEEP]
│       │   │   │   ├── controller/ (2 files)
│       │   │   │   ├── model/ (2 files)
│       │   │   │   ├── repository/ (2 files)
│       │   │   │   └── service/ (1 file)
│       │   │   │
│       │   │   ├── user/                           ← [KEEP]
│       │   │   │   ├── controller/ (1 file)
│       │   │   │   ├── model/ (1 file)
│       │   │   │   ├── repository/ (1 file)
│       │   │   │   └── service/ (1 file)
│       │   │   │
│       │   │   └── websocket/                      ← [KEEP]
│       │   │       ├── GameBroadcaster.java
│       │   │       ├── GameWebSocketController.java
│       │   │       ├── GameWebSocketHandler.java
│       │   │       ├── RedisWebSocketRelay.java
│       │   │       └── config/ (2 files)
│       │   │
│       │   └── resources/                          ← DO NOT TOUCH
│       │       ├── application*.properties (3)
│       │       ├── logback-spring.xml
│       │       ├── candles/ (10 JSON files)
│       │       └── db/
│       │           ├── seed-demo.sql
│       │           └── migration/ (6 Flyway scripts)
│       │
│       └── test/java/com/tradelearn/server/
│           ├── ServerApplicationTests.java
│           ├── game/service/MatchLifecycleServiceTest.java
│           ├── leaderboard/service/RankServiceTest.java
│           ├── market/service/MarketDataServiceTest.java
│           ├── matchmaking/service/MatchmakingServiceTest.java
│           ├── profile/service/ProfileServiceTest.java
│           └── util/
│               ├── EloUtilTest.java
│               └── ScoringUtilTest.java
│
└── frontend/                                       ← CLEANER
    ├── .dockerignore
    ├── .env
    ├── .env.example
    ├── .gitignore
    ├── Dockerfile
    ├── README.md
    ├── jsconfig.json
    ├── nginx.conf
    ├── package.json
    ├── package-lock.json
    ├── vercel.json
    ├── public/
    │   ├── _redirects
    │   ├── index.html
    │   ├── manifest.json
    │   ├── service-worker.js
    │   └── sounds/
    └── src/
        ├── App.js
        ├── App.test.js
        ├── index.css
        ├── index.js
        ├── reportWebVitals.js
        ├── setupTests.js
        ├── api/                                    ← [KEEP — 7 files]
        ├── assets/
        │   └── background.jpg
        ├── hooks/
        │   └── useGameSocket.js
        ├── layout/
        │   └── components/                         ← [KEEP — 10 files]
        ├── styles/
        │   └── theme.css
        └── features/
            ├── auth/
            │   ├── AuthContext.js
            │   └── pages/ (4 files)               ← (empty components/ dir removed)
            ├── dashboard/
            │   ├── components/ (4 files)
            │   └── pages/ (6 files)
            ├── game/
            │   ├── components/ (3 files)
            │   └── pages/ (4 files)
            ├── leaderboard/
            │   ├── components/ (4 files)
            │   ├── pages/ (2 files)
            │   └── utils/ (1 file)
            ├── learn/
            │   ├── components/ (8 files)
            │   └── pages/ (2 files)
            ├── legal/
            │   └── pages/ (4 files)
            ├── matchmaking/
            │   ├── components/ (2 files)
            │   └── pages/ (2 files)
            ├── practice/
            │   ├── data/
            │   │   └── historicalEvents.js         ← CANONICAL (kept)
            │   ├── pages/ (2 files)
            │   └── utils/ (1 file)
            ├── simulator/
            │   ├── components/ (23 files)
            │   ├── pages/ (3 files)               ← (empty data/ dir removed)
            │   └── utils/ (2 files)
            ├── social/
            │   └── components/ (4 files)           ← (empty pages/ dir removed)
            └── strategies/
                ├── components/ (4 files)
                ├── pages/ (2 files)
                └── utils/ (1 file)
```

---

## Change Summary

### Files Deleted

| File/Directory | Reason |
|---|---|
| `backend/src/main/java/com/tradelearn/server/controller/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/model/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/repository/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/service/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/security/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/socket/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/exception/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/middleware/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/util/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/validation/` | Empty stub |
| `backend/src/main/java/com/tradelearn/server/config/` | Empty stub |
| `backend/logs/` | Runtime files committed to source control |
| `frontend/fix_imports.js` | Completed migration script, no longer needed |
| `frontend/logo.svg` | Orphaned, unreferenced asset |
| `frontend/src/utils/` | Empty directory |
| `frontend/src/data/historicalEvents.js` | Duplicate of `features/practice/data/historicalEvents.js` |
| `frontend/src/data/` | Empty after removing duplicate |
| `frontend/src/features/simulator/data/` | Empty directory |
| `frontend/src/features/social/pages/` | Empty directory |
| `frontend/src/features/auth/components/` | Empty directory |
| `backend/src/main/java/com/tradelearn/server/common/config/` | After flattening WebConfig.java |
| `backend/src/main/java/com/tradelearn/server/common/controller/` | After flattening HealthController.java |
| `backend/src/main/java/com/tradelearn/server/infrastructure/pipeline/` | After moving GameMetricsService.java |

### Files Moved

| From | To | Notes |
|---|---|---|
| `tradelearn_architecture_audit.md` | `docs/architecture-audit-2026.md` | Root cleanup |
| `backend/PRODUCTION_ARCHITECTURE.md` | `docs/PRODUCTION_ARCHITECTURE.md` | Docs belong in docs/ |
| `common/config/WebConfig.java` | `common/WebConfig.java` | Flatten single-file dirs |
| `common/controller/HealthController.java` | `common/HealthController.java` | Flatten single-file dirs |
| `infrastructure/pipeline/GameMetricsService.java` | `infrastructure/scheduling/GameMetricsService.java` | Logical home |

### Files Created (New)

| File | Purpose |
|---|---|
| `ARCHITECTURE.md` | Official system architecture document |
| `REFACTOR_PLAN.md` | Detailed refactor plan with migration steps |
| `BEFORE_AFTER_TREE.md` | This file — visual before/after comparison |

---

## Metrics Comparison

| Metric | Before | After | Change |
|---|---|---|---|
| Empty backend directories | 11 | 0 | -11 |
| Backend top-level packages | 27 | 15 | -12 (empty stubs removed) |
| Frontend dead files | 2 (fix_imports.js, logo.svg) | 0 | -2 |
| Frontend empty directories | 5 | 0 | -5 |
| Root-level clutter files | 1 (audit.md) | 0 | -1 |
| Misplaced docs in backend/ | 1 (PRODUCTION_ARCHITECTURE.md) | 0 | -1 |
| Duplicate data files | 1 pair | 0 | -1 |
| Files requiring import updates | 2 (WebConfig, HealthController) | — | done |
| Files requiring package updates | 3 (WebConfig, HealthController, GameMetricsService) | — | done |
