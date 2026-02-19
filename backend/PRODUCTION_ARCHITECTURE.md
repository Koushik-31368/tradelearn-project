# TradeLearn Production Architecture

> **Target**: 10,000 concurrent 1v1 games — horizontally scalable.
> **Stack**: Spring Boot 3.2.5 / Java 17 / Redis / PostgreSQL / STOMP WebSockets

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Folder Structure](#2-folder-structure)
3. [Layer Architecture](#3-layer-architecture)
4. [Horizontal Scaling via Redis](#4-horizontal-scaling-via-redis)
5. [Middleware Pipeline](#5-middleware-pipeline)
6. [Validation Layer](#6-validation-layer)
7. [Error Handling](#7-error-handling)
8. [Environment Configuration](#8-environment-configuration)
9. [Capacity Planning (10K Games)](#9-capacity-planning-10k-games)
10. [Connection Pool Tuning](#10-connection-pool-tuning)
11. [Monitoring & Observability](#11-monitoring--observability)
12. [Deployment Topology](#12-deployment-topology)
13. [Security Hardening](#13-security-hardening)
14. [Operational Runbook](#14-operational-runbook)

---

## 1. System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    LOAD BALANCER (Nginx / ALB)              │
│              (sticky sessions for WebSocket)                │
└─────────────┬───────────────────────────────┬───────────────┘
              │                               │
   ┌──────────▼──────────┐        ┌──────────▼──────────┐
   │  TradeLearn Node 1  │        │  TradeLearn Node 2  │
   │                     │        │                     │
   │  ┌───────────────┐  │        │  ┌───────────────┐  │
   │  │ REST API       │  │        │  │ REST API       │  │
   │  │ WebSocket STOMP│  │        │  │ WebSocket STOMP│  │
   │  │ GameBroadcaster│  │        │  │ GameBroadcaster│  │
   │  │ Scheduler Pool │  │        │  │ Scheduler Pool │  │
   │  └───────────────┘  │        │  └───────────────┘  │
   └──────────┬──────────┘        └──────────┬──────────┘
              │                               │
   ┌──────────▼───────────────────────────────▼──────────┐
   │                     REDIS                            │
   │  Pub/Sub relay │ Game ownership │ Rate limit state   │
   └──────────┬───────────────────────────────┬──────────┘
              │                               │
   ┌──────────▼───────────────────────────────▼──────────┐
   │                   POSTGRESQL                         │
   │  Users │ Games │ Trades │ MatchStats                 │
   └─────────────────────────────────────────────────────┘
```

**Data flow for a single trade:**
1. Client sends STOMP message → `/app/game/{id}/trade`
2. `GameWebSocketHandler` validates and delegates to `MatchTradeService`
3. Trade persisted to PostgreSQL with server-authoritative price
4. Updated state broadcast via `GameBroadcaster`
5. Broadcaster sends locally via `SimpMessagingTemplate` AND cross-instance via Redis Pub/Sub
6. All connected clients (on any server) receive the update

---

## 2. Folder Structure

```
backend/src/main/java/com/tradelearn/server/
├── config/                     # Spring configuration beans
│   ├── RedisConfig.java        # Lettuce connection factory + templates
│   ├── SchedulerConfig.java    # Thread pool for candle ticks (64 threads)
│   ├── SecurityConfig.java     # CORS, Spring Security, env-var origins
│   ├── WebSocketConfig.java    # STOMP/SockJS endpoint registration
│   └── WebSocketEventListener.java  # Connect/disconnect lifecycle
│
├── controller/                 # REST endpoints
│   ├── AuthController.java     # Register, login, reset password
│   ├── HealthController.java   # GET /api/health — lightweight diagnostics
│   ├── LeaderboardController.java
│   ├── MatchController.java    # CRUD for matches (with @Valid)
│   └── GameWebSocketController.java  # DEPRECATED → socket/GameWebSocketHandler
│
├── dto/                        # Request/response DTOs with Jakarta Validation
│   ├── CreateMatchRequest.java # @NotNull, @Positive, @ValidStockSymbol, @Min/@Max
│   ├── MatchTradeRequest.java  # @ValidTradeType, @Positive, @NotBlank
│   └── MatchResult.java        # End-of-game result payload
│
├── exception/                  # Custom exceptions + global handler
│   ├── GameNotFoundException.java
│   ├── GlobalExceptionHandler.java  # @RestControllerAdvice with validation errors
│   ├── InvalidGameStateException.java
│   ├── RoomFullException.java
│   └── TradeValidationException.java
│
├── middleware/                  # Servlet filter pipeline
│   ├── RateLimitFilter.java    # @Order(1) — Bucket4j token bucket per IP
│   ├── RequestCorrelationFilter.java  # @Order(0) — UUID request tracing + MDC
│   └── SecurityHeadersFilter.java     # @Order(2) — OWASP headers
│
├── model/                      # JPA entities
│   ├── Game.java               # Match state with pessimistic locking
│   ├── MatchStats.java         # Per-player risk/score stats
│   ├── Trade.java              # Individual trade record
│   └── User.java               # Player with Elo rating
│
├── repository/                 # Spring Data JPA repositories
│   ├── GameRepository.java     # Includes atomicJoin, findByIdForUpdate
│   ├── MatchStatsRepository.java
│   ├── TradeRepository.java
│   └── UserRepository.java
│
├── service/                    # Business logic
│   ├── CandleService.java      # Server-authoritative candle data + caching
│   ├── MatchSchedulerService.java   # Per-game candle tick scheduler
│   ├── MatchService.java       # Match lifecycle (create/join/start/end)
│   ├── MatchTradeService.java  # Trade execution + position calculation
│   └── RoomManager.java        # In-memory room/session state (ConcurrentHashMap)
│
├── socket/                     # WebSocket infrastructure
│   ├── GameBroadcaster.java    # Abstraction: local + Redis broadcast
│   ├── GameWebSocketHandler.java    # STOMP message handlers (@MessageMapping)
│   └── RedisWebSocketRelay.java     # Redis Pub/Sub cross-instance relay
│
├── util/                       # Utility classes
│   ├── EloUtil.java            # Elo rating calculation (K=32)
│   ├── GameLogger.java         # Structured logging with MDC context
│   └── ScoringUtil.java        # Hybrid scoring formula
│
└── validation/                 # Custom Jakarta Validation constraints
    ├── StockSymbolValidator.java    # Validates candle data exists
    ├── TradeTypeValidator.java      # BUY/SELL/SHORT/COVER
    ├── ValidStockSymbol.java        # @ValidStockSymbol annotation
    └── ValidTradeType.java          # @ValidTradeType annotation
```

---

## 3. Layer Architecture

```
Request → Filter Chain → Controller → Service → Repository → DB
                ↓              ↓           ↓
          RateLimit     Validation    GameBroadcaster → Redis Pub/Sub → Other Nodes
          Correlation   @Valid
          SecHeaders    DTOs
```

### Filter Chain (Order)

| Order | Filter | Purpose |
|-------|--------|---------|
| 0 | `RequestCorrelationFilter` | Assigns UUID requestId to MDC |
| 1 | `RateLimitFilter` | IP-based Bucket4j rate limiting |
| 2 | `SecurityHeadersFilter` | OWASP security headers |

### Service Layer Rules

1. **No direct `SimpMessagingTemplate` injection** — always use `GameBroadcaster`
2. **All trades are server-authoritative** — prices come from `CandleService`, never the client
3. **Concurrent access** protected by `ConcurrentHashMap` (RoomManager) and pessimistic DB locks (GameRepository)
4. **Scheduler isolation** — each game gets its own `ScheduledFuture`, capped by thread pool

---

## 4. Horizontal Scaling via Redis

### Problem
WebSocket connections are server-local. If Player A connects to Node 1 and Player B to Node 2, neither sees the other's updates.

### Solution: Redis Pub/Sub Relay

```
Node 1                     Redis                     Node 2
  │                          │                          │
  │   broadcast(game/1/candle, data)                    │
  ├─── local send ──►        │                          │
  ├─── publish ─────►  tradelearn:ws:broadcast          │
  │                    ──────┼────────── subscribe ─────►│
  │                          │          onMessage()      │
  │                          │          ├── check INSTANCE_ID (dedup)
  │                          │          └── local send ──►
```

### Key Components

| Component | Responsibility |
|-----------|---------------|
| `GameBroadcaster` | Single injection point. Sends locally + publishes to Redis. |
| `RedisWebSocketRelay` | Implements `MessageListener`. Subscribes to Redis channel, deduplicates by INSTANCE_ID, re-broadcasts locally. |
| `RedisConfig` | Lettuce connection factory, JSON-serialized templates, listener container. |

### Game Ownership

Games are assigned to specific instances via Redis keys:

```
SET game:owner:{gameId} = "node-abc123"   TTL 60s
```

The scheduler renews TTL on each tick. If a node dies, ownership expires and another can take over.

### Instance Identity

Each JVM gets a unique 8-character ID on startup (`RedisWebSocketRelay.INSTANCE_ID`). This prevents relay loops: a node ignores messages it published itself.

---

## 5. Middleware Pipeline

### Rate Limiting (`RateLimitFilter`)

Three independent token buckets per IP:

| Tier | Path Pattern | Capacity | Refill Rate | Purpose |
|------|-------------|----------|-------------|---------|
| General | `*` | 100 tokens | 100/min | Browsing, queries |
| Trades | `/api/matches/*/trade` | 60 tokens | 60/min | Trade spam prevention |
| Create | `/api/matches` (POST) | 10 tokens | 10/min | Game creation flood |

Configurable via `application.properties`:
```properties
tradelearn.ratelimit.enabled=true
tradelearn.ratelimit.general-rpm=100
tradelearn.ratelimit.trades-rpm=60
tradelearn.ratelimit.create-rpm=10
```

Response on limit exceeded: HTTP 429 with JSON body.

### Request Correlation (`RequestCorrelationFilter`)

Every request gets a 12-character UUID:
```
X-Request-Id: a1b2c3d4e5f6
```

Propagated via SLF4J MDC → appears in all log lines:
```
2024-01-15 10:30:00.123 [http-nio-8080-exec-1] INFO MatchService [req=a1b2c3d4e5f6] - Match created
```

Slow requests (>2s) logged at WARN level with duration.

### Security Headers (`SecurityHeadersFilter`)

| Header | Value |
|--------|-------|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` (prod only) |
| `Cache-Control` | `no-store` (for `/api/*` routes) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=()` |

HSTS is disabled in dev via `tradelearn.security.hsts-enabled=false`.

---

## 6. Validation Layer

### DTO Validation (Jakarta Bean Validation)

All request DTOs use `@Valid` on controller endpoints:

```java
@PostMapping
public ResponseEntity<?> createMatch(@Valid @RequestBody CreateMatchRequest request) { ... }
```

**CreateMatchRequest constraints:**
- `creatorId`: `@NotNull @Positive`
- `stockSymbol`: `@NotBlank @ValidStockSymbol`
- `durationMinutes`: `@Min(1) @Max(60)`
- `startingBalance`: `@Min(10000) @Max(100000000)`

**MatchTradeRequest constraints:**
- `gameId`, `userId`: `@NotNull @Positive`
- `symbol`: `@NotBlank`
- `type`: `@NotBlank @ValidTradeType` (BUY/SELL/SHORT/COVER)
- `quantity`: `@Positive`

### Custom Annotations

| Annotation | Validator | Logic |
|-----------|-----------|-------|
| `@ValidTradeType` | `TradeTypeValidator` | Checks `BUY\|SELL\|SHORT\|COVER` (case-insensitive) |
| `@ValidStockSymbol` | `StockSymbolValidator` | Verifies `candles/{SYMBOL}.json` exists on classpath |

### Validation Error Response

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "Request validation failed. Check 'details.fieldErrors' for specifics.",
  "path": "uri=/api/matches",
  "details": {
    "fieldErrors": {
      "stockSymbol": "Stock symbol has no candle data available",
      "durationMinutes": "must be less than or equal to 60"
    },
    "totalErrors": 2
  }
}
```

---

## 7. Error Handling

### Exception Hierarchy

| Exception | HTTP Status | When |
|-----------|------------|------|
| `GameNotFoundException` | 404 | Game ID doesn't exist |
| `RoomFullException` | 409 | Room already has 2 players |
| `InvalidGameStateException` | 400 | Wrong lifecycle phase (e.g., joining a FINISHED game) |
| `TradeValidationException` | 400 | Bad trade (insufficient funds, invalid quantity) |
| `MethodArgumentNotValidException` | 400 | DTO @Valid constraint violation |
| `IllegalArgumentException` | 400 | Generic bad input |
| `IllegalStateException` | 409 | Business rule conflict |
| `Exception` (catch-all) | 500 | Unknown — generic message, no stack leak |

### Error Response Format

Every error returns the same shape:

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "status": 404,
  "error": "Game Not Found",
  "message": "Game 12345 not found",
  "path": "uri=/api/matches/12345",
  "details": { "gameId": 12345 }
}
```

---

## 8. Environment Configuration

### Variable Hierarchy

```
application.properties          ← Shared defaults
  └── application-local.properties   ← Dev overrides (profile=local)
  └── application-prod.properties    ← Production overrides (profile=prod)
        └── Environment variables    ← Highest priority (12-factor compliant)
```

### Critical Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `local` | Profile selector |
| `DATABASE_URL` | `jdbc:mysql://localhost:3306/tradelearn` | JDBC connection string |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `root` | Database password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis ACL password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,...` | Comma-separated CORS origins |
| `PORT` | `8080` | Server port |
| `INSTANCE_ID` | `single` | Instance identifier for health endpoint |

### Profile Differences

| Setting | local | prod |
|---------|-------|------|
| DB dialect | MySQL | PostgreSQL |
| DDL auto | `update` | `validate` |
| Hikari max pool | 10 | 50 |
| Scheduler threads | 8 | 64 |
| Rate limiting | disabled | enabled |
| HSTS | disabled | enabled |
| Log level | DEBUG | INFO |
| Redis | optional | required |

---

## 9. Capacity Planning (10K Games)

### Thread Pool Sizing

**Candle Scheduler (64 threads):**
```
Each game: 1 tick every 5 seconds, each tick takes ~20ms
10,000 games × 20ms / 5,000ms = 40 threads actively busy
Pool: 64 threads — 60% utilization, 24 threads headroom
```

**Tomcat HTTP threads (200 threads):**
```
REST calls per game: ~5/min average (create, join, trade queries)
10,000 games × 5 req/min = 50,000 req/min = ~833 req/sec
Avg response time: 50ms → 200 threads can handle 4,000 req/sec
Headroom: ~3,000 req/sec for traffic spikes
```

### Memory Budget

```
Per-game in-memory state:
  RoomManager.Room: ~200 bytes (player IDs, phase, ready flags)
  ScheduledFuture ref: ~100 bytes
  CandleService cache: ~50KB (1000 candles × 50 bytes)
  ─────────────────────
  Total per game: ~51KB

10,000 games × 51KB = ~500MB
JVM overhead + Spring: ~300MB
Base recommendation: 1GB heap minimum
```

### Database Connections

```
Hikari max pool: 50 connections
Each game uses ~2-3 connections during tick (read game + advance candle)
With connection pooling and ~20ms hold time:
  50 connections × (5000ms / 20ms) = 12,500 requests/sec
Far exceeds the ~833 req/sec needed
```

### Redis Throughput

```
Pub/Sub: 1 message per candle tick per game
10,000 games ÷ 5 sec = 2,000 msg/sec
Redis can handle 100K+ msg/sec → ~2% utilization
```

### Scaling Thresholds

| Metric | Single Node | 2 Nodes | 4 Nodes |
|--------|------------|---------|---------|
| Max concurrent games | ~3,000 | ~6,000 | ~12,000 |
| Max WebSocket connections | ~10,000 | ~20,000 | ~40,000 |
| DB connections | 50 | 100 | 200 |
| Scheduler threads | 64 | 128 | 256 |

---

## 10. Connection Pool Tuning

### HikariCP (PostgreSQL)

```properties
# Production settings (application-prod.properties)
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.idle-timeout=300000        # 5 min
spring.datasource.hikari.max-lifetime=1800000        # 30 min
spring.datasource.hikari.connection-timeout=30000    # 30 sec
spring.datasource.hikari.leak-detection-threshold=60000  # 1 min
```

**Why 50?** PostgreSQL default `max_connections` is 100. Two app instances × 50 = 100. Scale PostgreSQL to 300+ for 4 nodes.

### Lettuce (Redis)

```properties
spring.data.redis.lettuce.pool.max-active=16
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=4
```

Lettuce uses non-blocking I/O — 16 connections handle thousands of commands/sec. Multiplexing means connections are shared, not held.

### Hibernate Batching

```properties
spring.jpa.properties.hibernate.jdbc.batch_size=25
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

Trade inserts are batched when multiple trades arrive in the same tick window.

---

## 11. Monitoring & Observability

### Endpoints

| Endpoint | Purpose | Auth |
|----------|---------|------|
| `GET /api/health` | Game-specific metrics (zero-cost) | Public |
| `GET /actuator/health` | Spring Actuator health checks | Public |
| `GET /actuator/info` | Application info | Public |
| `GET /actuator/metrics` | Micrometer metric names | Public |
| `GET /actuator/prometheus` | Prometheus scrape endpoint | Public |

### Custom `/api/health` Response

```json
{
  "status": "UP",
  "application": "tradelearn-backend",
  "instance": "node-abc123",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "uptime": "2h 30m 15s",
  "game": {
    "activeRooms": 1523,
    "activeSchedulers": 1520,
    "connectedSessions": 3046
  },
  "jvm": {
    "maxMemoryMB": 2048,
    "usedMemoryMB": 742,
    "availableProcessors": 4
  }
}
```

### Logging Strategy

| Log File | Content | Retention |
|----------|---------|-----------|
| `logs/tradelearn.log` | All application logs | 30 days, 1GB cap |
| `logs/game-events.log` | Game lifecycle events with MDC | 30 days, 1GB cap |
| `logs/errors.log` | ERROR level only with full stack traces | 90 days |

Every log line includes `[req=<requestId>]` for distributed tracing.

### Key Metrics to Alert On

| Metric | Warning | Critical |
|--------|---------|----------|
| Active schedulers | >8,000 | >9,500 |
| JVM heap used | >70% | >85% |
| DB connection pool usage | >80% | >95% |
| HTTP 5xx rate | >0.1% | >1% |
| Response time P99 | >500ms | >2,000ms |

---

## 12. Deployment Topology

### Single Node (Development / Staging)

```
Docker Compose:
  ├── tradelearn-backend (port 8080)
  ├── mysql:8 (port 3306)
  └── redis:7 (port 6379)
```

### Multi-Node Production

```
                    ┌─────────────────┐
                    │  ALB / Nginx    │
                    │  (WebSocket     │
                    │   sticky)       │
                    └────┬───────┬────┘
                         │       │
              ┌──────────▼┐  ┌──▼──────────┐
              │ Node 1    │  │ Node 2      │
              │ (Railway) │  │ (Railway)   │
              └────┬──────┘  └──────┬──────┘
                   │                │
         ┌─────────▼────────────────▼──────────┐
         │           Redis (Upstash / ElastiCache) │
         └─────────────────────────────────────────┘
                          │
         ┌────────────────▼────────────────────────┐
         │     PostgreSQL (Railway / RDS)           │
         └─────────────────────────────────────────┘
```

### WebSocket Sticky Sessions

Load balancer MUST route WebSocket upgrade requests to the same backend instance for the lifetime of the connection. Configure via:
- **Nginx**: `ip_hash` or `sticky cookie`
- **AWS ALB**: Target group stickiness (application cookie)
- **Railway**: Single node per service (scale via separate services)

---

## 13. Security Hardening

### Headers (Production)

All API responses include OWASP-recommended headers via `SecurityHeadersFilter`:
- No MIME sniffing (`X-Content-Type-Options: nosniff`)
- No iframe embedding (`X-Frame-Options: DENY`)
- HTTPS enforcement (`Strict-Transport-Security`)
- No caching of API responses (`Cache-Control: no-store`)

### Rate Limiting

Three-tier IP-based limiting prevents:
- API scraping (100 req/min general)
- Trade spam (60 trades/min per IP)
- Game creation floods (10 games/min per IP)

Real client IP extracted from `X-Forwarded-For` / `X-Real-IP` headers (reverse proxy aware).

### Input Validation

All user input validated at two levels:
1. **DTO level**: Jakarta Bean Validation annotations (`@Valid`)
2. **Service level**: Business rules (balance check, participant verification, game state)

Server-authoritative prices prevent client-side price manipulation.

### CORS

Origins loaded from `CORS_ALLOWED_ORIGINS` environment variable — never hardcoded in production.

---

## 14. Operational Runbook

### Adding a New Server Instance

1. Deploy the same Docker image with a unique `INSTANCE_ID`
2. Point it at the same Redis and PostgreSQL
3. Register it behind the load balancer with WebSocket sticky session support
4. Verify via `GET /api/health` — check `instance` field
5. Games will automatically distribute across nodes via Redis relay

### Handling a Node Failure

1. Load balancer detects unhealthy node (no `/actuator/health` response)
2. WebSocket clients on that node are disconnected
3. Redis game ownership keys expire (60s TTL)
4. Affected ACTIVE games become ABANDONED via `WebSocketEventListener`
5. Clients reconnect to remaining nodes and see ABANDONED status

### Scaling Up for Peak Load

```bash
# Check current capacity
curl https://node1/api/health | jq '.game'

# If activeSchedulers > 80% of pool:
# 1. Increase SCHEDULER_POOL_SIZE or add another node
# 2. Verify Redis can handle increased Pub/Sub throughput
# 3. Ensure PostgreSQL max_connections accommodates new Hikari pools
```

### Emergency: Game Stuck in ACTIVE

```sql
-- Find stuck games (active for more than 1 hour)
SELECT id, created_at, status FROM games
WHERE status = 'ACTIVE' AND created_at < NOW() - INTERVAL '1 hour';

-- Force-finish via API
-- POST /api/matches/{gameId}/end
```

### Database Maintenance

```sql
-- Archive old finished games (keep 90 days)
DELETE FROM trades WHERE game_id IN (
  SELECT id FROM games WHERE status = 'FINISHED'
  AND end_time < NOW() - INTERVAL '90 days'
);

-- Vacuum after bulk delete (PostgreSQL)
VACUUM ANALYZE trades;
VACUUM ANALYZE games;
```

---

## Architecture Decision Records

| Decision | Rationale |
|----------|-----------|
| Redis Pub/Sub over STOMP broker relay | Simpler to implement; no external RabbitMQ dependency; handles 100K+ msg/sec |
| Bucket4j over Spring Cloud Gateway | Embedded rate limiting without API gateway; simpler deployment |
| Per-game ScheduledFuture over global cron | Independent lifecycle per game; clean cancellation on game end |
| Pessimistic locking over optimistic | Prevents double-join race conditions; acceptable latency for write-heavy game ops |
| GameBroadcaster abstraction | Decouples services from transport; makes horizontal scaling invisible to business logic |
| MDC-based request correlation | Cross-cutting concern without code changes in services; automatic log enrichment |

---

*Last updated: Production Architecture v2.0*
*Stack: Spring Boot 3.2.5 • Java 17 • Redis 7 • PostgreSQL 16 • Micrometer + Prometheus*
