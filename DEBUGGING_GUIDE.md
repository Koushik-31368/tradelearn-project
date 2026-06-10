# Multiplayer Game Debugging System

## Overview

This debugging system provides comprehensive, structured logging for all multiplayer game operations. It helps you instantly identify why a game doesn't start, track player connections, monitor game state transitions, and diagnose trade issues.

## 🎯 Features

### 1. **Structured Logging with Timestamps**
- All logs include ISO-8601 timestamps with millisecond precision
- Consistent format across all services
- Easy to grep and parse

### 2. **Comprehensive Game Lifecycle Tracking**
- Room creation and removal
- Player joins and leaves
- Game state transitions (WAITING → STARTING → ACTIVE → FINISHED)
- Interval/scheduler creation and deletion
- WebSocket connections and disconnections

### 3. **Visual Log Icons**
- 🏠 Room events
- 👤 Player joins
- 🚪 Player leaves
- 🔄 State transitions
- ✅ Game started
- ❌ Game cannot start / errors
- ⏰ Interval created
- ⏳ Interval tick
- ⏹️ Interval deleted
- 🔌 WebSocket connected/disconnected
- 💰 Trades
- 📊 Diagnostic snapshots

### 4. **Diagnostic Snapshots**
Key operations include detailed diagnostic snapshots showing:
- Game ID
- Player IDs
- Room size
- Game state/phase
- Whether scheduler is running
- All relevant context

### 5. **Error Context**
All errors include:
- Operation name
- Game ID
- User ID (when applicable)
- Full exception details
- Stack traces

### 6. **Multiple Log Files**
```
logs/
  tradelearn.log       # All logs
  game-events.log      # Game-specific events with MDC context
  errors.log           # Errors only (kept for 90 days)
```

## 📋 Log File Locations

### Console Output
Colored, human-readable output with emoji indicators for quick visual scanning.

### `logs/tradelearn.log`
Complete log of all application activity. Rolled daily, kept for 30 days.

### `logs/game-events.log`
Structured game events with MDC context (gameId and userId). Format:
```
2026-02-19T10:30:45.123Z | INFO | RoomManager | gameId=123 userId=456 | 🏠 Event details...
```

### `logs/errors.log`
All ERROR-level logs with full stack traces. Kept for 90 days for long-term debugging.

## 🔍 How to Debug: "Why Won't My Game Start?"

### Step 1: Check Room Creation
```bash
grep "ROOM_CREATED" logs/game-events.log | tail -20
```

Look for:
- `gameId` - the game ID
- `creatorId` - user who created the game
- `roomSize` - should be 1
- `phase` - should be "WAITING"

### Step 2: Check Player Join
```bash
grep "PLAYER_JOINED" logs/game-events.log | tail -20
```

Look for:
- Second player joining the room
- `roomSize` - should become 2
- `phase` - should change to "STARTING"
- `isFull` - should be true

### Step 3: Check Game State Transition
```bash
grep "GAME_STATE_TRANSITION" logs/game-events.log | tail -20
```

Should see:
- `WAITING` → `STARTING` (when opponent joins)
- `STARTING` → `ACTIVE` (when scheduler starts)

### Step 4: Check Scheduler Creation
```bash
grep "INTERVAL_CREATED" logs/game-events.log | tail -20
```

Should show:
- `gameId`
- `intervalSeconds` - should be 5
- `task` - should be "candle_progression"

### Step 5: Check for Errors
```bash
grep "GAME_CANNOT_START" logs/errors.log | tail -20
```

This will show exactly why the game didn't start, with full context:
- Room full
- Invalid game state
- Missing opponent
- Database errors
- etc.

## 🐛 Common Issues and Their Logs

### Issue: "Game stuck in WAITING"
**What to check:**
```bash
# Check if opponent joined
grep "gameId=YOUR_GAME_ID" logs/game-events.log | grep "PLAYER_JOINED"

# Check if room is full
grep "gameId=YOUR_GAME_ID" logs/game-events.log | grep "roomSize"
```

