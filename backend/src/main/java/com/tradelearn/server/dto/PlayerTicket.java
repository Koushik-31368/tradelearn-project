package com.tradelearn.server.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A player waiting in the ranked matchmaking queue.
 *
 * Implements {@link Comparable} for placement in a {@link java.util.concurrent.ConcurrentSkipListSet}.
 * Ordering: rating ASC → joinTime ASC → userId ASC (total ordering guarantee).
 *
 * Immutable once created — safe for concurrent SkipList access.
 *
 * ELO window expansion:
 *   0–20 s  → ±100 rating
 *   20–40 s → ±200 rating
 *   40+ s   → any opponent
 *   120+ s  → auto-expired (cleanup removes)
 */
public final class PlayerTicket implements Comparable<PlayerTicket> {

    /** Maximum time a ticket can stay in the queue before auto-removal. */
    public static final Duration MAX_QUEUE_TIME = Duration.ofMinutes(2);

    private final long userId;
    private final String username;
    private final int rating;
    private final Instant joinTime;

    public PlayerTicket(long userId, String username, int rating) {
        this.userId = userId;
        this.username = username;
        this.rating = rating;
        this.joinTime = Instant.now();
    }

    // ── Accessors ──

    public long getUserId()      { return userId; }
    public String getUsername()   { return username; }
    public int getRating()       { return rating; }
    public Instant getJoinTime() { return joinTime; }

    // ── ELO window logic ──

    /**
     * Returns the maximum ELO difference this ticket currently accepts,
     * based on how long the player has been waiting.
     */
    public int getAllowedRatingGap() {
        long waitSeconds = Duration.between(joinTime, Instant.now()).getSeconds();
        if (waitSeconds < 20) return 100;
        if (waitSeconds < 40) return 200;
        return Integer.MAX_VALUE;
    }

    /**
     * Whether this ticket has exceeded the maximum queue time.
     */
    public boolean isExpired() {
        return Duration.between(joinTime, Instant.now()).compareTo(MAX_QUEUE_TIME) > 0;
    }

    // ── ConcurrentSkipListSet contract ──

    /**
     * Total-order comparator for SkipList placement.
     * 1. Rating ascending — so {@code lower()} / {@code higher()} return
     *    the nearest-rated neighbors.
     * 2. Join time ascending — tie-breaker so earlier arrivals are first.
     * 3. UserId ascending — guarantees uniqueness (SkipList treats compareTo==0 as equal).
     */
    @Override
    public int compareTo(PlayerTicket other) {
        int cmp = Integer.compare(this.rating, other.rating);
        if (cmp != 0) return cmp;
        cmp = this.joinTime.compareTo(other.joinTime);
        if (cmp != 0) return cmp;
        return Long.compare(this.userId, other.userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerTicket that)) return false;
        return userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "PlayerTicket{userId=" + userId +
               ", rating=" + rating +
               ", waitSec=" + Duration.between(joinTime, Instant.now()).getSeconds() +
               ", window=±" + getAllowedRatingGap() +
               "}";
    }
}
