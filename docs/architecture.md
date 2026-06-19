# TradeLearn — System Architecture

## Overview

TradeLearn is a full-stack competitive trading simulation platform built on Java 21 / Spring Boot 3.2 (backend) and React 19 (frontend), communicating over REST and WebSocket (STOMP/SockJS over Redis Pub/Sub for multi-instance support).

---

## Backend Domain Package Structure

The backend follows a **domain-driven package layout**. Each domain owns its controllers, services, repositories, and models in one place — making it easy to find, change, and test related code together.

```
com.tradelearn.server/
│
├── auth/                     # JWT authentication & Spring Security
│   ├── config/               #   SecurityConfig
│   ├── controller/           #   AuthController
│   └── security/             #   JwtUtil, JwtAuthenticationFilter,
│                             #   WebSocketAuthInterceptor, ...
│
├── game/                     # Multiplayer match engine
│   ├── controller/           #   MatchController, TradeController
│   ├── model/                #   Game, Trade, MatchStats, PlayerPosition
│   ├── repository/           #   GameRepository, TradeRepository, MatchStatsRepository
│   └── service/
│       ├── MatchService           (façade — delegates to the three below)
│       ├── MatchLifecycleService  (create/join/start/cancel)
│       ├── MatchScoringService    (end/abandon/ELO/rematch)
│       ├── MatchQueryService      (read-only queries)
│       ├── MatchTradeService      (trade execution, position snapshots)
│       └── TradeService           (simulator trade helpers)
│
├── market/                   # Yahoo Finance proxy & candle management
│   ├── controller/           #   MarketDataController
│   ├── provider/             #   MarketDataProvider (interface), YahooFinanceProvider
│   └── service/              #   CandleService, HistoricalCandleService,
│                             #   MarketDataService (LRU-cached, max 200 entries)
│
├── matchmaking/              # Distributed Redis-backed ELO matchmaking
│   ├── controller/           #   MatchmakingController
│   └── service/              #   MatchmakingService (Redis ZSET + Lua + Redisson lock),
│                             #   MatchmakingQueueMonitor (Micrometer gauge)
│
├── infrastructure/           # Cross-cutting platform concerns
│   ├── redis/
│   │   ├── config/           #   RedisConfig
│   │   ├── room/             #   RoomManager, RedisRoomStore, ResilientRedisRoomStore
│   │   └── store/            #   PositionSnapshotStore
│   ├── resilience/           #   CircuitBreakerRegistry, GracefulDegradationManager,
│   │                         #   GameFreezeService, DatabaseFailoverHandler,
│   │                         #   CrashRecoveryService, StateReconciliationService
│   ├── scheduling/           #   MatchSchedulerService, GameCleanupService
│   ├── ratelimit/            #   TradeRateLimiter, TradeProcessingPipeline
│   └── pipeline/             #   GameMetricsService
│
├── websocket/                # STOMP WebSocket layer
│   ├── config/               #   WebSocketConfig
│   ├── GameWebSocketHandler  #   STOMP message routing + auth validation
│   ├── GameWebSocketController
│   ├── GameBroadcaster       #   sendToGame(), sendToUser(), broadcastLobbyUpdate()
│   └── RedisWebSocketRelay   #   Redis Pub/Sub → STOMP broadcast (multi-instance)
│
├── user/                     # User account management
│   ├── controller/           #   UserController
│   ├── model/                #   User
│   ├── repository/           #   UserRepository
│   └── service/              #   UserService
│
├── leaderboard/              # Rankings, user profiles
│   ├── controller/           #   LeaderboardController, PracticeLeaderboardController
│   ├── model/                #   LeaderboardEntry
│   ├── repository/           #   LeaderboardRepository
│   └── service/              #   LeaderboardService, RankService
│
├── social/                   # Friends & challenge system
│   ├── controller/           #   SocialController, ChallengeWebSocketController
│   ├── model/                #   Friendship, GameChallenge
│   ├── repository/           #   FriendshipRepository, GameChallengeRepository
│   └── service/              #   SocialService
│
├── quests/                   # Daily quests, weekly challenges, achievements
│   ├── controller/           #   QuestController, AchievementController
│   ├── model/                #   DailyQuest, WeeklyChallenge, Achievement, ...
│   ├── repository/           #   (6 repositories)
│   └── service/              #   QuestService, QuestCleanupService, AchievementService
│
├── simulator/                # Practice mode portfolio simulation
│   ├── controller/           #   SimulatorController, TradeJournalController
│   ├── model/                #   Portfolio, Holding, TradeJournal
│   ├── repository/           #   PortfolioRepository, HoldingRepository, TradeJournalRepository
│   └── service/              #   SimulatorService
│
├── analytics/                # Strategy analysis, backtesting, readiness score
│   ├── controller/           #   AnalyticsController, StrategyController
│   └── service/              #   AnalyticsService, BacktestService, ReadinessScoreService
│
├── learning/                 # Lesson progress tracking
│   ├── controller/           #   LearningController
│   ├── model/                #   UserLessonProgress
│   ├── repository/           #   UserLessonProgressRepository
│   └── service/              #   LearningService
│
├── common/                   # Shared, domain-agnostic concerns
│   ├── config/               #   WebConfig (CORS)
│   ├── controller/           #   HealthController
│   ├── exception/            #   GlobalExceptionHandler + custom exceptions
│   ├── middleware/           #   RateLimitFilter, SecurityHeadersFilter, RequestCorrelationFilter
│   ├── util/                 #   EloUtil, ScoringUtil, GameLogger
│   └── validation/           #   @ValidStockSymbol, @ValidTradeType validators
│
└── dto/                      # Shared request/response DTOs (no entity leakage)
    # CreateMatchRequest, MatchResult, TradeRequest, Candle, CandleDto, ...
```

