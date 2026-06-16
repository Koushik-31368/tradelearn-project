# TradeLearn — System Architecture

## Overview

TradeLearn is a full-stack competitive trading simulation platform built on Java 21 / Spring Boot 3.2 (backend) and React 19 (frontend), communicating over REST and WebSocket.

---

## Layers

```
┌──────────────────────────────────────────────────────────────────┐
│                       Frontend (React SPA)                       │
│                                                                  │
│  pages/          — full-page route components                    │
│  components/     — reusable UI components (feature-grouped)      │
│  hooks/          — useGameSocket (STOMP lifecycle manager)       │
│  context/        — AuthContext (JWT + user state)               │
│  services/       — marketApi.js (backend HTTP calls)            │
│  utils/          — api.js, skillTier.js, strategyDetector.js    │
│  styles/         — theme.css (single design token source)       │
└──────────────────────────┬──────────────────────────────────────┘
                           │
               REST (Axios) │  WebSocket (STOMP/SockJS)
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                   Backend (Spring Boot)                         │
│                                                                  │
│  controller/     — REST endpoints (thin, delegate to service)   │
│  service/        — business logic (stateless where possible)    │
│  repository/     — Spring Data JPA (PostgreSQL)                 │
│  socket/         — WebSocket handlers + GameBroadcaster         │
│  security/       — JWT filter, WebSocket auth interceptors      │
│  middleware/     — Rate limiting, CORS headers, correlation ID  │
│  config/         — Spring beans (Security, WebSocket, Redis)    │
│  dto/            — Request/response shapes (no entity leakage)  │
│  exception/      — Custom exceptions + GlobalExceptionHandler   │
│  util/           — EloUtil, ScoringUtil, GameLogger             │
│  validation/     — Custom @ValidStockSymbol, @ValidTradeType    │
└──────────────┬─────────────────────────────┬────────────────────┘
               │                             │
        JPA (Hibernate)                Spring Data Redis
               │                             │
    ┌──────────▼─────────┐       ┌───────────▼──────────┐
    │     PostgreSQL      │       │        Redis          │
    │                     │       │                       │
    │  users              │       │  room:{gameId}        │
    │  games              │       │  candle:{gameId}      │
    │  trades             │       │  position:{gameId}    │
    │  match_stats        │       │  scheduler:{gameId}   │
    │  achievements       │       │  rematch:{gameId}     │
    │  daily_quests       │       │  (Pub/Sub relay)      │
    └─────────────────────┘       └───────────────────────┘
```

---

## Multiplayer Match Lifecycle

```
User A creates game (REST POST /api/match)
    → MatchService.createMatch()
    → DB: Game{status=WAITING}
    → RoomManager.createRoom()
    → User A is on GamePage, WebSocket connected

User B joins game (REST POST /api/match/{id}/join)
    → MatchService.joinMatch() [atomic CAS: WAITING→ACTIVE]
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
    → MatchService.endMatch()
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

---

## Resilience Patterns

### Redis Circuit Breaker (`ResilientRedisRoomStore`)

The `ResilientRedisRoomStore` wraps `RedisRoomStore` with a custom circuit breaker:

- **Closed** → all calls go to Redis normally
- **Open** (after 5 consecutive failures, 30s cooldown) → fallback to in-memory `shadowRooms` ConcurrentHashMap
- **Half-Open** → probe allowed; success → Close, failure → stay Open

This ensures the multiplayer system can continue serving games during transient Redis outages.

### Transactional Side Effects (`afterCommit`)

All Redis/WebSocket mutations are registered as `TransactionSynchronization.afterCommit()` hooks. This guarantees:
- Side effects only fire if the DB transaction commits successfully
- No stale Redis state if DB rolls back (deadlock, validation failure, etc.)

### Trade Processing Pipeline (`TradeProcessingPipeline`)

Trades from WebSocket arrive on STOMP inbound threads (~100µs each) and are immediately submitted to an async `ExecutorService` queue. This prevents slow trade processing from blocking WebSocket thread pools.

---

## Authentication Flow

```
POST /api/auth/register  →  BCrypt hash password  →  DB: User saved
POST /api/auth/login     →  verify password  →  JWT issued (24h default)

Client stores JWT in localStorage (key: tradelearn_token)
All REST requests: Authorization: Bearer <jwt>
WebSocket: token passed as ?token= query param on SockJS URL
```

JWT validation happens in two places:
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
