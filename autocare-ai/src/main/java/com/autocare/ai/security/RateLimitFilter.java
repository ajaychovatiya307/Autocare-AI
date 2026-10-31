package com.autocare.ai.security;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.exception.ErrorResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final AutoCareProperties.RateLimit properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AutoCareProperties properties, ObjectMapper objectMapper) {
        this.properties = properties.rateLimit();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/kb/")
                || path.equals("/ai/autocare/chat/sync")
                || path.equals("/ai/autocare/chat/stream"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean kbRequest = request.getRequestURI().startsWith("/kb/");
        String bucketKey = (kbRequest ? "kb:" : "chat:") + authentication.getName();
        int limit = kbRequest ? properties.kbRequestsPerMinute() : properties.chatRequestsPerMinute();
        long windowMillis = properties.windowSeconds() * 1_000L;
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(bucketKey, key -> new Window(now, 0));
        long retryAfter = window.acquire(now, windowMillis, limit);
        if (retryAfter < 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String correlationId = String.valueOf(request.getAttribute(RequestLoggingFilter.CORRELATION_ID_ATTRIBUTE));
        log.warn(
                "correlationId={} user={} endpoint={} limitGroup={} status=429",
                correlationId,
                authentication.getName().replaceAll("[\\r\\n\\t]", "_"),
                request.getRequestURI().replaceAll("[\\r\\n\\t]", "_"),
                kbRequest ? "kb" : "chat");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ErrorResponseFactory.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please retry later.", request));
    }

    private static final class Window {

        private long startedAt;
        private int requests;

        private Window(long startedAt, int requests) {
            this.startedAt = startedAt;
            this.requests = requests;
        }

        private synchronized long acquire(long now, long windowMillis, int limit) {
            if (now - startedAt >= windowMillis) {
                startedAt = now;
                requests = 0;
            }
            if (requests >= limit) {
                return Math.max(1, (startedAt + windowMillis - now + 999) / 1_000);
            }
            requests++;
            return -1;
        }
    }
}
