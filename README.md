<div align="center">

<img src="https://img.shields.io/badge/TradeLearn-Educational%20Trading%20Platform-0d9488?style=for-the-badge&labelColor=0f172a" alt="TradeLearn" height="40"/>

# TradeLearn

### Gamified Multiplayer Trading Simulator

**Compete head-to-head · Build real trading skills · Climb the ELO leaderboard**

[![CI](https://github.com/Koushik-31368/tradelearn-project/actions/workflows/ci.yml/badge.svg)](https://github.com/Koushik-31368/tradelearn-project/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-336791?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![License](https://img.shields.io/badge/License-Proprietary-64748b)

[🎮 Live Demo](#-live-demo) · [✨ Features](#-features) · [🏗 Architecture](#-architecture) · [🚀 Quick Start](#-quick-start) · [🛠 Tech Stack](#-tech-stack)

</div>

---

## 🎮 Live Demo

| | URL |
|---|---|
| 🌐 **Frontend** | [tradelearn-project.vercel.app](https://tradelearn-project.vercel.app) |
| 🔌 **API Health** | [tradelearn-project-g.onrender.com/actuator/health](https://tradelearn-project-g.onrender.com/actuator/health) |

> **Note:** Backend is hosted on Render free tier — first request may take ~30s to cold-start.

> [!NOTE]
> **Educational use only.** TradeLearn is a gamified learning simulator — not a licensed broker, financial advisor, or investment platform. Market data sourced from public APIs (yfinance, Finnhub) for non-commercial, academic use only.

---

## ✨ Features

### ⚔️ Real-Time Multiplayer Matches
Head-to-head trading battles via WebSocket. Both players receive identical market candles simultaneously — no latency advantage. Results feed into an ELO rating system.

**Scoring formula:**
```
Score = (Profit% × 0.60) + (Risk Score × 0.20) + (Accuracy% × 0.20)
```

### 📈 DB-Backed Historical Replay Engine
Every multiplayer match replays a **random window** of real NSE historical data pulled from a PostgreSQL database — not dummy JSON. 10 symbols, 2 years of daily OHLCV candles (4,970+ rows).

### 🕹 Solo Trading Simulator
Real-time candlestick chart with SMA overlays, live portfolio P&L, equity curve, market sentiment panel, and full order management. Seeded with NSE blue-chip stocks.

### 🎓 Learning Academy
Structured curriculum: candlestick patterns → technical indicators → risk management → trading psychology. Progressive sections with embedded quizzes.

### 📊 Strategy Engine
Eight documented strategies (RSI Mean Reversion, SMA Crossover, Breakout, Momentum, S&R, Scalping, Buy & Hold, MACD) with entry/exit rules and direct simulator links.

### 🔬 Backtest Engine
SMA crossover backtester against any OHLCV dataset. Outputs equity curve, return %, max drawdown %, and trade-by-trade breakdown.

### 🏆 ELO Leaderboard
Chess-style rating adjusted after every match. Tier badges (Bronze → Silver → Gold → Diamond) with seasonal rankings.

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                          Browser                            │
│            React 18  ·  STOMP.js  ·  Axios                 │
└──────────────────┬──────────────────────┬───────────────────┘
                   │ HTTPS/REST           │ WSS WebSocket
                   ▼                      ▼
┌─────────────────────────────────────────────────────────────┐
│                  Spring Boot 3  (Java 21)                   │
│                                                             │
│  ┌────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ REST Controllers│  │ WebSocket Handler│  │JWT Security │  │
│  └───────┬────────┘  └────────┬────────┘  └─────────────┘  │
│          │                    │                              │
│  ┌───────▼────────────────────▼──────────────────────────┐  │
│  │                 Domain Services Layer                  │  │
│  │  MatchLifecycle · MatchScoring · EpochLockstep         │  │
│  │  ReplaySession  · CandleService · FinnhubProvider      │  │
│  │  Matchmaking    · Backtest      · ELO / XP             │  │
│  └───────┬─────────────────────┬─────────────────────────┘  │
│          │                     │                             │
│  ┌───────▼────────┐   ┌────────▼────────┐                   │
│  │ PostgreSQL/Neon│   │   Redis (7)      │                   │
│  │  games         │   │  Match rooms     │                   │
│  │  stock_candles │   │  Pub/Sub relay   │                   │
│  │  replay_sess.  │   │  Matchmaking     │                   │
│  └────────────────┘   └─────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| **Epoch-lockstep fairness engine** | Both players trade against identical candle snapshots — impossible to gain an advantage via network latency |
| **`afterCommit()` side effects** | Redis/WebSocket mutations only fire if the DB transaction committed — prevents Redis/DB split-brain on rollback |
| **Replay session per game** | Random historical window assigned at game creation — every match is a fresh, unpredictable market scenario |
| **DB-first candle load, JSON fallback** | Production uses real NSE data; dev/test environments fall back to classpath JSON transparently |
| **`@ConditionalOnProperty` Finnhub** | US real-time feed activates only when `FINNHUB_ENABLED=true` — zero overhead when disabled |

---

## 🛠 Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Application language |
| Spring Boot | 3.2 | REST API + WebSocket server |
| Spring Security + JWT | — | Stateless auth with refresh tokens |
| Spring Data JPA | — | ORM — PostgreSQL via Hibernate |
| PostgreSQL (Neon) | — | Primary database + Flyway migrations |
| Redis | 7 | Matchmaking queue, session state, Pub/Sub |
| STOMP / SockJS | — | Real-time WebSocket protocol |
| Bucket4j | — | API rate limiting |
| Micrometer + Actuator | — | Metrics & health endpoints |
| yfinance (Python) | — | Historical NSE market data ingestion |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 18 | Single-page application |
| Lightweight Charts | 5 | Candlestick chart rendering |
| STOMP.js / SockJS | — | WebSocket client |
| Axios | — | HTTP API client |
| React Router | 6 | Client-side routing |

### Infrastructure
| Service | Purpose |
|---|---|
| Neon (PostgreSQL) | Serverless DB — 4,970+ candle rows, Flyway migrations |
| Render | Backend production hosting (auto-deploy from `main`) |
| Vercel | Frontend CDN deployment (auto-deploy from `main`) |
| GitHub Actions | CI — `mvn verify` on every push (113 tests) |

---

## 📁 Project Structure

```
tradelearn/
├── backend/                              Spring Boot application
│   └── src/main/java/com/tradelearn/
│       ├── fairness/                     Epoch-lockstep engine (latency fairness)
│       └── server/
│           ├── auth/                     JWT authentication, security config
│           ├── game/                     Match lifecycle, scoring, trading, queries
│           ├── infrastructure/           Redis rooms, resilience, scheduling, rate limiting
│           ├── leaderboard/              ELO ranking, tier badges
│           ├── market/                   ← New: candle DB layer
│           │   ├── model/                StockSymbol, StockCandleDaily, GameReplaySession
│           │   ├── repository/           JPA repositories with range queries
│           │   ├── service/              CandleService, ReplaySessionService
│           │   ├── replay/               TickReplayEngine (solo simulator)
│           │   ├── controller/           SimulatorTickController REST API
│           │   └── provider/             FinnhubWebSocketProvider (US live feed)
│           ├── matchmaking/              Redis ZSET queue, Lua atomic scripts
│           ├── profile/                  User profile assembly
│           └── websocket/               STOMP handler, broadcaster, Redis relay
│   └── src/main/resources/
│       └── db/migration/                 Flyway V1–V7 schema migrations
│
├── frontend/src/
│   ├── api/                              Axios client + domain API modules
│   └── features/
│       ├── auth/                         Login, register, forgot password
│       ├── game/                         Lobby, live match, result, history
│       ├── leaderboard/                  Rankings, tier badges
│       ├── simulator/                    Candlestick chart, missions, practice
│       ├── strategies/                   Strategy cards and detail views
│       └── social/                       Friends panel, challenge listener
│
├── scripts/
│   ├── ingest_market_data.py             NSE historical data ingestion (yfinance)
│   └── requirements.txt
│
├── docs/                                 Architecture, API reference, demo guide
├── e2e/                                  Playwright production E2E tests
├── loadtest/                             k6 load tests + monitoring dashboards
└── .github/workflows/                    CI/CD pipelines
```

---

## 🚀 Quick Start

### Prerequisites
- Java 21+, Maven 3.9+
- Node.js 18+, npm
- Redis 7 (local or Docker)
- PostgreSQL or a [Neon](https://neon.tech) serverless DB

### 1. Clone & configure

```bash
git clone https://github.com/Koushik-31368/tradelearn-project.git
cd tradelearn-project

# Backend config
cp backend/.env.example backend/src/main/resources/application-local.properties
# Fill in: SPRING_DATASOURCE_URL, JWT_SECRET, REDIS_HOST
```

### 2. Populate market data (one-time)

```bash
cd scripts
pip install -r requirements.txt

# Add NEON_DATABASE_URL to scripts/.env, then:
python ingest_market_data.py --days 730
# → Ingests 4,970 rows of NSE daily OHLCV data
```

### 3. Start backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
# → http://localhost:8080/actuator/health
```

### 4. Start frontend

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

---

## ⚙️ Environment Variables

### Backend

| Variable | Required | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | ✅ | JDBC PostgreSQL URL |
| `SPRING_DATASOURCE_USERNAME` | ✅ | DB username |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | DB password |
| `JWT_SECRET` | ✅ | Min 64-char random string |
| `REDIS_HOST` | ✅ | Redis hostname |
| `CORS_ALLOWED_ORIGINS` | ✅ | Comma-separated allowed origins |
| `FINNHUB_ENABLED` | ❌ | `true` to enable US real-time feed |
| `FINNHUB_API_KEY` | ❌ | Finnhub API key (if enabled) |

### Frontend

| Variable | Required | Description |
|---|---|---|
| `REACT_APP_API_URL` | ❌ | Backend base URL (empty = relative) |
| `REACT_APP_WS_URL` | ❌ | WebSocket base URL |

---

## 🗺 Roadmap

- [x] Real NSE market data replay engine (4,970 rows, 10 symbols)
- [x] Epoch-lockstep fairness engine (latency-immune trading)
- [x] ELO ranking + composite scoring
- [x] Solo simulator with SMA overlays
- [x] Finnhub real-time US feed (optional)
- [ ] Trade replay — animated candle-by-candle match review
- [ ] Tournament mode — bracket-style multi-round competitions
- [ ] Advanced analytics — win-rate trends, strategy performance heatmap
- [ ] Mobile-responsive simulator
- [ ] OpenAPI/Swagger — auto-generated REST docs

---

## 📄 License

Proprietary. All rights reserved. © 2026 Koushik Reedy

---

<div align="center">

Built with ☕ Java + ⚛️ React · Deployed on Render + Vercel + Neon

</div>
