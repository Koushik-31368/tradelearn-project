package com.tradelearn.server.config;

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

import com.tradelearn.server.security.JwtAuthenticationEntryPoint;
import com.tradelearn.server.security.JwtAuthenticationFilter;

/**
 * Security configuration — JWT-based stateless authentication.
 *
 * Public endpoints:
 *   - POST /api/auth/login, /api/auth/register, /api/auth/refresh
 *   - GET  /api/match/open, /api/match/active, /api/match/finished (read-only)
 *   - GET  /api/users/leaderboard
 *   - WebSocket /ws/**
 *   - Actuator /actuator/health, /actuator/info, /actuator/prometheus
 *
 * All other /api/** endpoints require a valid JWT Bearer token.
 * Unauthenticated requests receive a clean 401 JSON response.
 *
 * CORS origins loaded from CORS_ALLOWED_ORIGINS env var.
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
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()

                // ── Public: Read-only match listings ──
                .requestMatchers("GET", "/api/match/open").permitAll()
                .requestMatchers("GET", "/api/match/active").permitAll()
                .requestMatchers("GET", "/api/match/finished").permitAll()
                .requestMatchers("GET", "/api/match/{gameId}").permitAll()

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