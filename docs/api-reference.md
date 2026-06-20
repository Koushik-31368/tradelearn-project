# TradeLearn API Reference

> **Version:** 1.0  
> **Base URL:** `http://localhost:8080` (dev) / configured via `REACT_APP_API_URL`  
> **Auth:** Bearer JWT — include `Authorization: Bearer <token>` on all protected endpoints.  
> **Content-Type:** `application/json` unless noted.

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Users & Leaderboard](#2-users--leaderboard)
3. [Profile](#3-profile)
4. [Match Lifecycle](#4-match-lifecycle)
5. [Trading (In-Match)](#5-trading-in-match)
6. [Market Data](#6-market-data)
7. [Analytics & Backtesting](#7-analytics--backtesting)
8. [Matchmaking](#8-matchmaking)
9. [Social](#9-social)
10. [WebSocket Events](#10-websocket-events)

---

## 1. Authentication

### `POST /api/auth/register`
Register a new user.

**Body:**
```json
{ "username": "trader1", "email": "trader@example.com", "password": "secret" }
```

**Response:** `200` — JWT token + user details.
```json
{ "token": "<jwt>", "userId": 1, "username": "trader1" }
```

---

### `POST /api/auth/login`
Log in with email + password.

**Body:**
```json
{ "email": "trader@example.com", "password": "secret" }
```

**Response:** `200` — same shape as register.

---

### `GET /api/auth/me` 🔒
Return the currently authenticated user.

**Response:** `200`
```json
{ "id": 1, "username": "trader1", "email": "...", "rating": 1200 }
```

---

## 2. Users & Leaderboard

### `GET /api/users/leaderboard`
Full leaderboard, ordered by rating descending.

**Response:** `200` — array of `LeaderboardDTO`
```json
[
  { "userId": 1, "username": "trader1", "rating": 1500, "rank": "Diamond" },
  ...
]
```

---

### `GET /api/users/leaderboard/top10`
Top 10 players only.

---

### `GET /api/users/leaderboard/tier/{tierName}`
Filter leaderboard by tier name (case-insensitive).

**Path param:** `tierName` — one of `Bronze`, `Silver`, `Gold`, `Platinum`, `Diamond`.

---

## 3. Profile

### `GET /api/profile/{userId}`
Full public profile for a user. Includes win/loss/draw record, match history, and performance stats.

**Path param:** `userId` — database ID of the user.

**Response:** `200`
```json
{
  "userId": 1,
  "username": "trader1",
  "rating": 1500,
  "rankTier": "Diamond",
  "rank": 3,
  "wins": 45,
  "losses": 12,
  "draws": 3,
  "totalFinished": 60,
  "avgDrawdown": 4.2,
  "avgAccuracy": 68.5,
  "avgScore": 72.1,
  "recentMatches": [
    {
      "gameId": 201,
      "stockSymbol": "INFY",
      "status": "FINISHED",
      "result": "WIN",
      "opponentName": "trader2",
      "finalBalance": 1125000.0,
      "startingBalance": 1000000.0,
      "eloDelta": 18,
      "createdAt": "2024-06-01T12:00:00"
    }
  ]
}
```

**Error:** `404` — user not found.

---

## 4. Match Lifecycle

### `POST /api/matches` 🔒
Create a new custom lobby match.

**Body:**
```json
{
  "creatorId": 1,
  "stockSymbol": "TCS",
  "durationMinutes": 5,
  "startingBalance": 1000000
}
```

**Response:** `200` — `Game` object (status `WAITING`).

---

### `POST /api/matches/{gameId}/join` 🔒
Join an open match. Transitions game `WAITING → ACTIVE`.

**Path param:** `gameId`  
**Body:** `{ "userId": 2 }`

**Response:** `200` — updated `Game` object (status `ACTIVE`).  
**Errors:** `400` — game not open; `409` — room full.

---

### `POST /api/matches/{gameId}/start` 🔒
Explicitly start an already-ACTIVE game (crash recovery path).

---

### `DELETE /api/matches/{gameId}` 🔒
Cancel a WAITING lobby. Only the creator can do this.

**Query param:** `userId` — requester's ID (validated server-side).

**Response:** `204 No Content`.  
**Errors:** `403` — not the host; `400` — game not in WAITING state.

---

### `POST /api/matches/{gameId}/end` 🔒
Force-end a match and trigger ELO scoring.

**Response:** `200` — `MatchResult` with final balances, winner, ELO deltas.
```json
{
  "gameId": 201,
  "winner": "trader1",
  "creatorFinalBalance": 1125000.0,
  "opponentFinalBalance": 980000.0,
  "creatorElo": 1518,
  "opponentElo": 1482
}
```

---

### `POST /api/matches/{gameId}/rematch` 🔒
Request a rematch after a finished game.

**Body:** `{ "userId": 1 }`

**Response:** `200` — new game object if both players accept, or pending status.

---

### `GET /api/matches`
List all open (WAITING) matches.

### `GET /api/matches/active`
List all active (in-progress) matches.

### `GET /api/matches/finished`
List all finished matches.

### `GET /api/matches/{gameId}`
Get a specific match by ID.

### `GET /api/matches/user/{userId}` 🔒
Get all matches for a specific user.

---

## 5. Trading (In-Match)

All trade execution happens over **WebSocket** (see [§10](#10-websocket-events)).  
The REST endpoint below is for reading trade history only.

### `GET /api/matches/{gameId}/trades` 🔒
Get all trades placed in a specific match.

**Response:** `200` — array of `Trade` objects.

---

## 6. Market Data

### `GET /api/market/history` 🔒
Fetch historical OHLCV candle data for a symbol and date range.

**Query params:**
| Param | Type | Example |
|---|---|---|
| `symbol` | string | `INFY` |
| `start` | ISO date | `2024-01-01` |
| `end` | ISO date | `2024-06-01` |

**Response:** `200` — array of `Candle`
```json
[
  { "date": "2024-01-02", "open": 1450.0, "high": 1462.5, "low": 1440.0, "close": 1458.0, "volume": 3200000 }
]
```

**Notes:**
- Results are cached server-side (LRU cache, 200 entries max) to avoid redundant Yahoo Finance API calls.
- Symbol format: NSE ticker without `.NS` suffix (e.g. `INFY`, `TCS`, `RELIANCE`).

---

## 7. Analytics & Backtesting

### `POST /api/analytics/backtest`
Run an SMA crossover backtest on a set of candles.

**Body:**
```json
{
  "symbol": "INFY",
  "initialCapital": 1000000,
  "smaFast": 10,
  "smaSlow": 30,
  "candles": [
    { "date": "2024-01-02", "open": 1450.0, "high": 1462.5, "low": 1440.0, "close": 1458.0, "volume": 3200000 }
  ]
}
```

**Response:** `200` — `BacktestResult`
```json
{
  "symbol": "INFY",
  "initialCapital": 1000000,
  "finalCapital": 1148200.0,
  "returnPct": 14.82,
  "maxDrawdownPct": 6.3,
  "winRatePct": 66.7,
  "tradesCount": 6,
  "trades": [ ... ],
  "equityCurve": [ ... ]
}
```

---

### `POST /api/analytics/backtest/batch`
Run backtest across multiple symbols with the same candle set.

**Body:** Same as single backtest but with `symbols: ["TCS", "INFY", "RELIANCE"]` instead of `symbol`.

**Response:** `200` — results sorted by `returnPct` descending.

---

### `GET /api/analytics/stats/{userId}` 🔒
Aggregate performance stats for a user across all finished matches.

---

## 8. Matchmaking

> Matchmaking uses Redis and is only active when `redis.enabled=true`.

### `POST /api/matchmaking/queue` 🔒
Join the matchmaking queue.

**Body:** `{ "userId": 1 }`

**Response:**
- `200` with `gameId` — match found immediately.
- `202 Accepted` — queued; awaiting opponent. Match notification arrives via WebSocket (`match-found` event).

---

### `DELETE /api/matchmaking/queue/{userId}` 🔒
Leave the matchmaking queue.

**Response:** `204 No Content`.

---

### `GET /api/matchmaking/queue/size`
Current queue size (cross-instance via Redis).

**Response:** `200` — `{ "size": 12 }`

---

### `GET /api/matchmaking/queue/status/{userId}` 🔒
Check if a user is in the queue and how long they've waited.

**Response:** `200`
```json
{ "queued": true, "waitSeconds": 15 }
```

---

## 9. Social

### `POST /api/social/friends/request` 🔒
Send a friend request.

**Body:** `{ "senderId": 1, "receiverId": 2 }`

---

### `POST /api/social/friends/accept` 🔒
Accept a pending friend request.

**Body:** `{ "userId": 2, "friendId": 1 }`

---

### `GET /api/social/friends/{userId}` 🔒
List all accepted friends.

---

## 10. WebSocket Events

### Connection
Connect to `ws://<host>/ws` (STOMP over SockJS).  
Authentication: pass JWT as `token` query parameter on the handshake URL.

---

### Client → Server (SEND)

| Destination | Payload | Description |
|---|---|---|
| `/app/game/{gameId}/trade` | `TradeAction` | Place a trade |
| `/app/game/{gameId}/position` | `{}` | Request current position snapshot |
| `/app/game/{gameId}/ready` | `{}` | Signal ready for next round |
| `/app/game/{gameId}/rejoin` | `{}` | Reconnect after disconnect |
| `/app/matchmaking/queue` | `PlayerTicket` | Join matchmaking queue |
| `/app/matchmaking/dequeue` | `{}` | Leave matchmaking queue |

**TradeAction schema:**
```json
{ "type": "BUY", "amount": 100, "price": 1452.0, "symbol": "INFY" }
```
Valid types: `BUY`, `SELL`, `SHORT`, `COVER`. Max 100,000 per order. Rate limit: 5 trades/sec/player/game.

---

### Server → Client (SUBSCRIBE)

| Topic | Event | Payload |
|---|---|---|
| `/topic/game/{gameId}` | `started` | `{ gameId, status, opponentId, opponentUsername }` |
| `/topic/game/{gameId}` | `trade` | Saved `Trade` object |
| `/topic/game/{gameId}` | `candle` | `Candle` — next price candle revealed |
| `/topic/game/{gameId}` | `scoreboard` | `{ player1: {cash, pnl}, player2: {cash, pnl} }` |
| `/topic/game/{gameId}` | `ended` | `MatchResult` |
| `/topic/game/{gameId}` | `nextRound` | `"NEXT_ROUND"` string |
| `/topic/game/{gameId}` | `player-reconnected` | `{ gameId, reconnectedUserId }` |
| `/topic/game/{gameId}` | `error` | `{ message: "..." }` |
| `/user/queue/errors` | — | Personal error message |
| `/user/queue/match-found` | — | `{ gameId, stockSymbol, durationMinutes, startingBalance }` |
| `/user/queue/match-expired` | — | `{ message: "..." }` |
| `/topic/lobby/update` | — | Lobby refresh trigger |

---

## Error Codes

| HTTP Status | Meaning |
|---|---|
| `400` | Validation error (invalid payload, bad state) |
| `401` | Missing or expired JWT |
| `403` | Forbidden (not the owner / participant) |
| `404` | Resource not found |
| `409` | Conflict (room full, already queued, etc.) |
| `429` | Rate limited |
| `500` | Internal server error |

---

## Changelog

| Version | Date | Notes |
|---|---|---|
| 1.0 | June 2026 | Initial API reference — extracted profile domain, consolidated candle DTOs, single Axios client |
