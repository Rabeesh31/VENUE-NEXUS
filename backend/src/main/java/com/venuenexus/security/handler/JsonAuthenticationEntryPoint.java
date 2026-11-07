package com.venuenexus.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.common.error.ApiError;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter-level authentication entry point emitting JSON {@link ApiError} payloads.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Must produce byte-for-byte identical error JSON structure to the MVC-level
 * {@link com.venuenexus.common.error.GlobalExceptionHandler}, including
 * the traceId field and response header {@code X-Trace-Id}.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);
    public static final String AUTH_EXCEPTION_ATTR = "VENUE_NEXUS_AUTH_EXCEPTION";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String traceId = TraceIdFilter.getCurrentTraceId();
        ApiException apiException = (ApiException) request.getAttribute(AUTH_EXCEPTION_ATTR);

        ErrorCode errorCode = apiException != null ? apiException.getErrorCode() : ErrorCode.AUTH_TOKEN_INVALID;
        String message = apiException != null ? apiException.getMessage() : errorCode.getDefaultMessage();
        int status = errorCode.getStatusCode();

        log.warn("Authentication failed [traceId={}, status={}, error={}, path={}]: {}",
                traceId, status, errorCode.name(), request.getRequestURI(), message);

        ApiError errorBody = ApiError.of(
                errorCode,
                message,
                request.getRequestURI(),
                traceId
        );

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(TraceIdFilter.TRACE_HEADER, traceId);

        objectMapper.writeValue(response.getWriter(), errorBody);
    }
}