**Expected:** You should see a PLAYER_JOINED event with `roomSize=2` and `isFull=true`

**If missing:** Opponent never joined. Check:
- Did they call `/api/matches/{gameId}/join`?
- Is there a "GAME_CANNOT_START" error?
- Is the room already full?

### Issue: "Game stuck in STARTING"
**What to check:**
```bash
# Check scheduler creation
grep "gameId=YOUR_GAME_ID" logs/game-events.log | grep "INTERVAL_CREATED"

# Check state transitions
grep "gameId=YOUR_GAME_ID" logs/game-events.log | grep "GAME_STATE_TRANSITION"
```

**Expected:** Should see INTERVAL_CREATED followed by transition to ACTIVE

**If missing:** Scheduler didn't start. Check:
- `loadCandles()` errors
- `startProgression()` errors
- Database connection issues

### Issue: "Players can't place trades"
**What to check:**
```bash
# Check trade rejections
grep "TRADE_REJECTED" logs/game-events.log | tail -20

# Check for validation errors
grep "TradeValidationException" logs/errors.log | tail -20
```

**Look for:**
- "Insufficient funds"
- "Insufficient shares"
- "Game is not ACTIVE"
- "User is not a participant"

### Issue: "WebSocket disconnections"
**What to check:**
```bash
# Check disconnections
grep "WEBSOCKET_DISCONNECTED" logs/game-events.log | tail -20

# Check if game was abandoned
grep "ABANDONED" logs/game-events.log | tail -20
```

## 🔧 Using the Logging API in Your Code

### GameLogger Methods

#### Room Events
```java
GameLogger.logRoomCreated(log, gameId, creatorId);
GameLogger.logPlayerJoined(log, gameId, userId, roomSize, phase);
GameLogger.logPlayerLeft(log, gameId, userId, remainingPlayers, reason);
GameLogger.logRoomRemoved(log, gameId, reason);
```

#### Game State
```java
GameLogger.logGameStateTransition(log, gameId, oldState, newState, playerCount);
GameLogger.logGameStarted(log, gameId, creatorId, opponentId);
GameLogger.logGameCannotStart(log, gameId, reason, contextMap);
GameLogger.logGameFinished(log, gameId, winnerId, creatorBalance, opponentBalance);
```

#### Intervals/Scheduler
```java
GameLogger.logIntervalCreated(log, gameId, intervalSeconds);
GameLogger.logIntervalTick(log, gameId, currentCandle, totalCandles, price);
GameLogger.logIntervalDeleted(log, gameId, reason);
```

#### Trades
```java
GameLogger.logTradePlaced(log, gameId, userId, type, quantity, symbol, price);
GameLogger.logTradeRejected(log, gameId, userId, type, quantity, reason);
```

#### WebSocket
```java
GameLogger.logWebSocketConnected(log, sessionId, userId, gameId);
GameLogger.logWebSocketDisconnected(log, sessionId, userId, gameId, remainingPlayers);
```

#### Errors
```java
GameLogger.logError(log, "operationName", gameId, exception, contextMap);
```

#### Diagnostic Snapshots
```java
GameLogger.logDiagnosticSnapshot(log, "Snapshot Label", Map.of(
    "key1", value1,
    "key2", value2
));
```

#### MDC Context (for request tracking)
```java
try {
    GameLogger.setGameContext(gameId);
    GameLogger.setUserContext(userId);
    // ... your code ...
} finally {
    GameLogger.clearContext();
}
```

## 📊 Monitoring Active Games

### View All Active Rooms
```bash
grep "DIAGNOSTIC_SNAPSHOT" logs/game-events.log | grep "totalRooms" | tail -5
```

### View Interval Ticks (candle progression)
```bash
grep "INTERVAL_TICK" logs/game-events.log | tail -20
```

### Count Active Games
```bash
grep "phase.*ACTIVE" logs/game-events.log | tail -20
```

## 🚨 Exception Handling

### Custom Exceptions

