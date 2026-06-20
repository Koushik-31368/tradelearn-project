<div align="center">

# 📈 TradeLearn

### Competitive Trading Education Platform

**Real-time head-to-head matches · ELO ranking · Candlestick simulator · SMA backtesting**

[![Build](https://github.com/koushik31368/tradelearn/actions/workflows/build.yml/badge.svg)](https://github.com/koushik31368/tradelearn/actions/workflows/build.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=springboot)
![React](https://img.shields.io/badge/React-18-blue?logo=react)
![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)
![License](https://img.shields.io/badge/License-Proprietary-lightgrey)

[🎮 Live Demo](#-live-demo) · [📸 Screenshots](#-screenshots) · [🚀 Quick Start](#-quick-start) · [🛠 Tech Stack](#-tech-stack)

</div>

---

## 🎮 Live Demo

> **Try it now — no signup required with demo accounts**

| URL | Status |
|---|---|
| **Frontend** | [tradelearn.vercel.app](https://tradelearn.vercel.app) |
| **API Health** | [api.tradelearn.onrender.com/actuator/health](https://api.tradelearn.onrender.com/actuator/health) |

**Demo Accounts** (password: `Demo1234`):

| Account | ELO | Tier | Login |
|---|---|---|---|
| ArjunMehra | 1820 | 💎 Diamond | `demo.diamond@tradelearn.com` |
| PriyaNair | 1245 | 🥇 Gold | `demo.gold@tradelearn.com` |
| RohanSingh | 820 | 🥈 Silver | `demo.silver@tradelearn.com` |

→ Full demo guide: [docs/demo-accounts.md](docs/demo-accounts.md)

---

## 📸 Screenshots

> *(Add screenshots to `docs/screenshots/` — see [Screenshot Guide](docs/screenshot-guide.md))*

| Home | Live Match | Leaderboard |
|:---:|:---:|:---:|
| ![Home](docs/screenshots/home.png) | ![Match](docs/screenshots/match.png) | ![Leaderboard](docs/screenshots/leaderboard.png) |

| Simulator | Practice Mode | Profile |
|:---:|:---:|:---:|
| ![Simulator](docs/screenshots/simulator.png) | ![Practice](docs/screenshots/practice.png) | ![Profile](docs/screenshots/profile.png) |

---

## ✨ Key Technical Highlights

> What makes TradeLearn technically interesting:

- **Real-time multiplayer via WebSocket** — STOMP-over-SockJS with cross-instance Redis Pub/Sub broadcasting. Both players receive the same price candle simultaneously with sub-100ms delivery.

- **ELO ranking system** — Chess-style rating with a 3-factor composite score: `60% Profit · 20% Risk Management (drawdown) · 20% Trade Accuracy`. Rating adjusts after every match using a configurable K-factor.

- **Atomic Redis matchmaking** — Queue implemented as a Redis ZSET (score = ELO rating). Matching uses Lua scripts for atomic compare-and-set to prevent race conditions when two players join simultaneously.

- **Transaction-safe side effects** — Redis mutations and WebSocket broadcasts are registered as `TransactionSynchronization.afterCommit()` hooks, guaranteeing they only fire if the DB transaction commits successfully.

- **LRU-cached market data** — Yahoo Finance OHLCV data cached with a bounded LinkedHashMap (200 entries, access-order eviction) to avoid redundant API calls during active matches.

- **SMA crossover backtest engine** — Configurable fast/slow SMA strategy that runs against historical candle data and produces an equity curve, max drawdown %, and trade-by-trade breakdown.

---

## 🛠 Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Application language |
| Spring Boot | 3.x | REST API + WebSocket server |
| Spring Security + JWT | — | Authentication & authorization |
| Spring Data JPA | — | Database ORM layer |
| MySQL / PostgreSQL | — | Primary database |
| Redis | 7 | Matchmaking queue, session state, Pub/Sub |
| Redisson | — | Distributed locks for match creation |
| STOMP / SockJS | — | Real-time WebSocket protocol |
| Bucket4j | — | API rate limiting |
| Micrometer + Actuator | — | Metrics & health endpoints |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 18 | Single-page application |
| Lightweight Charts | 5 | Candlestick chart rendering |
| STOMP.js / SockJS | — | WebSocket client |
| Axios | — | HTTP API client |
| React Router | 6 | Client-side routing |

### Infrastructure
| Technology | Purpose |
|---|---|
| Docker + Docker Compose | Containerized local development |
| GitHub Actions | CI — build & test on every PR |
| Render | Backend production hosting |
| Vercel | Frontend CDN deployment |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Browser                          │
│          React 18  ·  STOMP.js  ·  Axios               │
└────────────────┬─────────────────────┬──────────────────┘
                 │ HTTPS REST          │ WSS WebSocket
                 ▼                     ▼
┌───────────────────────────────────────────────────────────┐
│                  Spring Boot 3  (Java 21)                  │
│                                                           │
│  ┌──────────────┐  ┌────────────────┐  ┌──────────────┐  │
│  │ REST Controllers│  │ WebSocket Handler│  │ JWT Security │  │
│  └──────┬───────┘  └───────┬────────┘  └──────────────┘  │
│         │                  │                               │
│  ┌──────▼──────────────────▼──────────────────────────┐   │
│  │              Domain Services Layer                  │   │
│  │  MatchLifecycle · MatchScoring · MatchTrade         │   │
│  │  Matchmaking · MarketData · Backtest · Profile      │   │
│  └──────┬──────────────────┬──────────────────────────┘   │
│         │                  │                               │
│  ┌──────▼──────┐    ┌──────▼──────┐                       │
│  │  MySQL/PgSQL│    │    Redis     │                       │
│  │  (JPA/JDBC) │    │  Queue+Pub  │                       │
│  └─────────────┘    └─────────────┘                       │
└───────────────────────────────────────────────────────────┘
```

**Request paths:**
- `REST` → Controller → Service → Repository → Database
- `WebSocket Trade` → GameWebSocketHandler → MatchTradeService → Redis snapshot → Broadcaster → all clients
- `Matchmaking` → Redis ZSET (Lua atomic enqueue) → match found → `afterCommit` room creation → WebSocket notify

---

## 🎯 Core Features

### 🎓 Learning Academy
Structured curriculum covering candlestick patterns, technical indicators, risk management, and trading psychology. Progressive sections with embedded quizzes and visual examples.

### 📊 Strategy Engine
Eight documented trading strategies (RSI Mean Reversion, SMA Crossover, Breakout, Momentum, S&R, Scalping, Buy & Hold, MACD) with entry/exit rules and direct links to the simulator.

### 🕹 Trading Simulator
Real-time candlestick chart with SMA overlays, market sentiment panel (trend/volatility/momentum), portfolio tracking, equity curve, and watchlist. Seeded with NSE stocks.

### ⚔️ Multiplayer Ranked Matches
Head-to-head real-time matches via WebSocket. Both players receive the same market data simultaneously. Match results update ELO ratings with a composite skill score.

**Scoring formula:** `Score = (Profit% × 0.60) + (Risk Score × 0.20) + (Accuracy% × 0.20)`

### 📉 Historical Practice Mode
Replay iconic NSE market events (2020 COVID crash, sectoral crashes, rallies) using real OHLCV data. Strategy detection surfaces pattern hints in real time. Optional AI opponent for comparison.

### 🔬 Backtest Engine
SMA crossover backtest that runs against any OHLCV dataset. Outputs equity curve, return %, max drawdown %, and win rate across all generated signals.

---

## 🚀 Quick Start

```bash
# 1. Clone
git clone https://github.com/koushik31368/tradelearn.git
cd tradelearn

# 2. Configure environment
cp backend/.env.example backend/.env
# Edit backend/.env with your DB credentials

# 3. Start everything with Docker
docker-compose up --build
```

**App:** `http://localhost:3000`  
**API:** `http://localhost:8080/actuator/health`

> **Load demo data** after first boot:
> ```bash
> mysql -u root -p tradelearn < backend/src/main/resources/db/seed-demo.sql
> ```

→ Manual setup: [docs/developer-setup.md](docs/developer-setup.md)

---

## ⚙️ Environment Variables

### Backend (`.env` or Render dashboard)

| Variable | Required | Default | Description |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `local` | `local` or `prod` |
| `DATABASE_URL` | Yes (prod) | — | Full JDBC connection string |
| `DATABASE_USERNAME` | Yes (prod) | — | DB username |
| `DATABASE_PASSWORD` | Yes (prod) | — | DB password |
| `JWT_SECRET` | Yes (prod) | — | Min 64-char random string |
| `REDIS_HOST` | Yes | `localhost` | Redis hostname |
| `CORS_ALLOWED_ORIGINS` | Yes | `http://localhost:3000` | Comma-separated allowed origins |

### Frontend (`.env` or Vercel dashboard)

| Variable | Required | Default | Description |
|---|---|---|---|
| `REACT_APP_API_URL` | No | `""` (relative) | Backend API base URL |

---

## 📁 Project Structure

```
tradelearn/
├── backend/                         Spring Boot application
│   └── src/main/java/com/tradelearn/server/
│       ├── analytics/               Backtest engine, readiness scoring
│       ├── auth/                    JWT authentication, security config
│       ├── common/                  Shared utilities, exception handlers, filters
│       ├── game/                    Match lifecycle, scoring, trading, queries
│       ├── infrastructure/          Redis rooms, resilience, scheduling, rate limiting
│       ├── leaderboard/             ELO ranking, tier badges
│       ├── market/                  Yahoo Finance provider, LRU candle cache
│       ├── matchmaking/             Redis ZSET queue, ELO expansion, Lua scripts
│       ├── profile/                 User profile assembly service
│       ├── simulator/               Solo paper trading portfolio
│       ├── social/                  Friends, challenges, WebSocket social events
│       ├── user/                    User management
│       └── websocket/               STOMP handler, broadcaster, Redis relay
│
├── frontend/src/
│   ├── api/                         Axios client + domain API modules
│   ├── features/                    Feature-domain UI components and pages
│   │   ├── auth/                    Login, register, forgot password
│   │   ├── game/                    Lobby, live match, match result, history
│   │   ├── leaderboard/             Rankings, tier badges
│   │   ├── simulator/               Candlestick chart, missions, practice
│   │   ├── strategies/              Strategy cards and detail views
│   │   └── social/                  Friends panel, challenge listener
│   └── layout/                      Navbar, shared layout components
│
├── docs/                            Architecture, API reference, demo guide
├── loadtest/                        k6 load tests + monitoring dashboards
├── docker-compose.yml               Full-stack local dev environment
└── .github/workflows/               CI/CD pipelines
```

---

## 🗺 Roadmap

- [ ] Trade replay system — candle-by-candle animated replay of completed matches
- [ ] Advanced analytics dashboard — win rate trends, strategy performance breakdown
- [ ] Tournament mode — bracket-style multi-round competitions
- [ ] Mobile-responsive simulator — full trading experience on tablet/mobile
- [ ] OpenAPI/Swagger — auto-generated REST API documentation

---

## 📄 License

Proprietary. All rights reserved. © 2024 Koushik Reedy
