package com.venuenexus.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.common.error.ApiError;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter-level access denied handler emitting JSON {@link ApiError} payloads for 403 Forbidden responses.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        String traceId = TraceIdFilter.getCurrentTraceId();
        int status = ErrorCode.ACCESS_DENIED.getStatusCode();

        log.warn("Access denied [traceId={}, path={}]: {}",
                traceId, request.getRequestURI(), accessDeniedException.getMessage());

        ApiError errorBody = ApiError.of(
                ErrorCode.ACCESS_DENIED,
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
