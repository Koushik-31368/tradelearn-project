# TradeLearn — Staff Engineer Architecture Audit

> **Audit Date:** June 2026  
> **Auditor Role:** Staff Software Engineer  
> **Scope:** Full repository — backend (Spring Boot / Java 21) + frontend (React CRA)  
> **Goal:** Identify every structural, organizational, and maintainability issue before Series A.

---

## Table of Contents

1. [Current Architecture Audit](#1-current-architecture-audit)
2. [Proposed Professional Architecture](#2-proposed-professional-architecture)
3. [File-by-File Refactor Plan](#3-file-by-file-refactor-plan)
4. [Duplicate / Dead Code Identification](#4-duplicate--dead-code-identification)
5. [Service Layer Audit](#5-service-layer-audit)
6. [Backend Audit](#6-backend-audit)
7. [Frontend Audit](#7-frontend-audit)
8. [Documentation Audit](#8-documentation-audit)
9. [Migration Roadmap](#9-migration-roadmap)

---

## 1. Current Architecture Audit

### 1.1 Current Folder Structure

```
tradelearn/
├── backend/
│   ├── src/main/java/com/tradelearn/server/
│   │   ├── config/          (8 files)
│   │   ├── controller/      (20 files)        ← BLOATED
│   │   ├── dto/             (18 files)
│   │   ├── exception/       (5 files)
│   │   ├── middleware/      (3 files)
│   │   ├── model/           (16 files)
│   │   ├── repository/      (11 files)
│   │   ├── security/        (5 files)
│   │   ├── service/         (36 files)        ← SEVERELY BLOATED
│   │   ├── socket/          (3 files)
│   │   ├── util/            (3 files)
│   │   └── validation/      (4 files)
│   └── src/test/java/       (3 files)         ← NEAR-EMPTY
├── frontend/src/
│   ├── assets/
│   ├── components/          (flat + subdirs, mixed depth)
│   ├── context/
│   ├── data/
│   ├── hooks/
│   ├── pages/               (20+ pages, some with paired CSS)
│   ├── services/
│   ├── styles/
│   └── utils/
├── docs/                    (2 files — thin)
├── loadtest/                (k6 + k8s + monitoring)
└── CONTRIBUTING.md, README.md, docker-compose.yml, vercel.json
```

**Verdict:** The package structure is layered by technical role (controller/service/repository) rather than by feature domain. This is the classic "package-by-layer" anti-pattern that creates spaghetti dependencies between unrelated features at scale.

---

### 1.2 Feature Inventory

| Feature Domain | Backend Services | Controllers | Status |
|---|---|---|---|
| Auth / Users | UserService, RankService | AuthController, UserController | ✅ Clean |
| Multiplayer Match | MatchService (879 LOC!) | MatchController, GameController | ⚠️ God class |
| Match Trading | MatchTradeService | MatchController (mixed in) | ⚠️ Leaked |
| Matchmaking | MatchmakingService, MatchmakingQueueMonitor, QueueSizeProvider | MatchmakingController | ⚠️ Split unnecessarily |
| Room Management | RoomManager, RedisRoomStore, ResilientRedisRoomStore | — | ⚠️ 3-layer deep, confusing |
| Candle/Market | CandleService, HistoricalCandleService, MarketDataService, MarketDataProvider, YahooFinanceProvider | MarketController, MatchController (mixed) | ⚠️ Fragmented |
| Recovery/Resilience | CrashRecoveryService, GracefulDegradationManager, GameFreezeService, DatabaseFailoverHandler, StateReconciliationService, CircuitBreakerRegistry | HealthController | ⚠️ 6 resilience services in `service/` package |
| Leaderboard | LeaderboardService, RankService | LeaderboardController, PracticeLeaderboardController | ⚠️ Duplicate leaderboards |
| Learning | LearningService | LearningController | ✅ Isolated |
| Quests/Achievements | QuestService, QuestCleanupService, AchievementService | QuestController, AchievementController | ✅ OK |
| Analytics | AnalyticsService, BacktestService, ReadinessScoreService, GameMetricsService | AnalyticsController, StrategyController | ⚠️ Mixed concerns |
| Social | SocialService | SocialController, ChallengeWebSocketController | ✅ Isolated |
| Simulator | SimulatorService | SimulatorController | ✅ Isolated |
| Infrastructure | HeapPressureGuard, TradeProcessingPipeline, TradeRateLimiter, PositionSnapshotStore | — | ⚠️ All live in `service/` |

---

### 1.3 Technical Debt Inventory

#### 🔴 Critical Debt

1. **`service/` package is a junk drawer (36 files)**: Domain services, infrastructure components, resilience patterns, schedulers, and data stores are all co-mingled. A new developer cannot distinguish business logic from infrastructure code.

2. **`MatchService.java` is 879 lines** — a God class handling: match creation, join, auto-match, start, end, abandon-scoring, ELO calculation, rematch, quest updates, TransactionSynchronization registration, broadcaster calls, and position store management.

3. **`MarketDataService.java` hard-wires `YahooFinanceProvider`** at the constructor level (`@Autowired public MarketDataService(YahooFinanceProvider provider)`), defeating the `MarketDataProvider` interface abstraction entirely.

4. **Dual `Candle` class** — `com.tradelearn.server.model.Candle` (no Lombok, timestamp-based) AND `com.tradelearn.server.dto.Candle` (Lombok, date-string-based) exist simultaneously. The DTO is used in `MarketDataService`/`BacktestService`; the model is used in `CandleService`. This is a persistent source of confusion and import errors.

5. **`MatchController.java` contains candle management** (`/candle`, `/candle/advance`, `/candle/price`, `/candle/remaining`) — those are operational concerns that belong in a dedicated `MarketController` or at minimum a `CandleController`.

6. **`GameController.java` (legacy) and `MatchController.java` both expose join endpoints** (`POST /api/games/{id}/join` and `POST /api/match/{id}/join`). Two controllers for the same action. The `GameController` even labels itself "LEGACY" in a comment.

7. **`LeaderboardController.java`** exposes `/api/users/leaderboard` — but this controller is named "Leaderboard" and lives at the `/api/users` path. Meanwhile `PracticeLeaderboardController` lives at `/api/leaderboard`. The path conventions are inconsistent and confusing.

8. **`LeaderboardController.getUserProfile()`** contains 80+ lines of business logic (game filtering, wins/losses counting, stat calculation, recent match mapping) inline in a controller method. This is controller-as-service anti-pattern.

9. **`MatchController` duplicates participant authorization logic** in 3+ methods (`startMatch`, `endMatch`, `deleteMatch`) — the same `isParticipant` check copy-pasted each time.

10. **Zero integration tests / near-zero unit tests**: Only `ServerApplicationTests.java` (context load), `EloUtilTest.java`, and `ScoringUtilTest.java`. No tests for `MatchService`, `MatchTradeService`, `CrashRecoveryService`, or any controller.

#### 🟡 Moderate Debt

11. **`MatchmakingService`, `MatchmakingQueueMonitor`, and `QueueSizeProvider`** are three separate classes for what should be a single cohesive matchmaking module.

12. **`MatchSchedulerService` and `GameCleanupService`** both deal with game lifecycle scheduling — they share concerns but are separate classes.

13. **`CircuitBreakerRegistry`** is a hand-rolled circuit breaker placed in `service/`. Since Resilience4j is not in the pom, this custom impl is fine, but it should live in an `infrastructure/` or `resilience/` package, not alongside domain services.

14. **`RateLimitFilter` in `middleware/`** uses `@Value` fields that are read lazily (after constructor) — the `createBucket(generalRpm)` at request time means bucket parameters change if properties reload, but the bucket is cached with the old parameter. Minor bug.

15. **`GameWebSocketHandler` has a dead helper method** `buildGameState()` annotated with `@SuppressWarnings("unused")`. It's never called.

16. **`GameWebSocketHandler` has a debug handler** `@MessageMapping("/hello") → @SendTo("/topic/greetings")` returning "Hello from server" — obvious development leftover.

17. **`MatchService` has `RANKED_SYMBOLS` array defined mid-class** (between a method body end and the next method), appearing at line 386–390, breaking any coherent reading flow.

18. **`MatchController.java` starts with two blank lines before the package declaration** (lines 1–2). Minor but indicative of messy editing history.

19. **`LeaderboardController.java` starts with a blank line before the package declaration**.

20. **`MarketDataService` has an unbounded `ConcurrentHashMap` cache** with no TTL, no eviction, and no size limit — a memory leak in production.

---

### 1.4 Naming Inconsistencies

| Issue | Current | Should Be |
|---|---|---|
| Controller naming | `PracticeLeaderboardController` | `PracticeLeaderboardController` is fine, but it lives at `/api/leaderboard` while `LeaderboardController` lives at `/api/users`. Paths need reconciliation. |
| Service vs Infrastructure | `CircuitBreakerRegistry` in `service/` | Should be in `infrastructure/resilience/` |
| Candle duplication | `model.Candle` + `dto.Candle` | One canonical `Candle` record in `dto/` |
| `RedisRoomStore` vs `ResilientRedisRoomStore` | Not intuitive which to use | `RedisRoomStore` → `RedisRoomStoreClient`; `ResilientRedisRoomStore` → `RoomStoreAdapter` or similar |
| `MarketDataProvider` interface | In `service/` package | Should be in a `port/` or `provider/` subpackage |
| `GameLogger` utility | In `util/` | Fine, but `GameLogger` name is misleading — it's a structured log helper, not a log appender |
| `PositionSnapshotStore` | In `service/` | It's an infrastructure store — belongs in `infrastructure/` or `store/` |
| `ReadinessScoreService` | In `service/` | Belongs with analytics |
| `HeapPressureGuard` | In `service/` | Belongs in `infrastructure/resilience/` |

---

## 2. Proposed Professional Architecture

```
tradelearn/
│
├── backend/
│   └── src/main/java/com/tradelearn/
│       ├── TradeLearnApplication.java
│       │
│       ├── auth/                          ← Authentication domain
│       │   ├── controller/AuthController.java
│       │   ├── dto/LoginRequest.java, RegisterRequest.java, AuthResponse.java
│       │   ├── security/
│       │   │   ├── JwtUtil.java
│       │   │   ├── JwtAuthenticationFilter.java
│       │   │   ├── JwtAuthenticationEntryPoint.java
│       │   │   ├── CustomUserDetailsService.java
│       │   │   ├── WebSocketAuthInterceptor.java
│       │   │   └── WebSocketChannelInterceptor.java
│       │   └── config/SecurityConfig.java
│       │
│       ├── user/                          ← User domain
│       │   ├── controller/UserController.java
│       │   ├── model/User.java
│       │   ├── repository/UserRepository.java
│       │   ├── dto/UserDTO.java
│       │   └── service/UserService.java
│       │
│       ├── game/                          ← Core multiplayer game domain
│       │   ├── controller/
│       │   │   ├── MatchController.java   (lifecycle: create/join/start/end/cancel)
│       │   │   └── MatchResultController.java (stats, positions, trades)
│       │   ├── model/
│       │   │   ├── Game.java
│       │   │   ├── Trade.java
│       │   │   ├── MatchStats.java
│       │   │   └── PlayerPosition.java    (extracted from MatchTradeService inner class)
│       │   ├── repository/
│       │   │   ├── GameRepository.java
│       │   │   ├── TradeRepository.java
│       │   │   └── MatchStatsRepository.java
│       │   ├── dto/
│       │   │   ├── CreateMatchRequest.java
│       │   │   ├── JoinMatchRequest.java
│       │   │   ├── EndMatchRequest.java
│       │   │   ├── MatchTradeRequest.java
│       │   │   └── MatchResult.java
│       │   └── service/
│       │       ├── MatchLifecycleService.java (split from MatchService: create/join/start/end/abandon)
│       │       ├── MatchScoringService.java   (split from MatchService: ELO, scoring, persistStats)
│       │       ├── MatchTradeService.java     (KEEP, already focused)
│       │       └── MatchQueryService.java     (read-only queries: getMatch, getOpenMatches, etc.)
│       │
│       ├── matchmaking/                   ← Ranked queue domain
│       │   ├── controller/MatchmakingController.java
│       │   ├── dto/PlayerTicket.java
│       │   └── service/
│       │       ├── MatchmakingService.java        (merge: enqueue/dequeue/matchLogic)
│       │       └── MatchmakingMonitor.java        (merge: queue monitoring, scheduler)
│       │
│       ├── market/                        ← Market data domain
│       │   ├── controller/MarketController.java
│       │   ├── dto/Candle.java            (SINGLE canonical candle DTO)
│       │   ├── provider/
│       │   │   ├── MarketDataProvider.java (interface — the port)
│       │   │   └── YahooFinanceProvider.java
│       │   └── service/
│       │       ├── MarketDataService.java  (fix cache; inject interface not impl)
│       │       ├── CandleService.java
│       │       └── HistoricalCandleService.java
│       │
│       ├── leaderboard/                   ← Leaderboard domain
│       │   ├── controller/
│       │   │   ├── LeaderboardController.java    (/api/leaderboard — ranked)
│       │   │   └── PracticeLeaderboardController.java (/api/leaderboard/practice)
│       │   ├── model/LeaderboardEntry.java
│       │   ├── repository/LeaderboardRepository.java
│       │   ├── dto/LeaderboardDTO.java
│       │   └── service/
│       │       ├── LeaderboardService.java
│       │       └── RankService.java
│       │
│       ├── profile/                       ← User profile domain
│       │   ├── controller/ProfileController.java (extracted from LeaderboardController)
│       │   └── service/ProfileService.java
│       │
│       ├── learning/                      ← Learning/quiz domain
│       │   ├── controller/LearningController.java
│       │   ├── model/UserLessonProgress.java
│       │   ├── repository/UserLessonProgressRepository.java
│       │   └── service/LearningService.java
│       │
│       ├── quests/                        ← Quests & achievements domain
│       │   ├── controller/
│       │   │   ├── QuestController.java
│       │   │   └── AchievementController.java
│       │   ├── model/
│       │   │   ├── DailyQuest.java, UserDailyQuest.java
│       │   │   ├── WeeklyChallenge.java, UserWeeklyChallenge.java
│       │   │   └── Achievement.java, UserAchievement.java
│       │   ├── repository/ (all quest/achievement repos)
│       │   └── service/
│       │       ├── QuestService.java
│       │       ├── QuestCleanupService.java
│       │       └── AchievementService.java
│       │
│       ├── social/                        ← Social features domain
│       │   ├── controller/
│       │   │   ├── SocialController.java
│       │   │   └── ChallengeWebSocketController.java
│       │   ├── model/Friendship.java, GameChallenge.java
│       │   ├── repository/FriendshipRepository.java, GameChallengeRepository.java
│       │   ├── dto/FriendDTO.java, ChallengeDTO.java
│       │   └── service/SocialService.java
│       │
│       ├── analytics/                     ← Analytics domain
│       │   ├── controller/AnalyticsController.java
│       │   ├── dto/ (backtest DTOs)
│       │   └── service/
│       │       ├── AnalyticsService.java
│       │       ├── BacktestService.java
│       │       └── ReadinessScoreService.java
│       │
│       ├── simulator/                     ← Solo simulator domain
│       │   ├── controller/SimulatorController.java
│       │   ├── model/Portfolio.java, Holding.java, TradeJournal.java
│       │   ├── repository/ (portfolio/holding/journal repos)
│       │   └── service/SimulatorService.java
│       │
│       ├── tradejournal/                  ← Trade journal domain
│       │   ├── controller/TradeJournalController.java
│       │   ├── model/TradeJournal.java    (moved from simulator)
│       │   ├── repository/TradeJournalRepository.java
│       │   └── service/ (if needed)
│       │
│       ├── websocket/                     ← WebSocket infrastructure
│       │   ├── GameWebSocketHandler.java
│       │   ├── GameBroadcaster.java
│       │   ├── RedisWebSocketRelay.java
│       │   └── config/
│       │       ├── WebSocketConfig.java
│       │       └── WebSocketEventListener.java
│       │
│       ├── infrastructure/                ← Cross-cutting infrastructure
│       │   ├── redis/
│       │   │   ├── config/RedisConfig.java, RedissonConfig.java
│       │   │   ├── room/
│       │   │   │   ├── RoomManager.java
│       │   │   │   ├── RedisRoomStore.java
│       │   │   │   └── ResilientRedisRoomStore.java
│       │   │   └── store/PositionSnapshotStore.java
│       │   ├── resilience/
│       │   │   ├── CircuitBreakerRegistry.java
│       │   │   ├── GracefulDegradationManager.java
│       │   │   ├── DatabaseFailoverHandler.java
│       │   │   ├── CrashRecoveryService.java
│       │   │   ├── StateReconciliationService.java
│       │   │   ├── GameFreezeService.java
│       │   │   └── HeapPressureGuard.java
│       │   ├── scheduling/
│       │   │   ├── MatchSchedulerService.java
│       │   │   └── config/SchedulerConfig.java, AsyncConfig.java
│       │   ├── ratelimit/
│       │   │   ├── TradeRateLimiter.java
│       │   │   └── TradeProcessingPipeline.java
│       │   └── pipeline/
│       │       └── GameMetricsService.java
│       │
│       ├── common/                        ← Shared cross-domain components
│       │   ├── config/WebConfig.java
│       │   ├── controller/HealthController.java
│       │   ├── exception/
│       │   │   ├── GlobalExceptionHandler.java
│       │   │   ├── GameNotFoundException.java
│       │   │   ├── InvalidGameStateException.java
│       │   │   ├── RoomFullException.java
│       │   │   └── TradeValidationException.java
│       │   ├── middleware/
│       │   │   ├── RateLimitFilter.java
│       │   │   ├── RequestCorrelationFilter.java
│       │   │   └── SecurityHeadersFilter.java
│       │   ├── util/
│       │   │   ├── EloUtil.java
│       │   │   ├── ScoringUtil.java
│       │   │   └── GameLogger.java
│       │   └── validation/
│       │       ├── ValidStockSymbol.java, StockSymbolValidator.java
│       │       └── ValidTradeType.java, TradeTypeValidator.java
│       │
│       └── tests/
│           ├── unit/
│           └── integration/
│
├── frontend/
│   └── src/
│       ├── features/                      ← Feature-first organization
│       │   ├── auth/
│       │   │   ├── pages/ (LoginPage, RegisterPage, ForgotPasswordPage)
│       │   │   ├── components/
│       │   │   └── context/AuthContext.js
│       │   ├── game/
│       │   │   ├── pages/ (GamePage, LobbyPage, MatchResultPage, MatchHistoryPage)
│       │   │   ├── components/ (LiveScoreboard, Modal)
│       │   │   └── hooks/useGameSocket.js
│       │   ├── simulator/
│       │   │   ├── pages/ (SimulatorPage, PracticePage, MissionSelectionPage)
│       │   │   └── components/ (all simulator/ subdirectory)
│       │   ├── leaderboard/
│       │   │   ├── pages/LeaderboardPage.jsx
│       │   │   └── components/ (TopTraders, TierBadge)
│       │   ├── learning/
│       │   │   ├── pages/LearnPage.jsx
│       │   │   └── components/ (learn/ subdirectory)
│       │   ├── strategies/
│       │   │   ├── pages/StrategiesPage.jsx
│       │   │   └── components/ (strategies/ subdirectory)
│       │   ├── social/
│       │   │   └── components/ (social/ subdirectory)
│       │   └── profile/
│       │       └── pages/ProfilePage.jsx
│       │
│       ├── shared/                        ← Truly reusable UI components
│       │   ├── components/
│       │   │   ├── Navbar.jsx
│       │   │   ├── Footer.jsx
│       │   │   ├── Modal.jsx
│       │   │   ├── StockChart.jsx
│       │   │   └── DailyCheckinModal.jsx
│       │   └── styles/theme.css
│       │
│       ├── api/                           ← Consolidated API layer
│       │   ├── client.js                 (axios instance, interceptors)
│       │   ├── auth.api.js
│       │   ├── game.api.js
│       │   ├── market.api.js
│       │   ├── leaderboard.api.js
│       │   └── user.api.js
│       │
│       ├── hooks/                         ← Shared hooks
│       │   └── useGameSocket.js
│       │
│       ├── assets/
│       ├── App.jsx
│       └── index.js
│
├── docs/
│   ├── README.md             (improve)
│   ├── architecture.md       (exists — needs update)
│   ├── developer-setup.md    (NEW)
│   ├── deployment.md         (exists — needs update)
│   └── api-reference.md      (NEW)
│
├── scripts/
│   ├── db-migrate.sh
│   └── generate-env.sh
│
├── loadtest/                              (already exists, keep)
│   ├── k6/
│   ├── k8s/
│   └── monitoring/
│
├── docker-compose.yml
├── docker-compose.dev.yml    (NEW — for local dev with hot reload)
└── .github/
    └── workflows/            (CI/CD)
```

---

## 3. File-by-File Refactor Plan

### Backend

#### `service/` package (36 files — the biggest problem)

| File | Action | Destination | Reason |
|---|---|---|---|
| `MatchService.java` | **SPLIT** | `game/service/MatchLifecycleService.java` + `game/service/MatchScoringService.java` + `game/service/MatchQueryService.java` | 879-line God class; 3 distinct responsibilities |
| `MatchTradeService.java` | **MOVE** | `game/service/MatchTradeService.java` | Trade execution — belongs in game domain |
| `MatchSchedulerService.java` | **MOVE** | `infrastructure/scheduling/MatchSchedulerService.java` | Scheduling infrastructure, not domain |
| `MatchmakingService.java` | **MOVE** | `matchmaking/service/MatchmakingService.java` | Own domain |
| `MatchmakingQueueMonitor.java` | **MERGE** | Into `matchmaking/service/MatchmakingService.java` or `matchmaking/service/MatchmakingMonitor.java` | Thin class, only monitors queue size, no standalone reason |
| `QueueSizeProvider.java` | **MERGE** | Into `matchmaking/service/MatchmakingService.java` | 20-line interface wrapper — unnecessary abstraction |
| `RoomManager.java` | **MOVE** | `infrastructure/redis/room/RoomManager.java` | Redis infrastructure |
| `RedisRoomStore.java` | **MOVE** | `infrastructure/redis/room/RedisRoomStore.java` | Redis infrastructure |
| `ResilientRedisRoomStore.java` | **MOVE** | `infrastructure/redis/room/ResilientRedisRoomStore.java` | Redis infrastructure |
| `PositionSnapshotStore.java` | **MOVE** | `infrastructure/redis/store/PositionSnapshotStore.java` | In-memory store, infrastructure |
| `CandleService.java` | **MOVE** | `market/service/CandleService.java` | Market data domain |
| `HistoricalCandleService.java` | **MOVE** | `market/service/HistoricalCandleService.java` | Market data domain |
| `MarketDataService.java` | **MOVE + FIX** | `market/service/MarketDataService.java` | Fix: inject `MarketDataProvider` interface, not `YahooFinanceProvider` directly; fix unbounded cache |
| `MarketDataProvider.java` | **MOVE** | `market/provider/MarketDataProvider.java` | It's a port/interface — should be with its impl |
| `YahooFinanceProvider.java` | **MOVE** | `market/provider/YahooFinanceProvider.java` | Provider implementation |
| `CircuitBreakerRegistry.java` | **MOVE** | `infrastructure/resilience/CircuitBreakerRegistry.java` | Infrastructure |
| `GracefulDegradationManager.java` | **MOVE** | `infrastructure/resilience/GracefulDegradationManager.java` | Infrastructure |
| `DatabaseFailoverHandler.java` | **MOVE** | `infrastructure/resilience/DatabaseFailoverHandler.java` | Infrastructure |
| `CrashRecoveryService.java` | **MOVE** | `infrastructure/resilience/CrashRecoveryService.java` | Infrastructure |
| `StateReconciliationService.java` | **MOVE** | `infrastructure/resilience/StateReconciliationService.java` | Infrastructure |
| `GameFreezeService.java` | **MOVE** | `infrastructure/resilience/GameFreezeService.java` | Infrastructure |
| `HeapPressureGuard.java` | **MOVE** | `infrastructure/resilience/HeapPressureGuard.java` | Infrastructure |
| `TradeProcessingPipeline.java` | **MOVE** | `infrastructure/ratelimit/TradeProcessingPipeline.java` | Infrastructure pipeline |
| `TradeRateLimiter.java` | **MOVE** | `infrastructure/ratelimit/TradeRateLimiter.java` | Infrastructure |
| `GameMetricsService.java` | **MOVE** | `infrastructure/pipeline/GameMetricsService.java` | Metrics/observability |
| `GameCleanupService.java` | **MOVE** | `infrastructure/scheduling/GameCleanupService.java` | Scheduled cleanup |
| `LeaderboardService.java` | **MOVE** | `leaderboard/service/LeaderboardService.java` | Own domain |
| `RankService.java` | **MOVE** | `leaderboard/service/RankService.java` | Own domain |
| `LearningService.java` | **MOVE** | `learning/service/LearningService.java` | Own domain |
| `QuestService.java` | **MOVE** | `quests/service/QuestService.java` | Own domain |
| `QuestCleanupService.java` | **MOVE** | `quests/service/QuestCleanupService.java` | Own domain |
| `AchievementService.java` | **MOVE** | `quests/service/AchievementService.java` | Own domain |
| `SocialService.java` | **MOVE** | `social/service/SocialService.java` | Own domain |
| `AnalyticsService.java` | **MOVE** | `analytics/service/AnalyticsService.java` | Own domain |
| `BacktestService.java` | **MOVE** | `analytics/service/BacktestService.java` | Own domain |
| `ReadinessScoreService.java` | **MOVE** | `analytics/service/ReadinessScoreService.java` | Analytics domain |
| `SimulatorService.java` | **MOVE** | `simulator/service/SimulatorService.java` | Own domain |
| `UserService.java` | **MOVE** | `user/service/UserService.java` | Own domain |

#### `controller/` package (20 files)

| File | Action | Destination | Reason |
|---|---|---|---|
| `AuthController.java` | **MOVE** | `auth/controller/AuthController.java` | Auth domain |
| `UserController.java` | **MOVE** | `user/controller/UserController.java` | User domain |
| `GameController.java` | **DELETE** | — | LEGACY. `POST /api/games/{id}/join` is superseded by `MatchController`. `POST /api/games` duplicates `MatchController.createMatch()` without room setup. Fully obsolete. |
| `MatchController.java` | **SPLIT + MOVE** | `game/controller/MatchController.java` (lifecycle only) + `game/controller/MatchDataController.java` (candle, stats, position endpoints) | Too many endpoint categories in one controller |
| `GameWebSocketController.java` | **MOVE** | `websocket/GameWebSocketController.java` | WebSocket infrastructure |
| `ChallengeWebSocketController.java` | **MOVE** | `social/controller/ChallengeWebSocketController.java` | Social domain |
| `MatchmakingController.java` | **MOVE** | `matchmaking/controller/MatchmakingController.java` | Matchmaking domain |
| `MarketController.java` | **MOVE** | `market/controller/MarketController.java` | Market domain |
| `LeaderboardController.java` | **SPLIT + MOVE** | `leaderboard/controller/LeaderboardController.java` (leaderboard endpoints) + `profile/controller/ProfileController.java` (profile + W/L stats) | Profile logic should not live in `LeaderboardController` |
| `PracticeLeaderboardController.java` | **MOVE + RENAME** | `leaderboard/controller/PracticeLeaderboardController.java` (change path to `/api/leaderboard/practice`) | Inconsistent path (`/api/leaderboard` clashes with ranked) |
| `LearningController.java` | **MOVE** | `learning/controller/LearningController.java` | Learning domain |
| `QuestController.java` | **MOVE** | `quests/controller/QuestController.java` | Quests domain |
| `AchievementController.java` | **MOVE** | `quests/controller/AchievementController.java` | Quests domain |
| `SocialController.java` | **MOVE** | `social/controller/SocialController.java` | Social domain |
| `AnalyticsController.java` | **MOVE** | `analytics/controller/AnalyticsController.java` | Analytics domain |
| `StrategyController.java` | **MOVE** | `analytics/controller/StrategyController.java` | Analytics domain |
| `SimulatorController.java` | **MOVE** | `simulator/controller/SimulatorController.java` | Simulator domain |
| `TradeController.java` | **INVESTIGATE + MOVE** | `game/controller/` or merge into `MatchController` | Overlaps with MatchController `/trade` endpoint — needs audit |
| `TradeJournalController.java` | **MOVE** | `simulator/controller/TradeJournalController.java` or `tradejournal/controller/` | Journal belongs with simulator or its own domain |
| `HealthController.java` | **MOVE** | `common/controller/HealthController.java` | Shared/common |

#### `model/` package

| File | Action | Destination | Reason |
|---|---|---|---|
| `Candle.java` (model) | **DELETE** | — | `dto.Candle` is the canonical representation. The model `Candle` has no JPA annotations and serves no purpose that the DTO doesn't cover. Remove and update all references. |
| `Game.java` | **MOVE** | `game/model/Game.java` | Game domain |
| `Trade.java` | **MOVE** | `game/model/Trade.java` | Game domain |
| `MatchStats.java` | **MOVE** | `game/model/MatchStats.java` | Game domain |
| `PlayerPosition.java` | **MOVE** | `game/model/PlayerPosition.java` | Extract from `MatchTradeService` inner class |
| `User.java` | **MOVE** | `user/model/User.java` | User domain |
| `LeaderboardEntry.java` | **MOVE** | `leaderboard/model/LeaderboardEntry.java` | Leaderboard domain |
| `DailyQuest.java` + `UserDailyQuest.java` + `WeeklyChallenge.java` + `UserWeeklyChallenge.java` | **MOVE** | `quests/model/` | Quests domain |
| `Achievement.java` + `UserAchievement.java` | **MOVE** | `quests/model/` | Quests domain |
| `Friendship.java` + `GameChallenge.java` | **MOVE** | `social/model/` | Social domain |
| `Portfolio.java` + `Holding.java` + `TradeJournal.java` | **MOVE** | `simulator/model/` | Simulator domain |
| `UserLessonProgress.java` | **MOVE** | `learning/model/` | Learning domain |

#### Other notable files

| File | Action | Reason |
|---|---|---|
| `dto/Candle.java` | **KEEP** (canonical) | Delete `model/Candle.java` instead |
| `dto/CandleDto.java` | **DELETE** or **MERGE** | Likely a third candle representation — verify usage and consolidate |
| `socket/RedisWebSocketRelay.java` | **MOVE** | `websocket/RedisWebSocketRelay.java` |
| `socket/GameBroadcaster.java` | **MOVE** | `websocket/GameBroadcaster.java` |
| `socket/GameWebSocketHandler.java` | **MOVE + CLEANUP** | Remove `@MessageMapping("/hello")` debug handler; remove dead `buildGameState()` method |
| `ServerApplication.java` | **RENAME + MOVE** | `TradeLearnApplication.java` at root package — `com.tradelearn` |
| `config/AsyncConfig.java` | **MOVE** | `infrastructure/scheduling/config/AsyncConfig.java` |
| `config/RedisConfig.java` + `RedissonConfig.java` | **MOVE** | `infrastructure/redis/config/` |
| `config/SchedulerConfig.java` | **MOVE** | `infrastructure/scheduling/config/` |
| `config/SecurityConfig.java` | **MOVE** | `auth/config/` |
| `config/WebConfig.java` | **MOVE** | `common/config/` |
| `config/WebSocketConfig.java` + `WebSocketEventListener.java` | **MOVE** | `websocket/config/` |
| `backend/PRODUCTION_ARCHITECTURE.md` | **MOVE** | `docs/architecture.md` (update the existing one) |
| `backend/logs/` | **ADD TO .gitignore** | Log files should never be in source control |

---

### Frontend

| File | Action | Destination | Reason |
|---|---|---|---|
| `utils/api.js` | **RENAME + MOVE** | `api/client.js` | It's the HTTP client setup, not a utility |
| `services/marketApi.js` | **MOVE** | `api/market.api.js` | API layer, not a service |
| `utils/aiTrader.js` | **INVESTIGATE** | Likely simulator-only logic | Check if used in production paths |
| `utils/simulatorData.js` | **MOVE** | `features/simulator/data/simulatorData.js` | Feature-specific |
| `utils/missions.js` | **MOVE** | `features/simulator/utils/missions.js` | Feature-specific |
| `utils/strategyDetector.js` | **MOVE** | `features/strategies/utils/strategyDetector.js` | Feature-specific |
| `utils/skillTier.js` | **MOVE** | `features/leaderboard/utils/skillTier.js` or `shared/utils/` | Depends on usage |
| `data/historicalEvents.js` | **MOVE** | `features/simulator/data/historicalEvents.js` | Feature-specific |
| `components/StockChart.jsx` | **MOVE** | `shared/components/StockChart.jsx` | Reusable |
| `components/Navbar.jsx/.css` | **MOVE** | `shared/components/Navbar.jsx` | Shared layout |
| `components/Footer.jsx/.css` | **MOVE** | `shared/components/Footer.jsx` | Shared layout |
| `components/Modal.jsx/.css` | **MOVE** | `shared/components/Modal.jsx` | Shared UI |
| `components/TierBadge.jsx/.css` | **MOVE** | `features/leaderboard/components/TierBadge.jsx` | Leaderboard feature |
| `components/TopTraders.jsx/.css` | **MOVE** | `features/leaderboard/components/TopTraders.jsx` | Leaderboard feature |
| `components/LiveScoreboard.jsx/.css` | **MOVE** | `features/game/components/LiveScoreboard.jsx` | Game feature |
| `components/DailyCheckinModal.jsx` | **MOVE** | `features/quests/components/DailyCheckinModal.jsx` | Quest feature |
| `components/StockTicker.jsx/.css` | **MOVE** | `shared/components/StockTicker.jsx` | Shared layout |
| `components/simulator/*` | **MOVE** | `features/simulator/components/*` | Feature-specific |
| `components/learn/*` | **MOVE** | `features/learning/components/*` | Feature-specific |
| `components/social/*` | **MOVE** | `features/social/components/*` | Feature-specific |
| `components/strategies/*` | **MOVE** | `features/strategies/components/*` | Feature-specific |
| `context/AuthContext.js` | **MOVE** | `features/auth/context/AuthContext.js` | Feature-specific |
| `hooks/useGameSocket.js` | **KEEP** in `hooks/` or move to `features/game/hooks/` | Used only in game feature |
| `pages/MissionSelectionPage.jsx` | **MOVE** | `features/simulator/pages/` | Mission is part of simulator |
| `pages/TermsPage.jsx`, `PrivacyPage.jsx`, `RiskDisclosurePage.jsx` | **MOVE** | `features/legal/pages/` or `shared/pages/` | Legal content |
| `pages/ForgotPasswordPage.jsx` | **MOVE** | `features/auth/pages/` | Auth feature |
| `App.test.js` | **KEEP + EXPAND** | Root | Add real tests |
| `reportWebVitals.js` | **KEEP** | Root | Performance monitoring |
| `setupTests.js` | **KEEP** | Root | Test setup |

---

## 4. Duplicate / Dead Code Identification

### 4.1 Duplicate Logic

| Location | Duplicate | Severity |
|---|---|---|
| `MatchService.endMatch()` and `MatchService.forceFinishOnAbandon()` | Both contain identical ELO calculation + `persistStats()` block (~40 lines each). The scoring setup is copy-pasted. | 🔴 HIGH |
| `MatchService.createAutoMatch()`, `MatchService.joinMatch()`, `MatchService.requestRematch()` | All three register an identical `TransactionSynchronization.afterCommit()` block that calls `roomManager.createRoom/joinRoom`, `candleService.loadCandles`, `matchSchedulerService.startProgression`, and `positionStore.initializePosition`. | 🔴 HIGH |
| `MatchController.startMatch()`, `MatchController.endMatch()`, `MatchController.deleteMatch()` | The `isParticipant` check (verify user is creator OR opponent) is copy-pasted 3 times. | 🟡 MEDIUM |
| `MatchmakingController.getAuthenticatedUser()` and `MatchController.getAuthenticatedUser()` | Identical private method duplicated in two controllers. | 🟡 MEDIUM |
| `LeaderboardController.getLeaderboard()`, `getTop10Leaderboard()`, `getAllLeaderboard()`, `getLeaderboardByTier()` | `getLeaderboard()` and `getAllLeaderboard()` return identical data. `getTop10Leaderboard()` could be `getLeaderboard(limit=10)`. Three endpoints that could be one with query params. | 🟡 MEDIUM |
| `MarketDataService.getHistoricalData()` cache + `CandleService` internal storage | Two separate caching layers for market data. | 🟡 MEDIUM |
| `MatchTradeService.calculatePosition()` and `CrashRecoveryService.rebuildPositions()` | The recovery service reimplements position calculation from trades — acknowledged in comments but the duplication is still real and divergence-prone. | 🟡 MEDIUM |

### 4.2 Dead Code

| Location | Issue |
|---|---|
| `GameWebSocketHandler.buildGameState()` | Annotated `@SuppressWarnings("unused")` — never called anywhere. **DELETE.** |
| `GameWebSocketHandler.hello()` + `@SendTo("/topic/greetings")` | Debug/test handler. **DELETE.** |
| `GameController.java` entire file | Labeled LEGACY, superseded by `MatchController`. **DELETE.** |
| `MatchService.degradationManager` field | Wired via constructor but never called in `MatchService`. Comment says "retained for Spring bean initialization ordering." This is a code smell — a service should not depend on an unrelated bean for initialization ordering. **DELETE field; use `@DependsOn` annotation instead if ordering matters.** |
| `dto/CandleDto.java` | Likely a third candle representation. **Audit and DELETE if redundant with `dto/Candle.java`.** |

### 4.3 Debugging Leftovers

| Location | Issue |
|---|---|
| `GameWebSocketHandler.java:414` | `@MessageMapping("/hello") @SendTo("/topic/greetings") String hello(String msg)` — test handler |
| `LeaderboardController.java:76` | `/api/users/leaderboard/all` — identical response to `/api/users/leaderboard`, no added value |
| `MatchController.java:1-2` | Two blank lines before package declaration — editor artifact |
| `LeaderboardController.java:1` | One blank line before package declaration |
| Multiple files | `@SuppressWarnings("null")` scattered in production code — indicates code that was written without proper null-safety or was forced to compile. Should be fixed properly, not suppressed. |

### 4.4 Experimental / Abandoned Code

| Item | Evidence |
|---|---|
| `QueueSizeProvider.java` | A 1-method interface wrapping `MatchmakingService.queueSize()`. No tests, no documentation. Appears to be an abstraction created speculatively. |
| `MarketDataProvider.java` | Interface broken by `MarketDataService` hardcoding `YahooFinanceProvider` — the abstraction is incomplete/abandoned. |
| `backend/logs/` directory | Log files committed to the repo — accidental. |
| `loadtest/k8s/` | Kubernetes manifests exist but the project deploys to Render (see `docs/deployment.md`). These are aspirational or abandoned. |

---

## 5. Service Layer Audit

### 5.1 God Classes

| Class | Lines | Problem |
|---|---|---|
| `MatchService` | **879 lines** | Creates matches, joins matches, auto-creates matches, starts matches, ends matches, calculates ELO, persists stats, triggers quest updates, manages TransactionSynchronization, interfaces with 12+ dependencies. **Must be split.** |
| `RoomManager` | **614 lines** | Session tracking, room lifecycle, scheduler ownership, reconnection timers, ready-up, phase management, diagnostics. **Defensible** as an orchestrator but the dual local/Redis fallback pattern makes it complex. |

### 5.2 Services That Should Be Merged

| Services | Merge Into | Reason |
|---|---|---|
| `MatchmakingService` + `MatchmakingQueueMonitor` + `QueueSizeProvider` | `MatchmakingService` + `MatchmakingMonitor` (2 classes max) | `QueueSizeProvider` is a 1-method wrapper; `MatchmakingQueueMonitor` is a thin scheduled wrapper |
| `AnalyticsService` + `ReadinessScoreService` | `AnalyticsService` | `ReadinessScoreService` is an analytics concern |
| `GameCleanupService` + `QuestCleanupService` | Can stay separate, but both should move to `infrastructure/scheduling/` | Not merge candidates per se, but related by type |

### 5.3 Services That Should Be Split

| Service | Split Into | Reason |
|---|---|---|
| `MatchService` (879 LOC) | `MatchLifecycleService` (create/join/start/abandon/delete) + `MatchScoringService` (ELO, scoring, persistStats) + `MatchQueryService` (getMatch, getOpenMatches, getUserMatches) | SRP violation — too many responsibilities |

### 5.4 Circular Dependencies

| Circular Dep | Resolution |
|---|---|
| `MatchService` ↔ `MatchTradeService` | Resolved with `@Lazy` on `MatchTradeService` — acceptable workaround, but splitting `MatchService` will eliminate the cycle naturally |
| `MatchService` ↔ `QuestService` | Resolved with `@Lazy` on `QuestService` — same as above |
| `GracefulDegradationManager` ↔ `DatabaseFailoverHandler` | Resolved with setter injection via `@PostConstruct` — explicit bidirectional wiring. Fragile; should use Spring events or an observer interface |
| `GracefulDegradationManager` ↔ `GameFreezeService` | Resolved with setter injection via `@PostConstruct` — same fragility |

### 5.5 Unnecessary Abstractions

| Abstraction | Issue |
|---|---|
| `MarketDataProvider` interface | Broken — `MarketDataService` doesn't inject the interface, it injects `YahooFinanceProvider` directly. Interface is vestigial. Either fix the injection or remove the interface. |
| `QueueSizeProvider` | One-method interface wrapping `MatchmakingService.queueSize()`. No other implementations exist. No tests. Delete it. |

---

## 6. Backend Audit

### 6.1 Controller Structure

| Issue | Severity |
|---|---|
| **20 controllers** in a single flat package | 🔴 Needs domain split |
| `GameController` is fully superseded by `MatchController` | 🔴 Dead code |
| `MatchController` mixes game lifecycle, candle management, trade placement, stats, diagnostics | 🔴 Too many concerns |
| `LeaderboardController` contains profile business logic | 🟡 SRP violation |
| `getAuthenticatedUser()` copy-pasted across controllers | 🟡 Should be a shared `SecurityUtil` or `@ControllerAdvice` |
| No `@PreAuthorize` annotations — all auth done in controller bodies | 🟡 Should use Spring Security method security |
| `LeaderboardController` and `PracticeLeaderboardController` serve different paths for "leaderboard" | 🟡 Inconsistent API design |

### 6.2 WebSocket Architecture

The WebSocket architecture is actually well-designed:
- STOMP over SockJS via Spring WebSocket
- `GameWebSocketHandler` handles: trade, position query, ready-up, rejoin
- `GameBroadcaster` handles outbound broadcasts (to game topic, to user, error routing)
- `RedisWebSocketRelay` enables cross-instance broadcasting via Redis Pub/Sub
- JWT auth at handshake time via `WebSocketAuthInterceptor`
- Channel-level auth via `WebSocketChannelInterceptor`

**Issues:**
- Debug `hello` handler is a security surface that should be removed
- `GameWebSocketHandler` has 11 constructor dependencies — bloated
- Session registration happens both in `wsEventListener.registerSession()` and in `handleTrade` — duplicated call path

### 6.3 Redis Usage

| Usage | Pattern | Assessment |
|---|---|---|
| Room state | Hash + Set | ✅ Correct |
| Scheduler ownership | SETNX with TTL | ✅ Correct |
| Matchmaking queue | Redis ZSET (score=rating) | ✅ Correct |
| Rematch consent | Lua script + TTL | ✅ Excellent — race-free |
| Position snapshots | JVM in-memory `ConcurrentHashMap` | ⚠️ Not Redis — a restart loses all position state (handled by `CrashRecoveryService`) |
| Market data cache | JVM in-memory `ConcurrentHashMap` (unbounded) | 🔴 Memory leak — no TTL, no eviction, no size limit |
| Rate limiting | Bucket4j JVM-local | ⚠️ Per-instance only — not shared across cluster |

### 6.4 Database Layer

| Issue | Severity |
|---|---|
| Both MySQL AND PostgreSQL drivers in `pom.xml` | 🟡 One is unused — adds jar bloat and potential confusion |
| `Game.getCreatedAt()` returns `java.sql.Timestamp` | 🟡 Should be `java.time.LocalDateTime` or `Instant` |
| `LeaderboardController.getUserProfile()` runs multiple unbounded queries (all games for user, all users for rank calculation) in a single request | 🔴 Performance — `findAllByOrderByRatingDesc()` for rank calculation is O(n) in memory |
| Status fields stored as raw Strings (`"ACTIVE"`, `"WAITING"`, etc.) | 🟡 Should be `@Enumerated(EnumType.STRING)` JPA enums |
| No Flyway or Liquibase migrations | 🔴 Schema is managed ad-hoc |
| `TradeRepository.findByGameIdAndUserId()` lacks pagination | 🟡 Unbounded for high-volume games |

### 6.5 Repository Pattern

The repository pattern is used correctly with Spring Data JPA. Issues:
- `LeaderboardController` injects `UserRepository`, `GameRepository`, `MatchStatsRepository` directly — repositories should only be accessed through service layers
- Some controllers inject repositories directly (bypassing the service layer)

### 6.6 Package Organization

The root package is `com.tradelearn.server` — the `server` suffix is an implementation detail that leaks into the package name. Should be `com.tradelearn` with the server application in `com.tradelearn.TradeLearnApplication`.

---

## 7. Frontend Audit

### 7.1 Page Organization

| Issue | Severity |
|---|---|
| 20+ pages in a flat `pages/` directory | 🟡 Hard to navigate; needs feature grouping |
| `MissionSelectionPage` is in `pages/` but `MissionDashboard` is in `components/simulator/` | 🟡 Inconsistent — both should be in `features/simulator/` |
| Legal pages (`TermsPage`, `PrivacyPage`, `RiskDisclosurePage`) have no CSS companion files | 🟡 Inconsistent styling approach |
| `LegalPages.css` exists but `TermsPage.jsx`, `PrivacyPage.jsx`, `RiskDisclosurePage.jsx` don't import it | 🔴 Dead CSS file |
| `LoginPage.jsx` and `RegisterPage.jsx` both lack a paired `.css` file but `AuthForm.css` exists | 🟡 Naming disconnect |

### 7.2 Component Hierarchy

| Issue | Severity |
|---|---|
| `components/` root has flat files AND `simulator/`, `learn/`, `social/`, `strategies/` subdirectories | 🟡 Inconsistent depth — some features use subdirs, others dump in root |
| `StockChart.jsx` (no CSS) alongside `CandlestickChart.jsx` (with CSS) in different component subdirectories | 🟡 Possible duplication — both render stock charts |
| `components/simulator/MissionDashboard.jsx` is a full page rendered as a route target, not a component | 🔴 Architectural violation — pages should live in `pages/` |
| `components/simulator/MissionDebriefModal.jsx` and `components/simulator/ReflectionModal.jsx` — two modal components without CSS | 🟡 Inconsistent |
| `components/simulator/PerformanceChart.css` exists but no `PerformanceChart.jsx` was found | 🔴 Orphaned CSS file |

### 7.3 API Layer

| Issue | Severity |
|---|---|
| Two API patterns coexist: raw `fetch()` (in `marketApi.js`) and configured `axios` instance (in `api.js`) | 🟡 Should standardize on axios |
| API calls scattered across page components directly | 🟡 Should be centralized in `api/` service layer |
| `utils/api.js` is named as a utility but contains axios client setup, interceptors, and API methods — confusing role | 🟡 Naming/organization |
| No API type definitions or response schemas | 🟡 Runtime errors only discovered at runtime |

### 7.4 State Management

| Issue | Severity |
|---|---|
| Only `AuthContext` is global state | ✅ Appropriate for current scale |
| No centralized game/socket state — `useGameSocket.js` is the only hook | 🟡 As features grow, more shared state will be needed |
| `localStorage` used for token storage (security consideration for XSS) | 🟡 Acceptable for a trading simulation game, but document the decision |

### 7.5 Other Frontend Issues

| Issue | Severity |
|---|---|
| `App.js` imports `MissionDashboard` as a route-level component (it's in `components/`) | 🟡 Architectural inconsistency |
| Comment `// 1. Import` on SimulatorPage import in `App.js` | 🟡 Debug comment leftover |
| `frontend/logo.svg` in the root frontend directory | 🟡 Should be in `assets/` |
| `frontend/.env` committed to the repository | 🔴 Security risk — even if it only contains `REACT_APP_API_URL`, `.env` files should never be committed |
| React CRA (`react-scripts`) is being used — CRA is officially deprecated | 🟡 Should migrate to Vite for new development velocity |

---

## 8. Documentation Audit

### Current State

| Document | Location | Quality | Issues |
|---|---|---|---|
| `README.md` | Root | Medium | Covers features but no setup instructions for backend. No architecture diagram. |
| `CONTRIBUTING.md` | Root | Good | Has contribution guidelines |
| `docs/architecture.md` | `docs/` | Good start | Covers WebSocket + Redis architecture in detail but doesn't describe all domains |
| `docs/deployment.md` | `docs/` | Good | Render-specific deployment |
| `backend/PRODUCTION_ARCHITECTURE.md` | `backend/` | Excellent | Very detailed — but misplaced (should be in `docs/`) |
| `frontend/README.md` | `frontend/` | CRA boilerplate | Not customized for this project at all |
| No API reference | — | MISSING | No endpoint documentation |
| No developer setup guide | — | MISSING | New developer can't onboard without asking |

### 8.1 README Improvements Needed

```markdown
# TradeLearn

## Quick Start (2 minutes)
...

## System Requirements
- Java 21
- Node 20+
- MySQL 8 / PostgreSQL 15
- Redis 7 (optional — see docs/developer-setup.md for local mode)

## Architecture Overview
[Diagram]

## Documentation
- [Developer Setup](docs/developer-setup.md)
- [Architecture](docs/architecture.md)
- [API Reference](docs/api-reference.md)
- [Deployment](docs/deployment.md)
```

### 8.2 Missing: `developer-setup.md`

Should cover:
- Prerequisites
- Clone and build backend (`mvn clean package -DskipTests`)
- Database setup (schema DDL or migration command)
- Environment variables (reference `.env.example`)
- Running with Redis (`redis.enabled=true`) vs without
- Running frontend (`npm install && npm start`)
- WebSocket testing with wscat or Postman

### 8.3 Missing: `api-reference.md`

All REST endpoints with request/response shapes, authentication requirements, and examples.

---

## 9. Migration Roadmap

> [!IMPORTANT]
> Each phase is independently mergeable. Never refactor + add features in the same PR.

### Phase 1 — Safe Cleanup (Zero Risk)

**Goal:** Remove dead code and debugging leftovers without touching any logic.

**Duration estimate:** 1–2 days

- [ ] **DELETE** `GameController.java` entirely
- [ ] **DELETE** `model/Candle.java` (remove `model.Candle` imports, replace with `dto.Candle`)
- [ ] **DELETE** `dto/CandleDto.java` (after auditing usages — consolidate to `dto.Candle`)
- [ ] **DELETE** `GameWebSocketHandler.buildGameState()` dead method
- [ ] **DELETE** `GameWebSocketHandler.hello()` debug handler + `@SendTo("/topic/greetings")`
- [ ] **REMOVE** `QueueSizeProvider.java` (inline the one call into `MatchmakingService`)
- [ ] **REMOVE** `MatchService.degradationManager` field (use `@DependsOn` annotation instead)
- [ ] **FIX** `frontend/.env` — add to `.gitignore`, remove from repo, document in `.env.example`
- [ ] **FIX** `backend/logs/` — add to `.gitignore`, remove from repo
- [ ] **FIX** `MarketDataService` — inject `MarketDataProvider` interface (not `YahooFinanceProvider`)
- [ ] **FIX** `LeaderboardController.getAllLeaderboard()` — DELETE, it's identical to `getLeaderboard()`
- [ ] **REMOVE** debug comments in `App.js` (`// 1. Import`)
- [ ] **REMOVE** blank lines before package declarations in `MatchController.java`, `LeaderboardController.java`
- [ ] **ADD** `backend/logs/` and `frontend/.env` to `.gitignore`
- [ ] **MOVE** `backend/PRODUCTION_ARCHITECTURE.md` → `docs/architecture.md` (update existing)

---

### Phase 2 — Structural Reorganization

**Goal:** Reorganize packages by domain without changing logic.

**Duration estimate:** 3–5 days

- [ ] **CREATE** domain package structure in backend
- [ ] **MOVE** all service files to their domain packages (36 files) — **one domain at a time per PR**
  - PR 1: `user/` + `auth/`
  - PR 2: `market/` + `market/provider/`
  - PR 3: `leaderboard/` + `profile/`
  - PR 4: `quests/` + `social/`
  - PR 5: `learning/` + `analytics/` + `simulator/`
  - PR 6: `matchmaking/`
  - PR 7: `infrastructure/` (resilience, redis, scheduling, ratelimit)
  - PR 8: `game/` domain
  - PR 9: `websocket/` + `common/`
- [ ] **MOVE** all controller files to domain packages (same PR order as above)
- [ ] **MOVE** all model/repository/dto files to domain packages
- [ ] **MOVE** `ServerApplication.java` → `TradeLearnApplication.java` at `com.tradelearn`
- [ ] **REORGANIZE** frontend into `features/` + `shared/` + `api/` structure
  - Move pages into feature directories
  - Move components into feature directories
  - Consolidate API layer into `api/` directory
  - Move auth context into `features/auth/`

---

### Phase 3 — Service Consolidation

**Goal:** Fix God classes, eliminate duplicates, and clean up circular dependencies.

**Duration estimate:** 3–5 days

> [!WARNING]
> This phase requires careful testing. Run the full test suite (once written) before merging.

- [ ] **SPLIT** `MatchService` (879 LOC) into:
  - `MatchLifecycleService` — create, join, start, autoMatch, requestRematch, deleteGame
  - `MatchScoringService` — endMatch, forceFinishOnAbandon, persistStats (eliminate ELO duplication)
  - `MatchQueryService` — getMatch, getOpenMatches, getActiveMatches, getUserMatches
- [ ] **EXTRACT** repeated `TransactionSynchronization.afterCommit()` pattern in MatchService into a private helper `scheduleAfterCommit(Runnable)` or a shared `GameLifecycleSupport` component
- [ ] **EXTRACT** `isParticipant()` check into a shared `MatchAuthorizationService` or Spring Security `@PreAuthorize`
- [ ] **EXTRACT** `getAuthenticatedUser()` pattern into a shared base controller or utility
- [ ] **MERGE** `MatchmakingQueueMonitor` into `MatchmakingService` (or a thin `MatchmakingMonitor` companion)
- [ ] **FIX** `GracefulDegradationManager` ↔ `DatabaseFailoverHandler` circular dep — use Spring `ApplicationEvent`/`ApplicationListener` pattern instead of setter injection
- [ ] **FIX** `GracefulDegradationManager` ↔ `GameFreezeService` circular dep — same approach
- [ ] **FIX** `MarketDataService` unbounded cache — add Caffeine cache or bounded `LinkedHashMap(maxSize, FIFO)` with TTL
- [ ] **FIX** `LeaderboardController.getUserProfile()` — move all business logic to `ProfileService`; fix O(n) rank calculation with a database-side `ROW_NUMBER()` query
- [ ] **FIX** `PlayerPosition` — extract from `MatchTradeService` inner class to a proper `game/model/PlayerPosition.java`
- [ ] **CONSOLIDATE** `LeaderboardController.getLeaderboard()` and `getTop10Leaderboard()` into one endpoint with pagination or a `limit` query param
- [ ] **FIX** `TradeController` vs `MatchController.placeTrade()` overlap — investigate and eliminate one

---

### Phase 4 — Production Hardening

**Goal:** Add tests, observability, schema management, and production safety.

**Duration estimate:** 1–2 weeks ongoing

> [!CAUTION]
> Do not skip this phase before investor demos or production traffic.

- [ ] **ADD** Flyway or Liquibase database migration management
- [ ] **ADD** `@Enumerated(EnumType.STRING)` JPA enums for all status fields (`"ACTIVE"`, `"WAITING"`, etc.)
- [ ] **ADD** unit tests for `MatchLifecycleService`, `MatchScoringService`, `MatchTradeService` (critical business logic)
- [ ] **ADD** integration tests for match lifecycle happy path + edge cases
- [ ] **ADD** tests for `CrashRecoveryService` (the position rebuild logic)
- [ ] **ADD** tests for `RoomManager` (room create/join/disconnect/phase transitions)
- [ ] **FIX** MySQL + PostgreSQL dual driver — pick one, remove the other from `pom.xml`
- [ ] **FIX** all `java.sql.Timestamp` → `java.time.LocalDateTime` / `Instant`
- [ ] **FIX** HTTP rate limiter (`RateLimitFilter`) — the bucket config at `@Value` read time vs bucket creation time issue; or migrate to Resilience4j RateLimiter
- [ ] **ADD** Caffeine as a proper cache for `MarketDataService`
- [ ] **ADD** Redis-backed rate limiting for cross-instance protection (Bucket4j Redis extension)
- [ ] **ADD** `developer-setup.md` and `api-reference.md`
- [ ] **MIGRATE** frontend from CRA to Vite
- [ ] **ADD** `docker-compose.dev.yml` for local development
- [ ] **REVIEW** `backend/loadtest/k8s/` — either complete the Kubernetes setup or delete and document Render-only deployment

---

## Summary Scorecard

| Area | Current Score | Target Score |
|---|---|---|
| Package organization | 3/10 | 9/10 |
| Service cohesion | 4/10 | 9/10 |
| Dead code | 4/10 | 9/10 |
| Test coverage | 1/10 | 7/10 |
| Documentation | 4/10 | 8/10 |
| Controller cleanliness | 4/10 | 9/10 |
| Frontend organization | 5/10 | 8/10 |
| API layer | 5/10 | 8/10 |
| Redis architecture | 8/10 | 9/10 |
| WebSocket architecture | 7/10 | 9/10 |
| Resilience design | 8/10 | 9/10 |
| Schema management | 2/10 | 8/10 |

**Overall: 5/10 → Target: 9/10**

The core game logic and infrastructure design (Redis room store, circuit breakers, crash recovery, WebSocket relay) are genuinely impressive and production-quality. The problem is organization: all this good code is hidden inside a `service/` junk drawer that makes the codebase look far less professional than it is. The refactor is primarily a reorganization effort, not a rewrite.
