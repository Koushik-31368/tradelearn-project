package com.tradelearn.server.auth.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.auth.security.JwtUtil;

/**
 * Authentication controller — login, register, token refresh, logout.
 *
 * <h3>Token split strategy</h3>
 * <ul>
 *   <li><b>Access token</b> (short-lived, {@code tradelearn.jwt.expiration-ms}):
 *       returned in the JSON response body. The client stores it in React
 *       in-memory state (never localStorage). Sent as {@code Authorization: Bearer}
 *       on every API call.</li>
 *   <li><b>Refresh token</b> (long-lived, {@code tradelearn.jwt.refresh-expiration-ms}):
 *       set as an {@code httpOnly; Secure; SameSite=None} cookie scoped to
 *       {@code /api/auth}. Never readable by JS. The browser sends it
 *       automatically only to {@code /api/auth/refresh} and {@code /api/auth/logout}.</li>
 * </ul>
 *
 * <h3>CSRF safety</h3>
 * All state-changing endpoints authenticate via the short-lived access token
 * in the {@code Authorization} header — NOT via the cookie. The cookie is
 * path-scoped so it is never sent to arbitrary {@code /api/} endpoints,
 * eliminating any CSRF attack surface from the cookie.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Name of the httpOnly refresh-token cookie. */
    public static final String REFRESH_COOKIE_NAME = "rt";

    /** Cookie path — browser sends cookie only to endpoints under this path. */
    private static final String COOKIE_PATH = "/api/auth";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Whether to mark the cookie Secure.
     * Set COOKIE_SECURE=true in production (HTTPS). False for http local dev.
     */
    @Value("${tradelearn.cookie.secure:false}")
    private boolean cookieSecure;

    /**
     * SameSite attribute for the refresh cookie.
     * Use "None" in production (cross-domain Vercel → Render).
     * Use "Lax" for local same-origin dev.
     */
    @Value("${tradelearn.cookie.same-site:Lax}")
    private String cookieSameSite;

    public AuthController(UserRepository userRepository,
                          BCryptPasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Build the Set-Cookie header value for a new refresh token. */
    private ResponseCookie buildRefreshCookie(String refreshToken) {
        long maxAgeSeconds = jwtUtil.getRefreshExpirationMs() / 1000;
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** Build a cookie that immediately expires the refresh token (logout). */
    private ResponseCookie buildClearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    /** Standard user-info map returned in auth responses (no token — that's separate). */
    private Map<String, Object> userInfo(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("email", user.getEmail());
        m.put("rating", user.getRating());
        m.put("xp", user.getXp());
        m.put("loginStreak", user.getLoginStreak());
        return m;
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            if (user.getEmail() == null ||
                user.getUsername() == null ||
                user.getPassword() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "All fields are required"));
            }

            if (userRepository.existsByEmail(user.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email already exists"));
            }

            if (userRepository.existsByUsername(user.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username already taken"));
            }

            user.setPassword(passwordEncoder.encode(user.getPassword()));
            User saved = userRepository.save(user);

            String accessToken  = jwtUtil.generateAccessToken(saved.getId(), saved.getEmail(), saved.getUsername());
            String refreshToken = jwtUtil.generateRefreshToken(saved.getId(), saved.getEmail(), saved.getUsername());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token", accessToken);   // access token in body — stored in React state
            body.putAll(userInfo(saved));

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString())
                    .body(body);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Registration failed"));
        }
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String email    = loginRequest.get("email");
        String password = loginRequest.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        return userRepository.findByEmail(email)
                .map(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return ResponseEntity.badRequest().body((Object) Map.of("error", "Invalid password"));
                    }

                    String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getUsername());
                    String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail(), user.getUsername());

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("token", accessToken);
                    body.putAll(userInfo(user));

                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString())
                            .body((Object) body);
                })
                .orElse(ResponseEntity.badRequest().body((Object) Map.of("error", "Invalid email")));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    /**
     * Exchange the httpOnly refresh-token cookie for a new access token.
     *
     * <p>The frontend calls this on page load (to rehydrate in-memory state)
     * and after receiving a 401 on any API call.
     *
     * <p>The refresh cookie is path-scoped to {@code /api/auth}, so it is only
     * sent by the browser to this endpoint — not to any other API calls.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "No refresh token cookie"));
        }

        if (!jwtUtil.isValidRefreshToken(refreshToken)) {
            // Refresh token expired or tampered — clear the stale cookie and force re-login
            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, buildClearCookie().toString())
                    .body(Map.of("error", "Refresh token expired or invalid — please log in again"));
        }

        try {
            var claims = jwtUtil.parseRefreshToken(refreshToken);
            String email    = claims.getSubject();
            Long   userId   = claims.get("uid", Long.class);
            String username = claims.get("name", String.class);

            // Issue a fresh access token and rotate the refresh token (token rotation)
            String newAccessToken  = jwtUtil.generateAccessToken(userId, email, username);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, email, username);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(newRefreshToken).toString())
                    .body(Map.of("token", newAccessToken));

        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, buildClearCookie().toString())
                    .body(Map.of("error", "Token refresh failed"));
        }
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    /**
     * Clear the refresh-token cookie. The frontend is responsible for
     * discarding the in-memory access token on its side.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildClearCookie().toString())
                .body(Map.of("message", "Logged out"));
    }
}