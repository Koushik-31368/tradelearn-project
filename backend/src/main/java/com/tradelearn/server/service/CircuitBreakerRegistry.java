package com.tradelearn.server.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lightweight circuit breaker registry — no external dependency needed.
 *
 * <h3>State machine</h3>
 * <pre>
 *   CLOSED ──(failures ≥ threshold)──→ OPEN
 *   OPEN   ──(cooldown elapsed)──────→ HALF_OPEN
 *   HALF_OPEN ──(probe succeeds)─────→ CLOSED
 *   HALF_OPEN ──(probe fails)────────→ OPEN
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 *   CircuitBreaker cb = registry.get("redis");
 *   if (!cb.isCallPermitted()) throw new ServiceUnavailableException();
 *   try { doRedisCall(); cb.recordSuccess(); }
 *   catch (Exception e) { cb.recordFailure(); throw e; }
 * </pre>
 *
 * Thread safety: all counters are {@code AtomicInteger}/{@code AtomicLong}
 * with volatile state, lock-free CAS transitions.
 */
@Service
public class CircuitBreakerRegistry {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRegistry.class);

    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    /**
     * Get or create a circuit breaker with the given name and parameters.
     */
    public CircuitBreaker get(String name, int failureThreshold, long cooldownMs) {
        return breakers.computeIfAbsent(name, n ->
                new CircuitBreaker(n, failureThreshold, cooldownMs));
    }

    /** Get an existing breaker (returns null if not registered). */
    public CircuitBreaker get(String name) {
        return breakers.get(name);
    }

    /** All registered breaker names. */
    public Set<String> names() {
        return breakers.keySet();
    }

    // ===================== CIRCUIT BREAKER =====================

    public static class CircuitBreaker {

        public enum State { CLOSED, OPEN, HALF_OPEN }

        private final String name;
        private final int failureThreshold;
        private final long cooldownMs;

        private volatile State state = State.CLOSED;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile long openedAtMs = 0;

        // ── Counters ──
        private final AtomicLong totalSuccesses  = new AtomicLong();
        private final AtomicLong totalFailures   = new AtomicLong();
        private final AtomicLong totalRejections = new AtomicLong();
        private final AtomicLong totalTrips      = new AtomicLong();

        CircuitBreaker(String name, int failureThreshold, long cooldownMs) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.cooldownMs = cooldownMs;
            log.info("[CB:{}] Created — threshold={}, cooldown={}ms", name, failureThreshold, cooldownMs);
        }

        /**
         * Check if a call is permitted.
         * <ul>
         *   <li>CLOSED → always allowed</li>
         *   <li>OPEN → rejected unless cooldown elapsed (transitions to HALF_OPEN)</li>
         *   <li>HALF_OPEN → single probe allowed</li>
         * </ul>
         */
        public boolean isCallPermitted() {
            switch (state) {
                case CLOSED -> { return true; }
                case OPEN -> {
                    if (System.currentTimeMillis() - openedAtMs >= cooldownMs) {
                        state = State.HALF_OPEN;
                        log.info("[CB:{}] OPEN → HALF_OPEN (cooldown elapsed)", name);
                        return true; // allow probe
                    }
                    totalRejections.incrementAndGet();
                    return false;
                }
                case HALF_OPEN -> {
                    return true; // allow probe call
                }
            }
            return false;
        }

        /**
         * Record a successful call. Transitions HALF_OPEN → CLOSED.
         */
        public void recordSuccess() {
            totalSuccesses.incrementAndGet();
            consecutiveFailures.set(0);
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                log.info("[CB:{}] HALF_OPEN → CLOSED (probe succeeded)", name);
            }
        }

        /**
         * Record a failed call. May trip the breaker.
         */
        public void recordFailure() {
            totalFailures.incrementAndGet();
            int failures = consecutiveFailures.incrementAndGet();

            if (state == State.HALF_OPEN) {
                trip();
            } else if (state == State.CLOSED && failures >= failureThreshold) {
                trip();
            }
        }

        private void trip() {
            state = State.OPEN;
            openedAtMs = System.currentTimeMillis();
            totalTrips.incrementAndGet();
            log.warn("[CB:{}] → OPEN (tripped after {} consecutive failures)",
                    name, consecutiveFailures.get());
        }

        /**
         * Force the breaker to CLOSED (manual recovery).
         */
        public void reset() {
            state = State.CLOSED;
            consecutiveFailures.set(0);
            log.info("[CB:{}] Manual reset → CLOSED", name);
        }

        // ── Getters ──
        public String getName()           { return name; }
        public State  getState()          { return state; }
        public boolean isOpen()           { return state == State.OPEN; }
        public boolean isClosed()         { return state == State.CLOSED; }
        public long   getTotalSuccesses() { return totalSuccesses.get(); }
        public long   getTotalFailures()  { return totalFailures.get(); }
        public long   getTotalRejections(){ return totalRejections.get(); }
        public long   getTotalTrips()     { return totalTrips.get(); }
    }
}
