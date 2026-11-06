package com.venuenexus.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter that establishes and propagates distributed request trace IDs.
 * Matches docs/api-contract.md §5 and docs/security.md §7.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    // Strict safe pattern: alphanumeric and hyphens, 8 to 64 chars
    private static final Pattern SAFE_TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TRACE_HEADER);
        String traceId = resolveValidTraceId(incomingTraceId);

        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Ensure response header is present even if downstream threw
            if (!response.containsHeader(TRACE_HEADER)) {
                response.setHeader(TRACE_HEADER, traceId);
            }
            MDC.remove(MDC_KEY);
        }
    }

    public static String resolveValidTraceId(String candidate) {
        if (candidate != null && SAFE_TRACE_ID_PATTERN.matcher(candidate.trim()).matches()) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Resolves current trace ID from MDC, or generates a transient fallback.
     */
    public static String getCurrentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }
}
