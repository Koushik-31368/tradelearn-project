# TradeLearn — Developer Setup Guide

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 21 (Temurin recommended) | [adoptium.net](https://adoptium.net) |
| Maven | 3.9+ | bundled with IDE or `brew install maven` |
| Node.js | 20 LTS | [nodejs.org](https://nodejs.org) |
| npm | 10+ | bundled with Node |
| Docker | 24+ | [docs.docker.com](https://docs.docker.com/get-docker/) |
| Docker Compose | v2+ | bundled with Docker Desktop |
| Redis | 7+ | via Docker (see below) |
| PostgreSQL | 15+ | via Docker or local install |

---

## Quick Start (Docker — Recommended)

### 1. Clone the repo
```bash
git clone https://github.com/Koushik-31368/tradelearn.git
cd tradelearn
```

### 2. Create environment files

**Backend** — copy the example and fill in real values:
```bash
cp backend/.env.example backend/.env
```

Edit `backend/.env`:
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/tradelearn
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_password
JWT_SECRET=your_very_long_random_secret_at_least_256_bits
JWT_EXPIRATION_MS=86400000
REDIS_HOST=localhost
REDIS_PORT=6379
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

**Frontend** — create `.env.local` in the `frontend/` directory:
```env
REACT_APP_API_URL=http://localhost:8080
```

### 3. Start infrastructure (Redis + PostgreSQL)

```bash
# Spin up just Redis and Postgres for local dev
docker compose -f docker-compose.dev.yml up -d
```

### 4. Run the backend
```bash
cd backend
mvn spring-boot:run
```

The server starts at **http://localhost:8080**.

Health check: `curl http://localhost:8080/actuator/health`

### 5. Run the frontend
```bash
cd frontend
npm install
npm start
```

The React app starts at **http://localhost:3000**.

---

## Running the Full Stack via Docker Compose

For a production-like environment (all services in containers):

```bash
cp .env.example .env   # fill in real values
docker compose up --build
```

| Service | Port |
|---|---|
| Frontend (Nginx) | http://localhost:3000 |
| Backend (Spring Boot) | http://localhost:8080 |
| Redis | localhost:6379 |

---

## Environment Variables Reference

### Backend (`backend/.env` or system env)

| Variable | Required | Default | Description |
|---|---|---|---|
| `DATABASE_URL` | ✅ | — | JDBC URL: `jdbc:postgresql://host:5432/dbname` |
| `DATABASE_USERNAME` | ✅ | — | PostgreSQL username |
| `DATABASE_PASSWORD` | ✅ | — | PostgreSQL password |
| `JWT_SECRET` | ✅ | — | HMAC secret key (min 256 bits) |
| `JWT_EXPIRATION_MS` | ❌ | `86400000` | Token lifetime in ms (default 24 h) |
| `REDIS_HOST` | ❌ | `localhost` | Redis hostname |
| `REDIS_PORT` | ❌ | `6379` | Redis port |
| `CORS_ALLOWED_ORIGINS` | ❌ | `http://localhost:3000` | Comma-separated allowed origins |
| `SPRING_PROFILES_ACTIVE` | ❌ | `default` | Use `prod` in production |

### Frontend (`frontend/.env.local`)

| Variable | Required | Default | Description |
|---|---|---|---|
| `REACT_APP_API_URL` | ❌ | `""` (relative) | Backend base URL. Leave empty to use relative paths (same-origin proxy). Set to `http://localhost:8080` for local dev. |

---

## Project Structure

```
tradelearn/
├── backend/                  Spring Boot 3.2 / Java 21
│   └── src/main/java/com/tradelearn/server/
│       ├── auth/             JWT auth, security config, filters
│       ├── common/           Shared: exceptions, middleware, util, validation
│       ├── game/             Match lifecycle, trade execution, scoring
│       ├── infrastructure/   Redis room/store, resilience, scheduling, rate-limit
│       ├── leaderboard/      Rankings, user profile
│       ├── learning/         Lesson progress
│       ├── market/           Yahoo Finance proxy, candle service
│       ├── matchmaking/      Distributed ELO matchmaking engine
│       ├── quests/           Daily quests, weekly challenges, achievements
│       ├── simulator/        Practice mode portfolio simulation
│       ├── social/           Friends, challenges
│       ├── user/             User account CRUD
│       ├── websocket/        STOMP WebSocket handlers, broadcaster
│       └── analytics/        Backtest, strategy analysis, readiness score
│
└── frontend/                 React 19 SPA
    └── src/
        ├── api/              HTTP client + domain API modules
        ├── features/         Feature-grouped pages & components
        │   ├── auth/
        │   ├── game/
        │   ├── simulator/
        │   ├── leaderboard/
        │   ├── matchmaking/
        │   ├── social/
        │   ├── dashboard/
        │   └── ...
        ├── layout/           Navbar, Footer, Modal, StockTicker
        ├── hooks/            useGameSocket (STOMP lifecycle)
        └── utils/            Shared utilities (skillTier, strategyDetector, etc.)
```

---

## Running Tests

### Backend
```bash
cd backend
mvn clean verify
```

Unit + integration tests use an in-memory H2 database (configured in `src/test/resources/application-test.properties`).

### Frontend
```bash
cd frontend
npm test -- --watchAll=false
```

---

## Common Issues

### Backend fails to start: `Cannot create PoolingDataSource`
→ PostgreSQL is not running. Start it with `docker compose -f docker-compose.dev.yml up -d`.

### Frontend shows "Network Error" on login
→ Check that `REACT_APP_API_URL` in `frontend/.env.local` matches the backend port.

### Redis connection refused
→ Start Redis: `docker run -d -p 6379:6379 redis:7-alpine`
→ Or set `redis.enabled=false` in `application.properties` to run without Redis (matchmaking disabled).

### `REACT_APP_API_URL is not defined` warning
→ This is safe to ignore in local dev. It means all API calls use relative paths — which works when the frontend is served at the same origin as the backend.

---

## Useful Commands

```bash
# Check backend health
curl http://localhost:8080/actuator/health

# Flush Redis (dev only)
redis-cli FLUSHALL

# Watch backend logs in real-time
tail -f backend/logs/application.log

# List all Spring Boot endpoints
curl http://localhost:8080/actuator/mappings | python -m json.tool
```
