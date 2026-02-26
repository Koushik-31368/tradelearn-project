package com.tradelearn.server.service;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Database health monitor with circuit breaker protection.
 *
 * <h3>What it does</h3>
 * <ul>
 *   <li>Probes the database connection every 5 seconds via a lightweight
 *       {@code SELECT 1} query.</li>
 *   <li>Integrates with {@link CircuitBreakerRegistry} ("database" breaker)
 *       to track consecutive failures.</li>
 *   <li>Exposes {@link #isDatabaseHealthy()} for other services to check
 *       before attempting writes.</li>
 *   <li>Reports state transitions to the {@link GracefulDegradationManager}
 *       when present (optional dependency to avoid circular init).</li>
 * </ul>
 *
 * <h3>Recovery</h3>
 * When the breaker transitions HALF_OPEN → CLOSED (probe succeeds),
 * a "database-recovered" event is emitted, triggering reconciliation.
 *
 * <h3>Thread safety</h3>
 * {@code AtomicBoolean} for the health flag; circuit breaker is lock-free.
 * The {@code @Scheduled} method runs on a single thread (no overlap).
 */
@Service
public class DatabaseFailoverHandler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFailoverHandler.class);

    private static final String CB_NAME = "database";
    private static final int CB_FAILURE_THRESHOLD = 3;
    private static final long CB_COOLDOWN_MS = 20_000;
    private static final int PROBE_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;
    private final CircuitBreakerRegistry cbRegistry;

    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private volatile long lastSuccessfulProbeMs = System.currentTimeMillis();
    private volatile long lastFailureMs = 0;
    private volatile String lastErrorMessage = null;

    /**
     * Optional reference to GracefulDegradationManager — set after construction
     * to break circular dependency.
     */
    private volatile GracefulDegradationManager degradationManager;

    public DatabaseFailoverHandler(DataSource dataSource,
                                   CircuitBreakerRegistry cbRegistry) {
        this.dataSource = dataSource;
        this.cbRegistry = cbRegistry;
    }

    @PostConstruct
    void init() {
        cbRegistry.get(CB_NAME, CB_FAILURE_THRESHOLD, CB_COOLDOWN_MS);
        log.info("[DBFailover] Initialized — threshold={}, cooldown={}ms, probeTimeout={}s",
                CB_FAILURE_THRESHOLD, CB_COOLDOWN_MS, PROBE_TIMEOUT_SECONDS);
    }

    /** Called by GracefulDegradationManager after its own construction. */
    public void setDegradationManager(GracefulDegradationManager manager) {
        this.degradationManager = manager;
    }

    private CircuitBreakerRegistry.CircuitBreaker cb() {
        return cbRegistry.get(CB_NAME);
    }

    // ==================== PROBE ====================

    /**
     * Probe the database every 5 seconds. Runs on the Spring Scheduling
     * thread pool (enabled by @EnableScheduling in AsyncConfig).
     */
    @Scheduled(fixedDelayString = "${tradelearn.db.probe-interval-ms:5000}")
    public void probe() {
        CircuitBreakerRegistry.CircuitBreaker breaker = cb();
        if (breaker == null) return;

        boolean wasHealthy = healthy.get();

        try (Connection conn = dataSource.getConnection()) {
            conn.setNetworkTimeout(Runnable::run, PROBE_TIMEOUT_SECONDS * 1000);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(PROBE_TIMEOUT_SECONDS);
                stmt.execute("SELECT 1");
            }

            breaker.recordSuccess();
            lastSuccessfulProbeMs = System.currentTimeMillis();

            if (!wasHealthy) {
                healthy.set(true);
                lastErrorMessage = null;
                log.info("[DBFailover] Database recovered — back to healthy");
                notifyRecovery();
            }

        } catch (Exception e) {
            breaker.recordFailure();
            lastFailureMs = System.currentTimeMillis();
            lastErrorMessage = e.getMessage();

            if (wasHealthy && breaker.isOpen()) {
                healthy.set(false);
                log.error("[DBFailover] Database unreachable — circuit breaker OPEN: {}", e.getMessage());
                notifyDegradation();
            } else {
                log.warn("[DBFailover] Database probe failed: {}", e.getMessage());
            }
        }
    }

    // ==================== NOTIFICATIONS ====================

    private void notifyDegradation() {
        GracefulDegradationManager mgr = this.degradationManager;
        if (mgr != null) {
            mgr.onDatabaseUnavailable();
        }
    }

    private void notifyRecovery() {
        GracefulDegradationManager mgr = this.degradationManager;
        if (mgr != null) {
            mgr.onDatabaseRecovered();
        }
    }

    // ==================== PUBLIC API ====================

    /** True if the last probe succeeded and the circuit breaker is not OPEN. */
    public boolean isDatabaseHealthy() {
        return healthy.get();
    }

    /** True if the circuit breaker is OPEN (database is down). */
    public boolean isDatabaseDown() {
        return !healthy.get() && cb().isOpen();
    }

    /** Milliseconds since the last successful database probe. */
    public long msSinceLastSuccess() {
        return System.currentTimeMillis() - lastSuccessfulProbeMs;
    }

    /** Diagnostic snapshot for actuator/monitoring. */
    public Map<String, Object> diagnostics() {
        CircuitBreakerRegistry.CircuitBreaker breaker = cb();
        return Map.of(
                "healthy", healthy.get(),
                "circuitState", breaker != null ? breaker.getState().name() : "UNKNOWN",
                "lastSuccessMs", lastSuccessfulProbeMs,
                "lastFailureMs", lastFailureMs,
                "lastError", lastErrorMessage != null ? lastErrorMessage : "none",
                "totalFailures", breaker != null ? breaker.getTotalFailures() : 0,
                "totalTrips", breaker != null ? breaker.getTotalTrips() : 0
        );
    }
}
