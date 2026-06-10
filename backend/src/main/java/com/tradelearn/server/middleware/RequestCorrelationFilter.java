package com.tradelearn.server.middleware;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
 * Request correlation filter.
 *
 * Assigns a unique requestId to every HTTP request and places it
 * in the MDC (Mapped Diagnostic Context) so all downstream log
 * statements include the correlation ID. Also sets response headers
 * so the frontend can reference request IDs in bug reports.
 *
 * Headers:
 *   - X-Request-Id (response) — the correlation ID for this request
 *   - X-Request-Duration-Ms (response) — total processing time
 *
 * MDC keys set:
 *   - requestId — unique per-request UUID
 *   - requestPath — the URI path
 *   - requestMethod — HTTP method
 */
@Component
@Order(0)
public class RequestCorrelationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestId = UUID.randomUUID().toString().substring(0, 12);
        long startTime = System.currentTimeMillis();

        try {
            // Set MDC context for all downstream logging
            MDC.put("requestId", requestId);
            MDC.put("requestPath", httpRequest.getRequestURI());
            MDC.put("requestMethod", httpRequest.getMethod());

            // Set response header
            httpResponse.setHeader("X-Request-Id", requestId);

            chain.doFilter(request, response);

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            httpResponse.setHeader("X-Request-Duration-Ms", String.valueOf(duration));

            // Log slow requests (> 2 seconds)
            if (duration > 2000) {
                log.warn("[Slow Request] {} {} took {}ms (requestId={})",
                        httpRequest.getMethod(), httpRequest.getRequestURI(),
                        duration, requestId);
            }

            MDC.clear();
        }
    }
}
