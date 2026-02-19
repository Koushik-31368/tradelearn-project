package com.tradelearn.server.controller;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.config.WebSocketEventListener;
import com.tradelearn.server.service.MatchSchedulerService;
import com.tradelearn.server.service.RoomManager;

/**
 * Production health and diagnostics endpoint.
 *
 * Supplements Spring Actuator's /actuator/health with
 * game-specific operational metrics:
 *   - Active rooms, sessions, scheduler count
 *   - JVM uptime and memory usage
 *   - Instance identity (for multi-server debugging)
 *
 * Endpoint: GET /api/health
 *
 * This is intentionally lightweight — no DB queries,
 * no Redis calls. Safe to poll at high frequency from
 * load balancers and monitoring dashboards.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Instant BOOT_TIME = Instant.now();

    private final RoomManager roomManager;
    private final MatchSchedulerService matchSchedulerService;
    private final WebSocketEventListener wsEventListener;

    @Value("${spring.application.name:tradelearn}")
    private String appName;

    @Value("${INSTANCE_ID:single}")
    private String instanceId;

    public HealthController(RoomManager roomManager,
                            MatchSchedulerService matchSchedulerService,
                            WebSocketEventListener wsEventListener) {
        this.roomManager = roomManager;
        this.matchSchedulerService = matchSchedulerService;
        this.wsEventListener = wsEventListener;
    }

    /**
     * Lightweight health check with game-specific metrics.
     * No database or Redis calls — safe for frequent polling.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();

        // Status
        response.put("status", "UP");
        response.put("application", appName);
        response.put("instance", instanceId);
        response.put("timestamp", Instant.now().toString());

        // Uptime
        Duration uptime = Duration.between(BOOT_TIME, Instant.now());
        response.put("uptime", formatDuration(uptime));
        response.put("uptimeSeconds", uptime.getSeconds());

        // Game metrics (all in-memory, zero-cost)
        Map<String, Object> game = new LinkedHashMap<>();
        game.put("activeRooms", roomManager.activeRoomCount());
        game.put("activeSchedulers", matchSchedulerService.activeGameCount());
        game.put("connectedSessions", wsEventListener.getActiveSessionCount());
        response.put("game", game);

        // JVM metrics
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("maxMemoryMB", rt.maxMemory() / (1024 * 1024));
        jvm.put("totalMemoryMB", rt.totalMemory() / (1024 * 1024));
        jvm.put("freeMemoryMB", rt.freeMemory() / (1024 * 1024));
        jvm.put("usedMemoryMB", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        jvm.put("availableProcessors", rt.availableProcessors());
        jvm.put("jvmUptime", ManagementFactory.getRuntimeMXBean().getUptime());
        response.put("jvm", jvm);

        return ResponseEntity.ok(response);
    }

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
}