---

## Frontend Feature Structure

```
frontend/src/
│
├── api/                      # HTTP client & domain API modules
│   ├── client.js             #   Canonical axios instance (auth + error interceptors)
│   ├── auth.api.js           #   register(), login(), forgotPassword()
│   ├── game.api.js           #   createMatch(), joinMatch(), matchmakingQueue, ...
│   ├── leaderboard.api.js    #   getLeaderboard(), getUserProfile(), ...
│   ├── market.api.js         #   fetchMarketHistory(), fetchSymbols()
│   ├── user.api.js           #   dailyCheckin(), fetchDailyQuests(), ...
│   └── marketApi.js          #   Legacy (re-exports from market.api.js)
│
├── features/                 # Feature-grouped pages & components
│   ├── auth/                 #   Login, Register, ForgotPassword, AuthContext
│   ├── game/                 #   GamePage, MatchResultPage
│   ├── simulator/            #   SimulatorPage, MissionSelectionPage, MissionDashboard
│   ├── matchmaking/          #   LobbyPage, CreateGameForm
│   ├── leaderboard/          #   LeaderboardPage, TierBadge
│   ├── dashboard/            #   HomePage, ProfilePage, MatchHistoryPage
│   ├── social/               #   FriendsPanel, ChallengeListener
│   ├── practice/             #   PracticePage
│   ├── strategies/           #   StrategiesPage
│   ├── learn/                #   Learning modules
│   ├── legal/                #   Terms, Privacy, RiskDisclosure
│   └── quests/               #   Quest/Achievement UI
│
├── layout/                   # Global layout components
│   └── components/           #   Navbar, Footer, Modal, StockTicker, Hero
│
├── hooks/                    # Custom React hooks
│   └── useGameSocket.js      #   STOMP WebSocket lifecycle manager
│
└── utils/                    # Feature-agnostic utilities
    ├── api.js                #   Legacy bridge → re-exports from api/client.js
    ├── skillTier.js
    ├── strategyDetector.js
    ├── simulatorData.js
    ├── missions.js
    └── aiTrader.js
```

---

## Multiplayer Match Lifecycle

```
User A creates game (REST POST /api/match)
    → MatchLifecycleService.createMatch()
    → DB: Game{status=WAITING}
    → RoomManager.createRoom()
    → User A is on GamePage, WebSocket connected

User B joins game (REST POST /api/match/{id}/join)
    → MatchLifecycleService.joinMatch() [atomic CAS: WAITING→ACTIVE]
    → @Transactional.afterCommit():
        - RoomManager.joinRoom()
        - CandleService.loadCandles()
        - MatchSchedulerService.startProgression()
        - PositionSnapshotStore.initializePosition() × 2
        - GameBroadcaster.sendToGame("started")

Candle ticker (every 5 seconds)
    → MatchSchedulerService (distributed SETNX lock ensures only 1 scheduler)
    → CandleService.getCurrentCandle()
    → GameBroadcaster → /topic/game/{id}/candle → both clients

Trade (WebSocket STOMP)
    → /app/game/{id}/trade
    → GameWebSocketHandler
    → TradeProcessingPipeline (async queue)
    → MatchTradeService.executeTrade()
    → PositionSnapshotStore update
    → GameBroadcaster → /topic/game/{id}/scoreboard

Match ends (time runs out or all candles consumed)
    → MatchSchedulerService triggers endMatch
    → MatchScoringService.endMatch()
    → ScoringUtil.calculate() for both players
    → EloUtil.calculateNewRating() for both players
    → DB: Game{status=FINISHED}, MatchStats saved
    → GameBroadcaster → /topic/game/{id}/finished
    → Both clients redirect to /match/{id}/result
```

