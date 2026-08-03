package com.tradelearn.server.market.controller;

import com.tradelearn.server.market.replay.TickReplayEngine;
import com.tradelearn.server.market.service.ReplaySessionService;
import com.tradelearn.server.auth.security.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for solo simulator sessions.
 *
 * <p>These endpoints let the frontend start and stop a server-side
 * candle replay stream for the solo simulator ({@code /simulator} route).
 * The actual price ticks are delivered over WebSocket at:
 * {@code /topic/simulator/{sessionId}/tick}
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST   /api/simulator/session}           — start a replay session</li>
 *   <li>{@code DELETE /api/simulator/session/{id}}      — stop a replay session</li>
 *   <li>{@code GET    /api/simulator/session/{id}/price}— get current tick price (for trade settlement)</li>
 *   <li>{@code GET    /api/simulator/symbols}           — list available symbols with date ranges</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorTickController {

    private final TickReplayEngine replayEngine;
    private final ReplaySessionService sessionService;
    private final JwtUtil jwtUtil;

    public SimulatorTickController(TickReplayEngine replayEngine,
                                   ReplaySessionService sessionService,
                                   JwtUtil jwtUtil) {
        this.replayEngine = replayEngine;
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Starts a new simulator replay session.
     *
     * <p>Request body (JSON):
     * <pre>
     * {
     *   "ticker":        "RELIANCE",   // bare NSE ticker
     *   "candleCount":   60,           // number of candles to replay (default: 60)
     *   "tickIntervalMs": 5000         // ms between ticks (default: 5000, min: 500)
     * }
     * </pre>
     *
     * <p>Response (JSON):
     * <pre>
     * {
     *   "sessionId": "sim-42-1722680012345",
     *   "ticker":    "RELIANCE",
     *   "wsTopics":  "/topic/simulator/sim-42-1722680012345/tick"
     * }
     * </pre>
     */
    @PostMapping("/session")
    public ResponseEntity<?> startSession(
            @RequestBody StartSessionRequest req,
            HttpServletRequest httpRequest) {

        long userId = extractUserId(httpRequest);

        String ticker    = req.ticker() != null ? req.ticker() : "RELIANCE";
        int candleCount  = req.candleCount() > 0 ? req.candleCount() : 60;
        long intervalMs  = req.tickIntervalMs() > 0 ? req.tickIntervalMs() : 5000L;

        try {
            String sessionId = replayEngine.startSession(userId, ticker, candleCount, intervalMs);
            return ResponseEntity.ok(Map.of(
                    "sessionId", sessionId,
                    "ticker", ticker,
                    "candleCount", candleCount,
                    "tickIntervalMs", intervalMs,
                    "wsTopic", "/topic/simulator/" + sessionId + "/tick"
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Stops a simulator session and releases its scheduler.
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> stopSession(@PathVariable String sessionId) {
        if (!replayEngine.sessionExists(sessionId)) {
            return ResponseEntity.notFound().build();
        }
        replayEngine.stopSession(sessionId);
        return ResponseEntity.ok(Map.of("stopped", sessionId));
    }

    /**
     * Returns the last broadcast close price for a session.
     * The simulator frontend uses this to settle paper trades.
     */
    @GetMapping("/session/{sessionId}/price")
    public ResponseEntity<?> getCurrentPrice(@PathVariable String sessionId) {
        if (!replayEngine.sessionExists(sessionId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            double price = replayEngine.getCurrentPrice(sessionId);
            return ResponseEntity.ok(Map.of("sessionId", sessionId, "price", price));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Returns all available NSE symbols with their candle date ranges.
     * Used to populate the symbol selector in the simulator UI.
     */
    @GetMapping("/symbols")
    public ResponseEntity<?> listSymbols() {
        return ResponseEntity.ok(sessionService.listAvailableSymbols());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private long extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Long userId = jwtUtil.getUserId(token);
                return userId != null ? userId : -1L;
            } catch (Exception ignored) {}
        }
        return -1L; // anonymous (shouldn't reach here — route is secured)
    }

    /** Request body DTO for {@code POST /api/simulator/session}. */
    public record StartSessionRequest(
            String ticker,
            int candleCount,
            long tickIntervalMs
    ) {}
}
