/**
 * TradeLearn — Full Load Test Script (k6)
 *
 * Simulates 10,000 concurrent users in 1,000 active 1v1 matches
 * with 5 trades/sec/player, random disconnect/reconnect, sustained 30 min.
 *
 * Prerequisites:
 *   - k6 with xk6-websockets extension (k6 ≥ v0.46 has native WS support)
 *   - Target cluster reachable at BASE_URL
 *   - Database seeded (see seed-users.js)
 *
 * Run:
 *   k6 run --out json=results.json \
 *           -e BASE_URL=http://tradelearn-api.loadtest.svc.cluster.local:8080 \
 *           -e WS_URL=ws://tradelearn-api.loadtest.svc.cluster.local:8080 \
 *           tradelearn-load.js
 */

import http from "k6/http";
import ws from "k6/ws";
import { check, sleep, group } from "k6";
import { Counter, Rate, Trend, Gauge } from "k6/metrics";
import { SharedArray } from "k6/data";
import { randomIntBetween } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";
import exec from "k6/execution";

// ─── Configuration ──────────────────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const WS_URL = __ENV.WS_URL || "ws://localhost:8080";
const TOTAL_USERS = parseInt(__ENV.TOTAL_USERS || "10000");
const GAMES_TARGET = parseInt(__ENV.GAMES_TARGET || "1000");
const TRADE_INTERVAL_MS = 200; // 5 trades/sec/player
const MATCH_DURATION_MIN = 5;
const STARTING_BALANCE = 1000000.0;
const DISCONNECT_PROBABILITY = 0.02; // 2% chance per iteration
const RECONNECT_DELAY_SEC_MIN = 2;
const RECONNECT_DELAY_SEC_MAX = 8;

const STOCK_SYMBOLS = [
  "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK",
  "HINDUNILVR", "SBIN", "BHARTIARTL", "ITC", "KOTAKBANK",
  "LT", "AXISBANK", "ASIANPAINT", "MARUTI", "TITAN",
  "SUNPHARMA", "ULTRACEMCO", "WIPRO", "NESTLEIND", "BAJFINANCE",
];

// ─── Custom Metrics ─────────────────────────────────────────────────────────

const tradeLatency = new Trend("trade_latency_ms", true);
const wsMessageLatency = new Trend("ws_message_latency_ms", true);
const loginLatency = new Trend("login_latency_ms", true);
const createGameLatency = new Trend("create_game_latency_ms", true);
const joinGameLatency = new Trend("join_game_latency_ms", true);
const tradeSuccess = new Rate("trade_success_rate");
const wsConnectSuccess = new Rate("ws_connect_success_rate");
const tradesPlaced = new Counter("trades_placed_total");
const wsMessagesReceived = new Counter("ws_messages_received_total");
const disconnectEvents = new Counter("disconnect_events_total");
const reconnectEvents = new Counter("reconnect_events_total");
const activeGames = new Gauge("active_games");
const activeWsConnections = new Gauge("active_ws_connections");
const httpErrors = new Counter("http_errors_total");
const wsErrors = new Counter("ws_errors_total");

// ─── Pre-generated User Pool ────────────────────────────────────────────────

// Users are pre-seeded. This array maps VU numbers to credentials.
const users = new SharedArray("users", function () {
  const arr = [];
  for (let i = 0; i < TOTAL_USERS; i++) {
    arr.push({
      email: `loadtest_user_${i}@tradelearn.test`,
      username: `lt_user_${i}`,
      password: `LoadTest1${i}`,
    });
  }
  return arr;
});

// ─── Stages ─────────────────────────────────────────────────────────────────

