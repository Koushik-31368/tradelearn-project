package com.tradelearn.server.service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Circuit breaker that monitors JVM heap usage and activates
 * pressure mode when consumption exceeds a configurable threshold.
 *
 * <h3>Hysteresis (prevents rapid on/off flapping)</h3>
 * <ul>
 *   <li>PRESSURE ON  at 85% used heap (configurable)</li>
 *   <li>PRESSURE OFF at 75% used heap (configurable)</li>
 * </ul>
 *
 * <h3>When pressure is active:</h3>
 * <ul>
 *   <li>{@link TradeProcessingPipeline} rejects new trades</li>
 *   <li>{@link PositionSnapshotStore} refuses new snapshot entries</li>
 *   <li>Metrics expose the state for Prometheus/Grafana alerting</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * Uses {@link AtomicBoolean} with {@code compareAndSet} for lock-free
 * state transitions. The {@code @Scheduled} method runs on Spring's
 * scheduling thread; the {@code isUnderPressure()} check is a single
 * volatile read on the hot (trade) path — zero overhead.
 */
@Service
public class HeapPressureGuard {

    private static final Logger log = LoggerFactory.getLogger(HeapPressureGuard.class);

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final AtomicBoolean underPressure = new AtomicBoolean(false);

    private final double pressureThreshold;
    private final double recoveryThreshold;

    // ── Observable stats (volatile for cross-thread visibility) ──
    private volatile double lastHeapRatio;
    private volatile long lastUsedBytes;
    private volatile long lastMaxBytes;
    private volatile long pressureActivations;

    public HeapPressureGuard(
            @Value("${tradelearn.heap.pressure-threshold:0.85}") double pressureThreshold,
            @Value("${tradelearn.heap.recovery-threshold:0.75}") double recoveryThreshold) {
        this.pressureThreshold = pressureThreshold;
        this.recoveryThreshold = recoveryThreshold;
        log.info("[HeapGuard] Initialized — pressure={}%, recovery={}%",
                String.format("%.0f", pressureThreshold * 100),
                String.format("%.0f", recoveryThreshold * 100));
    }

    // ==================== PERIODIC CHECK ====================

    /**
     * Periodic heap check. Uses {@link MemoryMXBean} for accurate
     * committed/used/max values (avoids the imprecise
     * {@code Runtime.freeMemory()} estimation).
     */
    @Scheduled(fixedRateString = "${tradelearn.heap.check-interval-ms:2000}")
    void checkHeap() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max  = heap.getMax();

        if (max <= 0) return; // Unknown max (shouldn't happen with -Xmx set)

        double ratio = (double) used / max;
        lastHeapRatio = ratio;
        lastUsedBytes = used;
        lastMaxBytes  = max;

        if (ratio >= pressureThreshold && underPressure.compareAndSet(false, true)) {
            pressureActivations++;
            log.warn("[HeapGuard] PRESSURE ON — heap {}/{} MB ({}%)",
                    used / 1_048_576, max / 1_048_576,
                    String.format("%.1f", ratio * 100));
        } else if (ratio <= recoveryThreshold && underPressure.compareAndSet(true, false)) {
            log.info("[HeapGuard] PRESSURE OFF — heap {}/{} MB ({}%)",
                    used / 1_048_576, max / 1_048_576,
                    String.format("%.1f", ratio * 100));
        }
    }

    // ==================== API ====================

    /**
     * Check if the system is under heap pressure.
     * Single volatile read — safe to call on every trade (zero-cost hot path).
     */
    public boolean isUnderPressure() {
        return underPressure.get();
    }

    public double getHeapRatio()          { return lastHeapRatio; }
    public long   getUsedBytes()          { return lastUsedBytes; }
    public long   getMaxBytes()           { return lastMaxBytes; }
    public long   getPressureActivations(){ return pressureActivations; }
}
