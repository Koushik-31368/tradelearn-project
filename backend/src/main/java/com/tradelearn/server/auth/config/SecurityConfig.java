package com.tradelearn.server.auth.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tradelearn.server.auth.security.JwtAuthenticationEntryPoint;
import com.tradelearn.server.auth.security.JwtAuthenticationFilter;

/**
 * Security configuration — JWT-based stateless authentication.
 *
 * <h3>Token architecture (Issue 1)</h3>
 * <ul>
 *   <li><b>Access token:</b> Short-lived, returned in the login/register response body.
 *       Sent as {@code Authorization: Bearer} on every API call. Never stored
 *       in localStorage — lives in React in-memory state only.</li>
 *   <li><b>Refresh token:</b> Long-lived, set as an {@code httpOnly Secure SameSite=None}
 *       cookie scoped to {@code /api/auth}. The browser sends it automatically
 *       only to /api/auth/refresh and /api/auth/logout.</li>
 * </ul>
 *
 * <h3>Public endpoints</h3>
 * <ul>
 *   <li>POST /api/auth/login, /register, /refresh, /logout</li>
 *   <li>GET  /api/match/open, /active, /finished (read-only listings)</li>
 *   <li>GET  /api/users/leaderboard</li>
 *   <li>WebSocket /ws/**</li>
 *   <li>Actuator /actuator/health, /info, /prometheus</li>
 * </ul>
 *
 * All other /api/** endpoints require a valid JWT Bearer token.
 * Unauthenticated requests receive a clean 401 JSON response.
 *
 * CORS: allowedOrigins is an explicit allow-list ({@code CORS_ALLOWED_ORIGINS});
 * wildcards are blocked because {@code allowCredentials(true)} is set.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,https://tradelearn-project.vercel.app,https://tradelearn-project-kethans-projects-3fb29448.vercel.app}")
    private String allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          JwtAuthenticationEntryPoint jwtEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtEntryPoint = jwtEntryPoint;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // ── Public: Auth endpoints ──
                .requestMatchers("/api/auth/login", "/api/auth/register",
                                 "/api/auth/refresh", "/api/auth/logout").permitAll()

                // ── Public: Read-only match listings ──
                .requestMatchers("GET", "/api/match/open").permitAll()
                .requestMatchers("GET", "/api/match/active").permitAll()
                .requestMatchers("GET", "/api/match/finished").permitAll()
                .requestMatchers("GET", "/api/match/{gameId}").permitAll()

                // ── Public: Market data (Practice Mode — no auth needed) ──
                .requestMatchers("GET", "/api/market/**").permitAll()

                // ── Public: Leaderboard ──
                .requestMatchers("GET", "/api/users/leaderboard").permitAll()

                // ── Public: WebSocket (auth handled by interceptor) ──
                .requestMatchers("/ws/**").permitAll()

                // ── Public: Actuator health/metrics ──
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()

                // ── Everything else requires JWT ──
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtEntryPoint)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);

        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}