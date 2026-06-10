package com.tradelearn.server.controller;

/**
 * @deprecated Replaced by {@link com.tradelearn.server.socket.GameWebSocketHandler}.
 *
 * The new handler uses GameBroadcaster for cross-instance WebSocket
 * delivery via Redis Pub/Sub, enabling horizontal scaling.
 *
 * This class is retained only as a migration reference.
 * All @MessageMapping endpoints now live in socket/GameWebSocketHandler.
 *
 * @see com.tradelearn.server.socket.GameWebSocketHandler
 * @see com.tradelearn.server.socket.GameBroadcaster
 */
@Deprecated(since = "2.0", forRemoval = true)
public class GameWebSocketController {
    // All functionality moved to com.tradelearn.server.socket.GameWebSocketHandler
}