The system uses typed exceptions for better error handling:

- **GameNotFoundException**: Game not found in database
- **RoomFullException**: Room has max players (2)
- **InvalidGameStateException**: Operation not valid in current game state
- **TradeValidationException**: Trade validation failed (insufficient funds, etc.)

All exceptions are caught by the **GlobalExceptionHandler** which:
- Returns structured JSON error responses
- Logs with full context
- Protects against information leakage

### Example Error Response
```json
{
  "timestamp": "2026-02-19T10:30:45.123Z",
  "status": 400,
  "error": "Invalid Game State",
  "message": "Game 123 is in state 'FINISHED', expected 'ACTIVE'",
  "path": "/api/matches/123/trade",
  "details": {
    "gameId": 123,
    "currentState": "FINISHED",
    "expectedState": "ACTIVE"
  }
}
```

## 🎛️ Configuration

### Adjust Log Levels

Edit `backend/src/main/resources/logback-spring.xml`:

```xml
<!-- Make RoomManager even more verbose -->
<logger name="com.tradelearn.server.service.RoomManager" level="DEBUG" />

<!-- Quiet down Spring -->
<logger name="org.springframework" level="WARN"/>

<!-- Show SQL queries for DB debugging -->
<logger name="org.hibernate.SQL" level="DEBUG"/>
```

### Change Log Retention

```xml
<rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
    <maxHistory>90</maxHistory>  <!-- Keep for 90 days -->
    <totalSizeCap>5GB</totalSizeCap>  <!-- Max total size -->
</rollingPolicy>
```

## 💡 Pro Tips

1. **Use grep with context**
   ```bash
   grep -A 5 -B 5 "GAME_CANNOT_START" logs/errors.log
   ```

2. **Follow logs in real-time**
   ```bash
   tail -f logs/tradelearn.log | grep --line-buffered "gameId=123"
   ```

3. **Search by user**
   ```bash
   grep "userId=456" logs/game-events.log
   ```

4. **Track a single game from creation to finish**
   ```bash
   grep "gameId=123" logs/game-events.log
   ```

5. **Find all errors in the last hour**
   ```bash
   grep "$(date -d '1 hour ago' '+%Y-%m-%d %H')" logs/errors.log
   ```

6. **Export game session for analysis**
   ```bash
   grep "gameId=123" logs/game-events.log > game-123-debug.log
   ```

## 🎓 Understanding the Flow

A typical successful game flow looks like this in the logs:

```
1. 🏠 ROOM_CREATED - Creator creates game
   └─ gameId=123, creatorId=1, roomSize=1, phase=WAITING

2. 👤 PLAYER_JOINED - Opponent joins
   └─ gameId=123, userId=2, roomSize=2, phase=STARTING

3. 🔄 GAME_STATE_TRANSITION - Room transitions
   └─ gameId=123, oldState=STARTING, newState=ACTIVE

4. ⏰ INTERVAL_CREATED - Scheduler starts
   └─ gameId=123, intervalSeconds=5

5. ⏳ INTERVAL_TICK (repeats every 5s)
   └─ gameId=123, currentCandle=0, remaining=239

6. 💰 TRADE_PLACED (as players trade)
   └─ gameId=123, userId=1, type=BUY, quantity=100

7. 🏁 GAME_FINISHED
   └─ gameId=123, winnerId=1

8. ⏹️ INTERVAL_DELETED
   └─ gameId=123, reason=game_finished

9. 🗑️ ROOM_REMOVED
   └─ gameId=123, reason=cleanup_after_end
```

If any step is missing or shows an error, that's where your issue is!

## 📞 Need Help?

If you can't figure out the issue:

1. Export the relevant logs: `grep "gameId=YOUR_GAME_ID" logs/game-events.log > debug.log`
2. Check `logs/errors.log` for exceptions
3. Look for any ❌ "GAME_CANNOT_START" messages
4. Review the diagnostic snapshots (📊) for clues

Happy debugging! 🐛🔨