export const options = {
  scenarios: {
    // Phase 1: Match lifecycle — creators create and opponents join
    match_creators: {
      executor: "ramping-vus",
      exec: "matchCreator",
      startVUs: 0,
      stages: [
        { duration: "2m", target: GAMES_TARGET },       // ramp to 1000 creators
        { duration: "26m", target: GAMES_TARGET },      // sustain
        { duration: "2m", target: 0 },                  // ramp down
      ],
      gracefulRampDown: "30s",
      tags: { role: "creator" },
    },
    match_opponents: {
      executor: "ramping-vus",
      exec: "matchOpponent",
      startVUs: 0,
      stages: [
        { duration: "2m", target: GAMES_TARGET },       // ramp to 1000 opponents
        { duration: "26m", target: GAMES_TARGET },      // sustain
        { duration: "2m", target: 0 },                  // ramp down
      ],
      startTime: "15s", // slight lag so games exist to join
      gracefulRampDown: "30s",
      tags: { role: "opponent" },
    },
    // Phase 2: Pure REST trade blasters — supplementary load
    rest_traders: {
      executor: "ramping-vus",
      exec: "restTrader",
      startVUs: 0,
      stages: [
        { duration: "3m", target: 4000 },
        { duration: "24m", target: 4000 },
        { duration: "3m", target: 0 },
      ],
      startTime: "3m",  // wait for games to exist
      gracefulRampDown: "30s",
      tags: { role: "rest_trader" },
    },
    // Phase 3: Read-heavy observers (leaderboard, match list polling)
    observers: {
      executor: "constant-arrival-rate",
      exec: "observer",
      rate: 500,           // 500 iterations/sec
      timeUnit: "1s",
      duration: "28m",
      preAllocatedVUs: 200,
      maxVUs: 500,
      startTime: "1m",
      tags: { role: "observer" },
    },
    // Phase 4: Health check monitor
    health_monitor: {
      executor: "constant-arrival-rate",
      exec: "healthCheck",
      rate: 2,
      timeUnit: "1s",
      duration: "30m",
      preAllocatedVUs: 5,
      maxVUs: 10,
      tags: { role: "monitor" },
    },
  },
  thresholds: {
    // ── Latency SLOs ──
    "trade_latency_ms":        ["p(95)<200", "p(99)<500", "max<2000"],
    "ws_message_latency_ms":   ["p(95)<100", "p(99)<300"],
    "login_latency_ms":        ["p(95)<500", "p(99)<1000"],
    "create_game_latency_ms":  ["p(95)<1000", "p(99)<2000"],
    "join_game_latency_ms":    ["p(95)<1000", "p(99)<2000"],

    // ── Reliability SLOs ──
    "trade_success_rate":      ["rate>0.95"],
    "ws_connect_success_rate": ["rate>0.90"],
    "http_req_failed":         ["rate<0.05"],    // <5% HTTP error rate
    "http_req_duration":       ["p(95)<1000"],   // overall p95 < 1s

    // ── Throughput floors ──
    "trades_placed_total":     ["count>500000"], // expect ~5*10000*30*60/2 minimum
  },
};

// ─── Helpers ────────────────────────────────────────────────────────────────

function getUserForVU() {
  const idx = (exec.vu.idInTest - 1) % TOTAL_USERS;
  return users[idx];
}

function getCreatorUser(vuId) {
  // Creators use even-indexed users [0, 2, 4, ...]
  const idx = ((vuId - 1) * 2) % TOTAL_USERS;
  return users[idx];
}

function getOpponentUser(vuId) {
  // Opponents use odd-indexed users [1, 3, 5, ...]
  const idx = ((vuId - 1) * 2 + 1) % TOTAL_USERS;
  return users[idx];
}

function authHeaders(token) {
  return {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
  };
}

function login(user) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );
  loginLatency.add(Date.now() - start);

  if (res.status !== 200) {
    // Try register if login fails (first run)
    const regRes = http.post(
      `${BASE_URL}/api/auth/register`,
      JSON.stringify(user),
      { headers: { "Content-Type": "application/json" }, tags: { name: "register" } }
    );
    if (regRes.status === 200 || regRes.status === 201) {
      const body = JSON.parse(regRes.body);
      return { token: body.token, userId: body.id };
    }
    httpErrors.add(1);
    return null;
  }

  const body = JSON.parse(res.body);
  return { token: body.token, userId: body.id };
}

