# TradeLearn

> **Competitive trading skill platform — learn strategies, practice on historical data, compete in ranked real-time matches.**

[![Build](https://github.com/koushik31368/tradelearn/actions/workflows/build.yml/badge.svg)](https://github.com/koushik31368/tradelearn/actions/workflows/build.yml)

---

## Quick Start — 30 Seconds

```bash
# 1. Clone
git clone https://github.com/koushik31368/tradelearn.git
cd tradelearn

# 2. Configure secrets (see Environment Variables below)
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env

# 3. Start everything
docker-compose up --build
```

App is at `http://localhost:3000` · Backend at `http://localhost:8080/actuator/health`

---

## Overview

TradeLearn bridges the gap between theoretical trading education and real market decision-making. Unlike conventional paper trading apps, it introduces **competitive pressure**, **structured scoring**, and an **ELO-based ranking system** to create a measurable skill development environment.

**What makes it different:**

| Feature | TradeLearn | Typical Paper Trading App |
|---------|-----------|--------------------------|
| Scoring model | 60% Profit · 20% Risk · 20% Accuracy | P&L only |
| Competition | Real-time head-to-head ranked matches | Solo practice |
| Ranking | ELO system (chess-style) | None |
| Skill progression | Beginner → Intermediate → Advanced → Elite | None |
| Learning content | Strategy-first curriculum with live practice | Generic articles |

---

## Core Features

### 📚 Learning Academy
Structured curriculum covering candlestick patterns, technical indicators, risk management fundamentals, and trading psychology. Organized into progressive sections with quizzes.

### 📊 Strategy Engine
Eight documented trading strategies (RSI Mean Reversion, SMA Crossover, Breakout, Momentum, Support & Resistance, Scalping, Buy & Hold, MACD) with detailed entry/exit rules and market condition guidelines. Each strategy links directly to the simulator.

### 🕹️ Trading Simulator
Candlestick chart-based simulator with real-time market sentiment analysis (trend, volatility, momentum), portfolio tracking, and equity curve visualization. Includes watchlist and SMA overlays.

### ⚔️ Multiplayer Ranked Matches
Real-time head-to-head trading matches via STOMP-over-WebSocket. Both players trade simultaneously on the same market data. Matches produce scored results that update ELO ratings.

### 📈 Historical Practice Mode
Replay iconic market events (NSE crashes and rallies) using real OHLCV data. Strategy detection gives hints in real time. AI opponent provides comparison.

### 🏆 ELO Ranking System
Chess-style ELO rating. Wins against higher-rated opponents yield larger gains. Skill tiers derived from rating thresholds.

---

## Scoring System

```
Match Score = (0.60 × Profit Component) + (0.20 × Risk Component) + (0.20 × Accuracy Component)
```

| Component | Weight | Description |
|-----------|--------|-------------|
| Profit | 60% | Net portfolio return vs. starting capital |
| Risk Management | 20% | Position sizing discipline and drawdown control |
| Accuracy | 20% | Percentage of profitable trades |

This model rewards consistent, disciplined trading over high-risk speculation.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                 Client (React SPA)                  │
│                                                     │
│  REST (Axios) ──────────► Spring Boot Controllers   │
│  WebSocket (STOMP/SockJS) ──► WebSocket Handlers    │
│  Static Assets ──────────────► Nginx / Vercel CDN   │
└─────────────────────────────────────────────────────┘
          │                        │
          ▼                        ▼
   PostgreSQL (JPA)          Redis (Lettuce)
   - Users, Games            - Room state
   - Trades, Scores          - Candle cache
   - Achievements            - Distributed locks
                             - Pub/Sub relay
```

**Key architectural decisions:**

- **Stateless API** — JWT authentication, no server-side sessions
- **STOMP multiplayer** — SockJS fallback for WebSocket across all environments
- **Redis circuit breaker** — `ResilientRedisRoomStore` wraps Redis with in-memory fallback; the system degrades gracefully if Redis is temporarily unavailable
- **Distributed locking** — Redisson prevents double-scheduler races under horizontal scaling
- **Transactional side effects** — Redis/WebSocket mutations are deferred to `afterCommit()` hooks, guaranteeing consistency if DB transactions roll back
- **Rate limiting** — Bucket4j token-bucket protects trade endpoints

---

## Tech Stack

### Frontend

| Technology | Version | Purpose |
|-----------|---------|---------|
| React | 19 | UI framework |
| React Router | 7 | Client-side routing |
| Lightweight Charts | 5 | Candlestick chart rendering |
| STOMP.js + SockJS | Latest | Real-time WebSocket |
| Axios | Latest | HTTP client |
| Vanilla CSS | — | Styling (no external UI library) |

### Backend

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Runtime |
| Spring Boot | 3.2 | Application framework |
| Spring Security + JWT | — | Authentication & authorization |
| Spring WebSocket (STOMP) | — | Real-time multiplayer |
| Spring Data JPA | — | Database access layer |
| PostgreSQL | — | Production database |
| Redis + Lettuce | — | Session caching, distributed state |
| Redisson | 3.27 | Distributed locks |
| Bucket4j | 8.7 | API rate limiting |
| Spring Actuator + Micrometer | — | Health checks & Prometheus metrics |

### Infrastructure

| Technology | Purpose |
|-----------|---------|
| Docker + Docker Compose | Containerized local & production deployment |
| Nginx | Frontend static serving and reverse proxy |
| Render | Production backend hosting |
| Vercel | Frontend CDN |

---

## Project Structure

```
tradelearn/
├── backend/                    Spring Boot application
│   ├── src/main/java/com/tradelearn/server/
│   │   ├── config/             Spring config (CORS, WebSocket, Security, Redis)
│   │   ├── controller/         REST API endpoints
│   │   ├── dto/                Data transfer objects (request/response shapes)
│   │   ├── exception/          Custom exceptions + GlobalExceptionHandler
│   │   ├── middleware/         Servlet filters (rate limit, correlation ID, security headers)
│   │   ├── model/              JPA entities
│   │   ├── repository/         Spring Data JPA repositories
│   │   ├── security/           JWT filter, auth interceptors
│   │   ├── service/            Business logic layer
│   │   ├── socket/             WebSocket handlers and game broadcaster
│   │   ├── util/               ELO, scoring, game logger utilities
│   │   └── validation/         Custom constraint validators
│   ├── src/main/resources/
│   │   ├── application.properties          Base config (safe defaults)
│   │   ├── application-local.properties    Local dev overrides (gitignored)
│   │   ├── application-prod.properties     Production config (env vars only)
│   │   ├── db/migration/                   Reference SQL migrations
│   │   └── logback-spring.xml              Structured logging config
│   └── .env.example            Environment variable template
│
├── frontend/                   React single-page application
│   ├── src/
│   │   ├── components/
│   │   │   ├── simulator/      SimulatorDashboard, CandlestickChart, TradingPanel,
│   │   │   │                   Watchlist, PortfolioSummary, MarketSentiment, etc.
│   │   │   ├── learn/          LearnCard, LearnSection, QuizCard, CandleDiagram
│   │   │   ├── social/         FriendsPanel, ChallengeListener
│   │   │   └── strategies/     StrategyCard, StrategyDetail
│   │   ├── pages/              Full-page route components
│   │   ├── context/            AuthContext (JWT + user state)
│   │   ├── hooks/              useGameSocket (STOMP lifecycle)
│   │   ├── services/           marketApi.js
│   │   ├── styles/             theme.css (single design token source)
│   │   └── utils/              api.js, skillTier.js, simulatorData.js, etc.
│   ├── .env.example            Environment variable template
│   └── public/
│
├── .github/workflows/          CI/CD pipelines
├── docker-compose.yml          Full stack local deployment (includes Redis)
├── vercel.json                 Vercel frontend deployment config
└── docs/                       Architecture and API documentation
```

---

## Getting Started

### Prerequisites

| Dependency | Version | Notes |
|-----------|---------|-------|
| Java | 21+ | [Download Temurin](https://adoptium.net/) |
| Maven | 3.9+ | Included via `./mvnw` wrapper |
| Node.js | 20+ | [Download](https://nodejs.org/) |
| PostgreSQL or MySQL | — | For local dev |
| Redis | 7+ | Required for multiplayer features |

### Option A — Docker (Recommended)

```bash
# Set required environment variables in a .env file at the project root
cp backend/.env.example .env   # then fill in DATABASE_URL, DATABASE_PASSWORD, JWT_SECRET

docker-compose up --build
```

All services (Redis, Backend, Frontend) start with health checks and proper ordering.

### Option B — Manual

**Backend:**
```bash
cd backend

# Create your local config (gitignored)
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
# Edit application-local.properties with your DB credentials

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
Server starts at `http://localhost:8080`

**Frontend:**
```bash
cd frontend
cp .env.example .env   # already set to http://localhost:8080
npm install
npm start
```
Client starts at `http://localhost:3000`

---

## Environment Variables

### Backend (set on Render or in `.env` for Docker)

| Variable | Required | Default | Description |
|---------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | `local` | `local` or `prod` |
| `DATABASE_URL` | Yes (prod) | — | Full JDBC connection string |
| `DATABASE_USERNAME` | Yes (prod) | — | DB username |
| `DATABASE_PASSWORD` | Yes (prod) | — | DB password |
| `JWT_SECRET` | Yes (prod) | — | Min 64-character random string |
| `JWT_EXPIRATION_MS` | No | `86400000` | Token TTL in milliseconds |
| `REDIS_HOST` | Yes | `localhost` | Redis hostname |
| `REDIS_PORT` | No | `6379` | Redis port |
| `CORS_ALLOWED_ORIGINS` | Yes | `http://localhost:3000` | Comma-separated allowed origins |
| `PORT` | No | `8080` | HTTP server port |

### Frontend (set on Vercel or in `.env`)

| Variable | Required | Default | Description |
|---------|----------|---------|-------------|
| `REACT_APP_API_URL` | No | `""` (relative) | Backend API base URL |

---

## Roadmap

- [ ] Trade replay system — candle-by-candle replay of completed matches
- [ ] Advanced analytics dashboard — win rate trends, strategy performance breakdown
- [ ] Tournament mode — bracket-style multi-round competitions
- [ ] Mobile-responsive simulator — full trading experience on tablet/mobile
- [ ] OpenAPI/Swagger documentation — auto-generated REST API reference

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming, PR process, and development workflow.

---

## License

This project is proprietary. All rights reserved.
