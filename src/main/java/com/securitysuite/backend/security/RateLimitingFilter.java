package com.securitysuite.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using token bucket algorithm
 * Limits requests per IP address to prevent abuse
 */
@Component
@Slf4j
public class RateLimitingFilter implements Filter {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    // Default rate limit: 100 requests per minute per IP
    private static final int CAPACITY = 100;
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    // Stricter limits for auth endpoints
    private static final int AUTH_CAPACITY = 10;
    private static final Duration AUTH_REFILL_DURATION = Duration.ofMinutes(1);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIP(httpRequest);
        String requestUri = httpRequest.getRequestURI();

        // Determine if this is an auth endpoint
        boolean isAuthEndpoint = requestUri.startsWith("/auth/");

        Bucket bucket = resolveBucket(clientIp, isAuthEndpoint);

        if (bucket.tryConsume(1)) {
            // Request allowed
            chain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, requestUri);
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\": \"Too many requests\", \"message\": \"Rate limit exceeded. Please try again later.\"}"
            );
        }
    }

    private Bucket resolveBucket(String clientIp, boolean isAuthEndpoint) {
        return cache.computeIfAbsent(clientIp, k -> createNewBucket(isAuthEndpoint));
    }

    private Bucket createNewBucket(boolean isAuthEndpoint) {
        if (isAuthEndpoint) {
            // Stricter limit for auth endpoints (10 requests per minute)
            Bandwidth limit = Bandwidth.classic(AUTH_CAPACITY,
                    Refill.intervally(AUTH_CAPACITY, AUTH_REFILL_DURATION));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        } else {
            // Default limit (100 requests per minute)
            Bandwidth limit = Bandwidth.classic(CAPACITY,
                    Refill.intervally(CAPACITY, REFILL_DURATION));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        // Get first IP if multiple proxies
        return xfHeader.split(",")[0].trim();
    }
}
