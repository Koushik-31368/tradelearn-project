package com.tradelearn.server.security;

import java.io.IOException;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tradelearn.server.model.User;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT authentication filter for REST API requests.
 *
 * Extracts the Bearer token from the Authorization header, validates it,
 * and sets the Spring Security authentication context with the full User
 * entity as the principal.
 *
 * Skipped for:
 *   - Requests without Authorization header
 *   - WebSocket upgrade requests (handled by WebSocketAuthInterceptor)
 *   - Actuator endpoints
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip if already authenticated (e.g., by another filter)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            if (!jwtUtil.isValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            Claims claims = jwtUtil.parseToken(token);
            String email = claims.getSubject();

            // Load the full user entity as the principal
            User user = userDetailsService.findUserByEmail(email);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,                       // principal = User entity
                            null,                       // credentials
                            Collections.emptyList()     // authorities
                    );
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT authenticated user: {} (id={})", email, user.getId());

        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            // Don't set authentication — request continues as anonymous
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Skip JWT filter for WebSocket upgrade requests — those are handled
     * separately by the WebSocket auth interceptor.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/ws");
    }
}
