# TradeLearn

**Competitive trading skill platform — learn strategies, practice in simulation, compete in ranked matches.**

---

## Overview

TradeLearn is a full-stack competitive trading simulation platform that treats trading education as a skill progression system rather than passive content consumption. Users learn market fundamentals, apply documented strategies in a realistic simulator, and compete in head-to-head ranked matches scored on profit, risk management, and accuracy.

The platform exists to bridge the gap between theoretical trading education and real market decision-making. Unlike conventional paper trading apps, TradeLearn introduces competitive pressure, structured scoring, and ELO-based ranking to create a measurable skill development environment.

**What makes it different:**

- Scoring rewards discipline, not gambling — 60% Profit, 20% Risk Management, 20% Accuracy
- Strategy-first approach — users learn and apply documented strategies, not random trades
- Competitive ranking — ELO system places traders against equally skilled opponents
- Skill tier progression — Beginner, Intermediate, Advanced, Elite tiers derived from rating
- Real-time multiplayer — WebSocket-based head-to-head matches with live game state

---

## Core Features

### Learning Academy
Structured curriculum covering candlestick patterns, technical indicators, risk management fundamentals, and trading psychology. Content is organized into progressive sections with clear learning objectives.

### Strategy Engine
Eight documented trading strategies (RSI Mean Reversion, SMA Crossover, Breakout, Momentum, Support and Resistance, Scalping, Buy and Hold, MACD) with detailed entry/exit rules, risk parameters, and market condition guidelines. Each strategy links directly to the simulator for practice.

### Trading Simulator
Candlestick chart-based simulator with real-time market sentiment analysis (trend, volatility, momentum), portfolio tracking, transaction history, and performance charting. Includes watchlist, SMA overlays, and equity curve visualization.

### Multiplayer Ranked Matches
Real-time head-to-head trading matches via STOMP-over-WebSocket. Players trade simultaneously on the same market data. Matches produce scored results that update ELO ratings.

### ELO Ranking System
Chess-style ELO rating system. Wins against higher-rated opponents yield larger rating gains. Skill tiers (Beginner, Intermediate, Advanced, Elite) are derived from rating thresholds.

### Performance Tracking
Match history with detailed results, leaderboard with tier badges, profile pages with rating progression, and portfolio analytics.

---

## Scoring System

Every match is evaluated on three weighted dimensions:

| Component | Weight | Description |
|-----------|--------|-------------|
| Profit | 60% | Net portfolio return relative to starting capital |
| Risk Management | 20% | Position sizing discipline and drawdown control |
| Accuracy | 20% | Percentage of profitable trades out of total trades |

This scoring model rewards consistent, disciplined trading over high-risk speculation.

---

## Tech Stack

### Frontend
| Technology | Purpose |
|------------|---------|
| React 19 | UI framework |
| React Router 7 | Client-side routing |
| Lightweight Charts | Candlestick chart rendering |
| STOMP.js + SockJS | Real-time WebSocket communication |
| Axios | HTTP client |
| CSS (per-component) | Styling — no external UI library |

### Backend
| Technology | Purpose |
|------------|---------|
| Java 17 | Runtime |
| Spring Boot 3.2 | Application framework |
| Spring Security + JWT | Authentication and authorization |
| Spring WebSocket | Real-time multiplayer communication |
| Spring Data JPA | Database access layer |
| PostgreSQL / MySQL | Relational database |
| Redis + Lettuce | Session caching, distributed state |
| Redisson | Distributed locks for match concurrency |
| Bucket4j | API rate limiting |
| Spring Actuator + Micrometer | Health checks and Prometheus metrics |

### Infrastructure
| Technology | Purpose |
|------------|---------|
| Docker + Docker Compose | Containerized deployment |
| Nginx | Frontend static file serving and reverse proxy |
| Railway | Production hosting |
| Vercel | Frontend CDN (alternative deployment) |

---

## Architecture

```
Client (React SPA)
  |
  |-- REST API (Axios) --> Spring Boot Controllers --> Services --> JPA --> PostgreSQL
  |
  |-- WebSocket (STOMP/SockJS) --> Spring WebSocket Handlers --> Redis Pub/Sub --> Game State
  |
  |-- Static Assets --> Nginx / Vercel CDN
```

**Key architectural decisions:**

- **Stateless API** — JWT-based authentication with no server-side session storage
- **Real-time multiplayer** — STOMP protocol over SockJS for bidirectional game communication
- **Distributed locking** — Redisson ensures match state consistency under concurrent writes
- **Rate limiting** — Bucket4j token-bucket algorithm protects API endpoints
- **Frontend calculation** — Simulator metrics (sentiment, volatility, momentum) computed client-side to eliminate unnecessary API calls
- **Modular components** — Each simulator panel, page, and utility is a self-contained module

---

## Project Structure

```
tradelearn/
|-- backend/
|   |-- src/main/java/com/tradelearn/server/
|   |   |-- config/          # Spring configuration (CORS, WebSocket, Security)
|   |   |-- controller/      # REST API endpoints
|   |   |-- dto/             # Data transfer objects
|   |   |-- model/           # JPA entities
|   |   |-- repository/      # Database repositories
|   |   |-- security/        # JWT filter, auth provider
|   |   |-- service/         # Business logic layer
|   |   |-- socket/          # WebSocket handlers and game logic
|   |   |-- util/            # Shared utilities
|   |   |-- validation/      # Input validation
|   |-- src/main/resources/
|       |-- application.properties
|       |-- application-prod.properties
|
|-- frontend/
|   |-- src/
|   |   |-- components/
|   |   |   |-- simulator/   # SimulatorDashboard, CandlestickChart, TradingPanel,
|   |   |   |                # Watchlist, PortfolioSummary, MarketSentiment
|   |   |   |-- learn/       # LearnCard, LearnSection, QuizCard, CandleDiagram
|   |   |   |-- strategies/  # StrategyCard, StrategyDetail
|   |   |   |-- Navbar, Footer, TierBadge, StockTicker
|   |   |-- pages/           # HomePage, LearnPage, StrategiesPage, SimulatorPage,
|   |   |                    # LobbyPage, GamePage, LeaderboardPage, ProfilePage,
|   |   |                    # MatchResultPage, TermsPage, PrivacyPage, etc.
|   |   |-- context/         # AuthContext (JWT + user state)
|   |   |-- hooks/           # useGameSocket (STOMP lifecycle)
|   |   |-- utils/           # api.js, skillTier.js, simulatorData.js
|   |   |-- styles/          # Shared style modules
|   |-- public/
|
|-- docker-compose.yml
|-- vercel.json
```

---

## Getting Started

### Prerequisites

- Java 17+
- Node.js 18+
- PostgreSQL (or MySQL)
- Redis (optional — required for multiplayer scaling)
- Maven 3.8+

### Backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The server starts on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm start
```

The client starts on `http://localhost:3000`.

### Docker

```bash
docker-compose up --build
```

Starts both frontend and backend containers with networking preconfigured.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile (`local`, `prod`) | `local` |
| `PORT` | Backend server port | `8080` |
| `REACT_APP_API_URL` | Backend API base URL | (empty — uses relative paths) |

---

## Roadmap

- Trade replay system — candle-by-candle replay of completed matches
- Advanced analytics dashboard — win rate trends, strategy performance breakdown
- Tournament mode — bracket-style multi-round competitions
- Mobile-responsive simulator — full trading experience on tablet and mobile
- Social features — follow traders, share match results

---

## License

This project is proprietary. All rights reserved.
