package com.autocare.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_ATTRIBUTE = "autocare.correlationId";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader("X-Correlation-ID", correlationId);
        MDC.put("correlationId", correlationId);
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String user = authentication == null ? "anonymous" : clean(authentication.getName());
            String role = authentication == null
                    ? "anonymous"
                    : authentication.getAuthorities().stream()
                            .map(authority -> clean(authority.getAuthority()))
                            .collect(Collectors.joining(","));
            String path = clean(request.getRequestURI());
            String mode = request.getRequestURI().endsWith("/stream")
                    ? "async"
                    : request.getRequestURI().endsWith("/sync") ? "sync" : "admin";
            String advisors = advisorsFor(mode);
            log.info(
                    "correlationId={} user={} role={} endpoint={} mode={} advisors={} latencyMs={} status={}",
                    correlationId,
                    user,
                    role,
                    path,
                    mode,
                    advisors,
                    (System.nanoTime() - start) / 1_000_000,
                    response.getStatus());
            MDC.remove("correlationId");
        }
    }

    private String clean(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\r\\n\\t]", "_");
    }

    private String advisorsFor(String mode) {
        if (mode.equals("admin")) {
            return "none";
        }
        return String.join(
                ",",
                "PiiVehicleRedactionAdvisor",
                MessageChatMemoryAdvisor.class.getSimpleName(),
                "ConversationAuditAdvisor",
                SimpleLoggerAdvisor.class.getSimpleName(),
                RetrievalAugmentationAdvisor.class.getSimpleName(),
                mode.equals("async") ? "StreamingCitationValidationAdvisor" : "CitationValidationAdvisor",
                "DomainSafeguardAdvisor");
    }
}
