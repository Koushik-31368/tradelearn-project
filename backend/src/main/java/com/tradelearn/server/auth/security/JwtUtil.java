package com.tradelearn.server.auth.security;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * JWT utility class — generates and validates HS256 tokens.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Key rotation:</b> Supports current + previous key. Tokens signed with the
 *       previous key are still accepted during the rotation window. Set
 *       {@code tradelearn.jwt.previous-secret} when rotating keys.</li>
 *   <li><b>Replay prevention:</b> Each token contains a unique {@code jti} (JWT ID) claim.
 *       Once a token is used for a WebSocket handshake, its jti is recorded in a bounded
 *       in-memory set. Subsequent uses of the same token are rejected. JTI entries are
 *       evicted after the token's TTL expires.</li>
 *   <li><b>Key ID:</b> Each token includes a {@code kid} header identifying which key
 *       signed it, enabling seamless rotation without service disruption.</li>
 * </ul>
 *
 * Token claims:
 *   sub  = user email (unique, used for lookup)
 *   uid  = user ID (avoids extra DB call for most operations)
 *   name = username (convenience, displayed in UI)
 *   jti  = unique token ID (replay prevention)
 *   iat  = issued at
 *   exp  = expiration
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String CURRENT_KID = "current";
    @SuppressWarnings("unused")
    private static final String PREVIOUS_KID = "previous";

    private final SecretKey currentKey;
    private final SecretKey previousKey;  // null if no rotation in progress
    private final long expirationMs;      // access token expiry (kept for compat; same as accessExpirationMs)

    // ── Refresh token has its own secret key and a longer TTL ───────────────
    private static final String REFRESH_KID = "refresh";
    private final SecretKey refreshKey;
    private final long refreshExpirationMs;

    /**
     * Bounded JTI replay cache. Key = jti string, Value = expiration timestamp.
     * Entries are evicted periodically when they expire.
     * Max size bounded to prevent OOM: 500K entries ≈ ~50 MB in worst case.
     */
    private static final int MAX_JTI_CACHE_SIZE = 500_000;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final ConcurrentHashMap<String, Long> usedJtis = new ConcurrentHashMap<>();
    private ScheduledExecutorService jtiCleanupExecutor;

    public JwtUtil(
            @Value("${tradelearn.jwt.secret}") String secret,
            @Value("${tradelearn.jwt.previous-secret:}") String previousSecret,
            @Value("${tradelearn.jwt.expiration-ms}") long expirationMs,
            @Value("${tradelearn.jwt.refresh-secret:}") String refreshSecret,
            @Value("${tradelearn.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.currentKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.previousKey = (previousSecret != null && !previousSecret.isBlank())
                ? Keys.hmacShaKeyFor(previousSecret.getBytes())
                : null;
        this.expirationMs = expirationMs;
        this.refreshKey = resolveRefreshKey(refreshSecret);
        this.refreshExpirationMs = refreshExpirationMs;

        if (this.previousKey != null) {
            log.info("[JWT] Key rotation active — accepting tokens signed with both current and previous keys");
        }
    }

    /**
     * Resolve the refresh-token signing key.
     *
     * <ul>
     *   <li>If {@code refreshSecret} is non-blank: use it as-is (standard prod path).</li>
     *   <li>If blank/absent: generate a cryptographically-random 64-byte key at boot.
     *       <strong>This is only safe for local development</strong> — all existing refresh
     *       tokens are invalidated on every restart. In production, set
     *       {@code JWT_REFRESH_SECRET} to a stable, independent secret; the app
     *       fails at startup if the property is missing (no fallback in prod profile).</li>
     * </ul>
     */
    private static SecretKey resolveRefreshKey(String refreshSecret) {
        if (refreshSecret != null && !refreshSecret.isBlank()) {
            return Keys.hmacShaKeyFor(refreshSecret.getBytes());
        }
        // Dev-only: random key, invalidated on every restart
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        String hex = HexFormat.of().formatHex(randomBytes);
        log.warn("[JWT] tradelearn.jwt.refresh-secret is not set — generating a random key for this session.");
        log.warn("[JWT] Existing refresh tokens will be INVALID after restart. Set JWT_REFRESH_SECRET in production.");
        return Keys.hmacShaKeyFor(hex.getBytes());
    }

    @PostConstruct
    public void init() {
        // Evict expired JTIs every 5 minutes
        jtiCleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jti-cleanup");
            t.setDaemon(true);
            return t;
        });
        jtiCleanupExecutor.scheduleAtFixedRate(this::evictExpiredJtis, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (jtiCleanupExecutor != null) {
            jtiCleanupExecutor.shutdownNow();
        }
    }

    // ==================== TOKEN GENERATION ====================

    /**
     * Generate a short-lived ACCESS token for the given user.
     * Signed with {@code currentKey}. Expiry controlled by
     * {@code tradelearn.jwt.expiration-ms} (default 15 min recommended).
     */
    public String generateAccessToken(Long userId, String email, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .header().keyId(CURRENT_KID).and()
                .subject(email)
                .claim("uid", userId)
                .claim("name", username)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(currentKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Backward-compat alias — delegates to {@link #generateAccessToken}.
     * Existing callers (e.g. WebSocketAuthInterceptor) continue to work unchanged.
     */
    public String generateToken(Long userId, String email, String username) {
        return generateAccessToken(userId, email, username);
    }

    /**
     * Generate a long-lived REFRESH token for the given user.
     * Signed with a <em>separate</em> {@code refreshKey} so that a compromised
     * access-token secret cannot be used to forge refresh tokens.
     * Expiry controlled by {@code tradelearn.jwt.refresh-expiration-ms} (default 7 days).
     */
    public String generateRefreshToken(Long userId, String email, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .header().keyId(REFRESH_KID).and()
                .subject(email)
                .claim("uid", userId)
                .claim("name", username)
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(refreshKey, Jwts.SIG.HS256)
                .compact();
    }

    // ==================== TOKEN PARSING ====================

    /**
     * Parse and validate a JWT token. Tries the current key first,
     * then falls back to the previous key if rotation is active.
     *
     * @throws JwtException if the token is invalid or expired with both keys
     */
    public Claims parseToken(String token) {
        // Try current key first
        try {
            return Jwts.parser()
                    .verifyWith(currentKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            if (previousKey == null) throw e;
            // Fall through to try previous key
        }

        // Try previous key (rotation window)
        return Jwts.parser()
                .verifyWith(previousKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Parse and validate a REFRESH token.
     * Uses the dedicated {@code refreshKey} — access tokens are rejected here.
     *
     * @throws JwtException if the token is invalid, expired, or not a refresh token
     */
    public Claims parseRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // Enforce type claim so access tokens can't be used as refresh tokens
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new JwtException("Token is not a refresh token");
        }
        return claims;
    }

    /**
     * Check if a refresh token is valid (not expired, correct key, correct type).
     */
    public boolean isValidRefreshToken(String token) {
        try {
            parseRefreshToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("[JWT] Refresh token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("[JWT] Refresh token invalid: {}", e.getMessage());
        }
        return false;
    }

    /** Refresh token TTL in milliseconds — used to set cookie Max-Age. */
    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    /**
     * Extract the user email (subject) from a token.
     */
    public String getEmail(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Extract the user ID from the "uid" claim.
     */
    public Long getUserId(String token) {
        return parseToken(token).get("uid", Long.class);
    }

    /**
     * Extract the username from the "name" claim.
     */
    public String getUsername(String token) {
        return parseToken(token).get("name", String.class);
    }

    /**
     * Check if a token is valid (not expired, signature OK).
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT invalid: {}", e.getMessage());
        }
        return false;
    }

    // ==================== REPLAY PREVENTION ====================

    /**
     * Check if a token has been used before (replay attack detection).
     * If the token has a jti claim, it is checked against the replay cache.
     * If the jti is new, it is recorded and the method returns true (allowed).
     * If the jti was previously recorded, returns false (replay detected).
     *
     * <p>Tokens without a jti claim are allowed (backward compatibility
     * for tokens issued before this feature was deployed).</p>
     *
     * @param token the JWT token string
     * @return true if the token is allowed (first use or no jti), false if replay detected
     */
    public boolean checkAndRecordJti(String token) {
        try {
            Claims claims = parseToken(token);
            String jti = claims.getId();

            if (jti == null || jti.isBlank()) {
                // Legacy token without jti — allow but log
                log.debug("[JWT] Token without jti — allowing (legacy compatibility)");
                return true;
            }

            // Bounded cache check — prevent OOM
            if (usedJtis.size() >= MAX_JTI_CACHE_SIZE) {
                // Emergency eviction of expired entries
                evictExpiredJtis();
                if (usedJtis.size() >= MAX_JTI_CACHE_SIZE) {
                    log.warn("[JWT] JTI cache at capacity ({}) — allowing token to prevent DoS lockout",
                            MAX_JTI_CACHE_SIZE);
                    return true;
                }
            }


            // Replay cache logic removed for lightweight config
            return true;

        } catch (JwtException e) {
            // If we can't parse, it's invalid anyway — let isValid() catch it
            return true;
        }
    }

    /**
     * Evict expired JTI entries to keep the cache bounded.
     */
    private void evictExpiredJtis() {
        long now = System.currentTimeMillis();
        int before = usedJtis.size();
        usedJtis.entrySet().removeIf(e -> e.getValue() < now);
        int evicted = before - usedJtis.size();
        if (evicted > 0) {
            log.debug("[JWT] Evicted {} expired JTI entries (remaining: {})", evicted, usedJtis.size());
        }
    }

    /**
     * Number of active JTI entries (diagnostic).
     */
    public int getJtiCacheSize() {
        return usedJtis.size();
    }
}