---

## Redis Key Space

| Key Pattern | TTL | Purpose |
|-------------|-----|---------|
| `room:{gameId}:phase` | 2h | Game phase (WAITING/ACTIVE/FINISHED) |
| `room:{gameId}:players` | 2h | Set of connected player IDs |
| `room:{gameId}:disconnected` | 2h | Set of disconnected player IDs (grace period) |
| `room:{gameId}:scheduler` | 30s (rolling) | Distributed scheduler ownership lock |
| `candles:{gameId}` | 30m | Serialized candle list for the game |
| `position:{gameId}:{userId}` | 2h | Player position snapshot (equity, drawdown, trades) |
| `rematch:{gameId}` | 120s | Rematch consent (atomic Lua set-if-absent) |
| `rl:{userId}` | 1m | Rate limiter token bucket |
| `matchmaking:queue` | — | ZSET: score=rating, member=userId |
| `matchmaking:ticket:{userId}` | 180s | Player metadata hash (username, rating, joinTime) |

---

## Resilience Patterns

### Redis Circuit Breaker (`ResilientRedisRoomStore`)

Wraps `RedisRoomStore` with a custom circuit breaker:
- **Closed** → all calls go to Redis normally
- **Open** (after 5 consecutive failures, 30s cooldown) → fallback to in-memory `shadowRooms`
- **Half-Open** → probe allowed; success → Close, failure → stay Open

### Transactional Side Effects (`afterCommit`)

All Redis/WebSocket mutations are registered as `TransactionSynchronization.afterCommit()` hooks:
- Side effects only fire if the DB transaction commits successfully
- No stale Redis state if DB rolls back

### Trade Processing Pipeline (`TradeProcessingPipeline`)

Trades arrive on STOMP inbound threads and are immediately submitted to an async `ExecutorService` queue, preventing slow trade processing from blocking WebSocket thread pools.

### GracefulDegradationManager

Centralised state machine coordinating all disaster recovery:
```
NORMAL → DEGRADED_REDIS (Redis down, local fallback)
NORMAL → DEGRADED_DB (DB down, trades suspended)
DEGRADED_* + other failure → FROZEN (all games paused)
FROZEN → RECOVERING → NORMAL (reconciliation)
```

---

## Authentication Flow

```
POST /api/auth/register  →  BCrypt hash password  →  DB: User saved
POST /api/auth/login     →  verify password  →  JWT issued (24h default)

Client stores JWT in localStorage (key: tradelearn_token)
All REST requests: Authorization: Bearer <jwt>
WebSocket: token passed as ?token= query param on SockJS URL
```

JWT validation happens in:
- `JwtAuthenticationFilter` — for REST requests
- `WebSocketAuthInterceptor` + `WebSocketChannelInterceptor` — for STOMP frames

---

## Scoring Formula

```java
double profitComponent   = (finalEquity - startingBalance) / startingBalance;
double riskComponent     = 1.0 - Math.min(maxDrawdown / 0.20, 1.0);
double accuracyComponent = totalTrades > 0 ? (double) profitableTrades / totalTrades : 0.5;

double score = (0.60 * profitComponent)
             + (0.20 * riskComponent)
             + (0.20 * accuracyComponent);
```

---

## ELO Calculation

Standard ELO with K-factor = 32:

```java
double expectedScore = 1.0 / (1.0 + Math.pow(10, (opponentRating - myRating) / 400.0));
int newRating = (int) Math.round(myRating + 32 * (actualScore - expectedScore));
```

Where `actualScore` is `1.0` (win), `0.5` (draw), or `0.0` (loss).
