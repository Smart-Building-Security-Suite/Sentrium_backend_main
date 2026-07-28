package com.securitysuite.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for the /auth/** endpoints.
 * Limits each IP to {@code maxRequests} requests per {@code windowSeconds} seconds.
 *
 * <p>This is a best-effort guard suitable for single-instance deployments.
 * For multi-instance or high-throughput production use, replace with
 * a Redis-backed solution (e.g., Bucket4j + Spring Cache + Redis).
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final long windowMs;

    /** token → (count, windowStartMs) */
    private final Map<String, long[]> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${app.rate-limit.auth.max-requests:20}") int maxRequests,
            @Value("${app.rate-limit.auth.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMs = TimeUnit.SECONDS.toMillis(windowSeconds);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/auth/") && !path.startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = getClientIp(request);
        long now = Instant.now().toEpochMilli();

        long[] bucket = buckets.compute(ip, (key, existing) -> {
            if (existing == null || now - existing[1] > windowMs) {
                return new long[]{1, now};
            }
            existing[0]++;
            return existing;
        });

        if (bucket[0] > maxRequests) {
            log.warn("Rate limit exceeded for IP {} on {}", ip, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many authentication attempts. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
