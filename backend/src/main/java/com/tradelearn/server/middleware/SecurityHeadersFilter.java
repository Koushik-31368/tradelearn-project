package com.tradelearn.server.middleware;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security headers filter for production hardening.
 *
 * Applies OWASP-recommended security headers to all HTTP responses:
 *   - X-Content-Type-Options: nosniff
 *   - X-Frame-Options: DENY
 *   - X-XSS-Protection: 0 (disabled — CSP is the modern replacement)
 *   - Strict-Transport-Security: max-age=31536000 (HSTS for 1 year)
 *   - Cache-Control: no-store for API responses
 *   - Content-Security-Policy: default-src 'self'
 *
 * These headers are critical for production deployment to prevent
 * clickjacking, MIME-sniffing, and downgrade attacks.
 */
@Component
@Order(2)
public class SecurityHeadersFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    @Value("${tradelearn.security.hsts.enabled:true}")
    private boolean hstsEnabled;

    @Value("${tradelearn.security.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Prevent MIME-type sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // Disable legacy XSS filter (CSP is the modern approach)
        httpResponse.setHeader("X-XSS-Protection", "0");

        // HSTS — only apply on HTTPS
        if (hstsEnabled && "https".equalsIgnoreCase(httpRequest.getScheme())) {
            httpResponse.setHeader("Strict-Transport-Security",
                    "max-age=" + hstsMaxAge + "; includeSubDomains");
        }

        // Prevent caching of API responses
        String path = httpRequest.getRequestURI();
        if (path.startsWith("/api/")) {
            httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            httpResponse.setHeader("Pragma", "no-cache");
        }

        // Referrer policy
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions policy — disable unused browser APIs
        httpResponse.setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=()");

        chain.doFilter(request, response);
    }
}