function createMatch(token, symbol) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/match/create`,
    JSON.stringify({
      stockSymbol: symbol,
      durationMinutes: MATCH_DURATION_MIN,
      startingBalance: STARTING_BALANCE,
    }),
    { ...authHeaders(token), tags: { name: "create_match" } }
  );
  createGameLatency.add(Date.now() - start);

  if (res.status === 200 || res.status === 201) {
    const game = JSON.parse(res.body);
    return game.id || game.gameId;
  }
  httpErrors.add(1);
  return null;
}

function joinMatch(token, gameId) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/match/${gameId}/join`,
    null,
    { ...authHeaders(token), tags: { name: "join_match" } }
  );
  joinGameLatency.add(Date.now() - start);

  check(res, { "join match succeeded": (r) => r.status === 200 });
  if (res.status !== 200) {
    httpErrors.add(1);
    return false;
  }
  return true;
}

function placeTrade(token, gameId, symbol, type, quantity) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/match/trade`,
    JSON.stringify({
      gameId: gameId,
      symbol: symbol,
      type: type,
      quantity: quantity,
    }),
    { ...authHeaders(token), tags: { name: "place_trade" } }
  );
  const elapsed = Date.now() - start;
  tradeLatency.add(elapsed);

  const success = res.status === 200 || res.status === 201;
  tradeSuccess.add(success ? 1 : 0);
  if (success) {
    tradesPlaced.add(1);
  } else {
    httpErrors.add(1);
  }
  return success;
}

function connectWebSocket(token, gameId, userId, onMessage) {
  const url = `${WS_URL}/ws?token=${token}`;

  const res = ws.connect(url, {}, function (socket) {
    wsConnectSuccess.add(1);
    activeWsConnections.add(1);

    // STOMP CONNECT
    socket.send(
      "CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0"
    );

    // Subscribe to all relevant game topics
    const subs = [
      `/topic/game/${gameId}/candle`,
      `/topic/game/${gameId}/started`,
      `/topic/game/${gameId}/finished`,
      `/topic/game/${gameId}/trade`,
      `/topic/game/${gameId}/scoreboard`,
      `/topic/game/${gameId}/error/${userId}`,
      `/topic/game/${gameId}/player-reconnecting`,
      `/topic/game/${gameId}/player-reconnected`,
      `/topic/game/${gameId}/player-disconnected`,
    ];

    socket.on("open", function () {
      sleep(0.5); // Wait for CONNECTED frame
      subs.forEach((dest, i) => {
        socket.send(
          `SUBSCRIBE\nid:sub-${i}\ndestination:${dest}\n\n\0`
        );
      });
    });

    socket.on("message", function (msg) {
      wsMessagesReceived.add(1);
      const receiveTime = Date.now();

      // Parse STOMP MESSAGE frames
      if (msg.startsWith("MESSAGE")) {
        const bodyStart = msg.indexOf("\n\n");
        if (bodyStart > 0) {
          const body = msg.substring(bodyStart + 2).replace("\0", "");
          try {
            const data = JSON.parse(body);
            // Track candle broadcast latency if timestamp is present
            if (data.timestamp) {
              wsMessageLatency.add(receiveTime - data.timestamp);
            } else {
              wsMessageLatency.add(0); // can't measure, record 0
            }
            if (onMessage) onMessage(data, msg);
          } catch (_) {
            // Non-JSON STOMP frame (CONNECTED, RECEIPT, etc.)
          }
        }
      }
    });

    socket.on("error", function (e) {
      wsErrors.add(1);
      wsConnectSuccess.add(0);
    });

    socket.on("close", function () {
      activeWsConnections.add(-1);
    });

    // Send STOMP trade via WebSocket
    socket.stompTrade = function (type, quantity, symbol) {
      const tradePayload = JSON.stringify({
        type: type,
        amount: quantity,
        price: 0, // server-authoritative
        playerId: userId,
        symbol: symbol,
      });
      socket.send(
        `SEND\ndestination:/app/game/${gameId}/trade\ncontent-type:application/json\n\n${tradePayload}\0`
      );
      tradesPlaced.add(1);
    };

    // Send ready signal
    socket.sendReady = function () {
      socket.send(
        `SEND\ndestination:/app/game/${gameId}/ready\ncontent-type:application/json\n\n{}\0`
      );
    };

    // Rejoin after disconnect
    socket.sendRejoin = function () {
      socket.send(
        `SEND\ndestination:/app/game/${gameId}/rejoin\ncontent-type:application/json\n\n{"userId":${userId}}\0`
      );
      reconnectEvents.add(1);
    };

    return socket;
  });

  return res;
}

function randomTradeType(hasPosition) {
  if (!hasPosition) {
    // No position yet — can only BUY or SHORT
    return Math.random() < 0.7 ? "BUY" : "SHORT";
  }
  const r = Math.random();
  if (r < 0.35) return "BUY";
  if (r < 0.55) return "SELL";
  if (r < 0.75) return "SHORT";
  return "COVER";
}

function randomQuantity() {
  return randomIntBetween(1, 50);
}

// ─── Scenario: Match Creator ────────────────────────────────────────────────

export function matchCreator() {
  const user = getCreatorUser(exec.vu.idInTest);
  const auth = login(user);
  if (!auth) return;

  const symbol =
    STOCK_SYMBOLS[randomIntBetween(0, STOCK_SYMBOLS.length - 1)];

  group("create_match", function () {
    const gameId = createMatch(auth.token, symbol);
    if (!gameId) return;

    activeGames.add(1);

    // Connect WebSocket and trade for the duration of the match
    let gameFinished = false;
    let hasPosition = false;

    const wsUrl = `${WS_URL}/ws?token=${auth.token}`;
    const res = ws.connect(wsUrl, {}, function (socket) {
      wsConnectSuccess.add(1);
      activeWsConnections.add(1);

      // STOMP handshake
      socket.send("CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0");

      socket.on("open", function () {
        sleep(0.3);
        // Subscribe to game topics
        const topics = [
          "candle", "started", "finished", "trade", "scoreboard",
          `error/${auth.userId}`, "player-reconnecting",
          "player-reconnected", "player-disconnected",
        ];
        topics.forEach((t, i) => {
          socket.send(
            `SUBSCRIBE\nid:sub-${i}\ndestination:/topic/game/${gameId}/${t}\n\n\0`
          );
        });
      });

      socket.on("message", function (msg) {
        wsMessagesReceived.add(1);
        if (msg.includes("/finished")) {
          gameFinished = true;
          socket.close();
        }
      });

      socket.on("close", function () {
        activeWsConnections.add(-1);
      });

      socket.on("error", function () {
        wsErrors.add(1);
      });

      // Trading loop — 5 trades/sec sustained
      socket.setTimeout(function () {
        tradingLoop(socket, auth, gameId, symbol);
      }, 2000); // wait for opponent to join

      // Disconnect/reconnect simulation
      socket.setTimeout(function () {
        disconnectReconnectLoop(socket, auth, gameId);
      }, randomIntBetween(30000, 120000));
    });

    // After WS disconnects, game may still be active via REST
    if (!gameFinished) {
      // Sustained REST trading fallback
      restTradingLoop(auth, gameId, symbol, 30);
    }

    activeGames.add(-1);
  });
}

function tradingLoop(socket, auth, gameId, symbol) {
  let tradeCount = 0;
  let hasPosition = false;
  const maxTrades = MATCH_DURATION_MIN * 60 * 5; // 5 trades/sec × duration

  const intervalId = socket.setInterval(function () {
    if (tradeCount >= maxTrades) return;

    const type = randomTradeType(hasPosition);
    const qty = randomQuantity();

    // Send trade via STOMP
    const payload = JSON.stringify({
      type: type,
      amount: qty,
      price: 0,
      playerId: auth.userId,
      symbol: symbol,
    });
    socket.send(
      `SEND\ndestination:/app/game/${gameId}/trade\ncontent-type:application/json\n\n${payload}\0`
    );
    tradesPlaced.add(1);
    tradeCount++;

    if (type === "BUY" || type === "SHORT") hasPosition = true;
  }, TRADE_INTERVAL_MS);
}

function disconnectReconnectLoop(socket, auth, gameId) {
  // Random disconnect simulation
  if (Math.random() < DISCONNECT_PROBABILITY) {
    disconnectEvents.add(1);
    socket.close();

    // Wait grace period then reconnect
    sleep(randomIntBetween(RECONNECT_DELAY_SEC_MIN, RECONNECT_DELAY_SEC_MAX));

    // Reconnect via new WebSocket
    const wsUrl = `${WS_URL}/ws?token=${auth.token}`;
    ws.connect(wsUrl, {}, function (newSocket) {
      wsConnectSuccess.add(1);
      activeWsConnections.add(1);
      reconnectEvents.add(1);

      newSocket.send("CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0");
      sleep(0.3);

      // Rejoin
      newSocket.send(
        `SEND\ndestination:/app/game/${gameId}/rejoin\ncontent-type:application/json\n\n{"userId":${auth.userId}}\0`
      );

      // Resubscribe
      const topics = [
        "candle", "started", "finished", "trade", "scoreboard",
        `error/${auth.userId}`,
      ];
      topics.forEach((t, i) => {
        newSocket.send(
          `SUBSCRIBE\nid:sub-${i}\ndestination:/topic/game/${gameId}/${t}\n\n\0`
        );
      });

      newSocket.on("close", function () {
        activeWsConnections.add(-1);
      });
    });
  }
}

function restTradingLoop(auth, gameId, symbol, maxSeconds) {
  const deadline = Date.now() + maxSeconds * 1000;
  let hasPosition = false;

  while (Date.now() < deadline) {
    const type = randomTradeType(hasPosition);
    const qty = randomQuantity();

    placeTrade(auth.token, gameId, symbol, type, qty);
    if (type === "BUY" || type === "SHORT") hasPosition = true;

    sleep(TRADE_INTERVAL_MS / 1000);
  }
}

// ─── Scenario: Match Opponent ───────────────────────────────────────────────

export function matchOpponent() {
  const user = getOpponentUser(exec.vu.idInTest);
  const auth = login(user);
  if (!auth) return;

  group("join_and_trade", function () {
    // Find an open match to join
    const openRes = http.get(`${BASE_URL}/api/match/open`, {
      tags: { name: "list_open_matches" },
    });
    if (openRes.status !== 200) {
      httpErrors.add(1);
      sleep(2);
      return;
    }

    let openGames;
    try {
      openGames = JSON.parse(openRes.body);
    } catch (_) {
      sleep(2);
      return;
    }

    if (!openGames || openGames.length === 0) {
      sleep(2);
      return;
    }

    // Pick a random open game
    const game = openGames[randomIntBetween(0, openGames.length - 1)];
    const gameId = game.id || game.gameId;
    const symbol = game.stockSymbol || "RELIANCE";

    if (!joinMatch(auth.token, gameId)) {
      sleep(1);
      return;
    }

    // Connect WebSocket and trade
    let gameFinished = false;
    const wsUrl = `${WS_URL}/ws?token=${auth.token}`;

    ws.connect(wsUrl, {}, function (socket) {
      wsConnectSuccess.add(1);
      activeWsConnections.add(1);

      socket.send("CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0");

      socket.on("open", function () {
        sleep(0.3);
        const topics = [
          "candle", "started", "finished", "trade", "scoreboard",
          `error/${auth.userId}`,
        ];
        topics.forEach((t, i) => {
          socket.send(
            `SUBSCRIBE\nid:sub-${i}\ndestination:/topic/game/${gameId}/${t}\n\n\0`
          );
        });
      });

      socket.on("message", function (msg) {
        wsMessagesReceived.add(1);
        if (msg.includes("/finished")) {
          gameFinished = true;
          socket.close();
        }
      });

      socket.on("close", function () {
        activeWsConnections.add(-1);
      });

      socket.on("error", function () {
        wsErrors.add(1);
      });

      // Trading loop
      socket.setTimeout(function () {
        tradingLoop(socket, auth, gameId, symbol);
      }, 1000);

      // Random disconnect/reconnect
      socket.setTimeout(function () {
        disconnectReconnectLoop(socket, auth, gameId);
      }, randomIntBetween(60000, 180000));
    });

    if (!gameFinished) {
      restTradingLoop(auth, gameId, symbol, 30);
    }
  });
}

// ─── Scenario: REST Trader (supplementary load) ─────────────────────────────

export function restTrader() {
  const user = getUserForVU();
  const auth = login(user);
  if (!auth) return;

  // Find an active game this user is in
  const myGames = http.get(`${BASE_URL}/api/match/user/me`, {
    ...authHeaders(auth.token),
    tags: { name: "my_matches" },
  });

  if (myGames.status !== 200) {
    httpErrors.add(1);
    sleep(5);
    return;
  }

  let games;
  try {
    games = JSON.parse(myGames.body);
  } catch (_) {
    sleep(5);
    return;
  }

  const activeGame = games.find((g) => g.status === "ACTIVE");
  if (!activeGame) {
    sleep(5);
    return;
  }

  const gameId = activeGame.id || activeGame.gameId;
  const symbol = activeGame.stockSymbol || "RELIANCE";

  // Burst trade — 5 trades/sec for 10 seconds
  restTradingLoop(auth, gameId, symbol, 10);
}

// ─── Scenario: Observer (read-heavy) ────────────────────────────────────────

export function observer() {
  group("observer_reads", function () {
    // Parallelize read requests
    const responses = http.batch([
      ["GET", `${BASE_URL}/api/match/active`, null, { tags: { name: "list_active" } }],
      ["GET", `${BASE_URL}/api/match/open`, null, { tags: { name: "list_open" } }],
      ["GET", `${BASE_URL}/api/match/finished`, null, { tags: { name: "list_finished" } }],
      ["GET", `${BASE_URL}/api/users/leaderboard`, null, { tags: { name: "leaderboard" } }],
    ]);

    responses.forEach((r) => {
      check(r, { "observer read OK": (res) => res.status === 200 });
    });

    // Pick a random active game and poll its details
    try {
      const active = JSON.parse(responses[0].body);
      if (active && active.length > 0) {
        const game = active[randomIntBetween(0, Math.min(active.length - 1, 49))];
        const gid = game.id || game.gameId;

        const detailResponses = http.batch([
          ["GET", `${BASE_URL}/api/match/${gid}`, null, { tags: { name: "match_detail" } }],
          ["GET", `${BASE_URL}/api/match/${gid}/trades`, null, { tags: { name: "match_trades" } }],
          ["GET", `${BASE_URL}/api/match/${gid}/stats`, null, { tags: { name: "match_stats" } }],
          ["GET", `${BASE_URL}/api/match/${gid}/candle`, null, { tags: { name: "current_candle" } }],
        ]);

        detailResponses.forEach((r) => {
          check(r, { "detail read OK": (res) => res.status === 200 });
        });
      }
    } catch (_) {
      // ignore parse errors
    }
  });
}

// ─── Scenario: Health Check ─────────────────────────────────────────────────

export function healthCheck() {
  const res = http.get(`${BASE_URL}/api/health`, {
    tags: { name: "health" },
  });

  check(res, {
    "health check OK": (r) => r.status === 200,
    "health response has uptime": (r) => {
      try {
        return JSON.parse(r.body).uptimeSeconds > 0;
      } catch (_) {
        return false;
      }
    },
  });

  // Also hit Prometheus endpoint if available
  const promRes = http.get(`${BASE_URL}/actuator/prometheus`, {
    tags: { name: "prometheus" },
  });
  check(promRes, { "prometheus OK": (r) => r.status === 200 });
}

// ─── Setup / Teardown ───────────────────────────────────────────────────────

export function setup() {
  console.log(`
╔══════════════════════════════════════════════════════════╗
║           TradeLearn Load Test — Configuration           ║
╠══════════════════════════════════════════════════════════╣
║  Target:    ${BASE_URL.padEnd(43)}║
║  Users:     ${String(TOTAL_USERS).padEnd(43)}║
║  Games:     ${String(GAMES_TARGET).padEnd(43)}║
║  Duration:  30 minutes sustained                        ║
║  Trade Rate: 5/sec/player (${TRADE_INTERVAL_MS}ms interval)              ║
╚══════════════════════════════════════════════════════════╝
  `);

  // Verify target is reachable
  const healthRes = http.get(`${BASE_URL}/api/health`);
  check(healthRes, {
    "target reachable": (r) => r.status === 200,
  });

  if (healthRes.status !== 200) {
    console.error("TARGET NOT REACHABLE — aborting");
    return null;
  }

  return { startTime: Date.now() };
}

export function teardown(data) {
  if (!data) return;
  const elapsed = (Date.now() - data.startTime) / 1000;
  console.log(`
╔══════════════════════════════════════════════════════════╗
║           Load Test Complete                             ║
║  Duration: ${elapsed.toFixed(0).padEnd(44)}║
╚══════════════════════════════════════════════════════════╝
  `);
}
