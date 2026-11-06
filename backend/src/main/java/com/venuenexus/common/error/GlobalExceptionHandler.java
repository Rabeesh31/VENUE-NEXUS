package com.venuenexus.common.error;

import com.venuenexus.common.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Global exception handler providing standardized ApiError responses.
 * Matches docs/api-contract.md §3.3.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ErrorCode errorCode = ex.getErrorCode();
        ApiError apiError = ApiError.of(
                errorCode,
                ex.getMessage(),
                request.getRequestURI(),
                traceId,
                ex.getDetails()
        );
        return buildResponse(apiError, errorCode.getHttpStatus(), traceId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        List<ApiError.FieldErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new ApiError.FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        ApiError apiError = ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                request.getRequestURI(),
                traceId,
                details
        );
        return buildResponse(apiError, ErrorCode.VALIDATION_FAILED.getHttpStatus(), traceId);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        List<ApiError.FieldErrorDetail> details = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation -> {
            String property = violation.getPropertyPath().toString();
            details.add(new ApiError.FieldErrorDetail(property, violation.getMessage()));
        });
        ApiError apiError = ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                request.getRequestURI(),
                traceId,
                details
        );
        return buildResponse(apiError, ErrorCode.VALIDATION_FAILED.getHttpStatus(), traceId);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ApiError apiError = ApiError.of(
                ErrorCode.MALFORMED_REQUEST,
                "Malformed JSON request or invalid payload format.",
                request.getRequestURI(),
                traceId
        );
        return buildResponse(apiError, ErrorCode.MALFORMED_REQUEST.getHttpStatus(), traceId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        String paramName = ex.getName();
        ErrorCode code = ErrorCode.MALFORMED_REQUEST;
        if ("page".equalsIgnoreCase(paramName) || "size".equalsIgnoreCase(paramName)) {
            code = ErrorCode.PAGINATION_INVALID;
        }
        List<ApiError.FieldErrorDetail> details = List.of(
                new ApiError.FieldErrorDetail(paramName, "Invalid value for parameter: " + ex.getValue())
        );
        ApiError apiError = ApiError.of(
                code,
                code.getDefaultMessage(),
                request.getRequestURI(),
                traceId,
                details
        );
        return buildResponse(apiError, code.getHttpStatus(), traceId);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        List<ApiError.FieldErrorDetail> details = List.of(
                new ApiError.FieldErrorDetail(ex.getParameterName(), "Required parameter is missing")
        );
        ApiError apiError = ApiError.of(
                ErrorCode.MALFORMED_REQUEST,
                "Required request parameter '" + ex.getParameterName() + "' is missing.",
                request.getRequestURI(),
                traceId,
                details
        );
        return buildResponse(apiError, ErrorCode.MALFORMED_REQUEST.getHttpStatus(), traceId);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ApiError apiError = ApiError.of(
                ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint.",
                request.getRequestURI(),
                traceId
        );
        return buildResponse(apiError, ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus(), traceId);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ApiError apiError = ApiError.of(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDefaultMessage(),
                request.getRequestURI(),
                traceId
        );
        return buildResponse(apiError, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus(), traceId);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ApiError apiError = ApiError.of(
                ErrorCode.RESOURCE_NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(),
                request.getRequestURI(),
                traceId
        );
        return buildResponse(apiError, ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus(), traceId);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ApiError apiError = ApiError.of(
                ErrorCode.ACCESS_DENIED,
                ErrorCode.ACCESS_DENIED.getDefaultMessage(),
                request.getRequestURI(),
                traceId
        );
        return buildResponse(apiError, ErrorCode.ACCESS_DENIED.getHttpStatus(), traceId);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ApiError apiError = ApiError.of(
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage(),
                request.getRequestURI(),
                traceId
        );
        return buildResponse(apiError, ErrorCode.AUTH_INVALID_CREDENTIALS.getHttpStatus(), traceId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        // Log unexpected error server-side with trace ID only; never expose details to client
        logger.error("Unexpected server exception. TraceId: {}", traceId, ex);

        ApiError apiError = ApiError.of(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                request.getRequestURI(),
                traceId
        );
        return buildResponse(apiError, ErrorCode.INTERNAL_ERROR.getHttpStatus(), traceId);
    }

    private String resolveTraceId() {
        return TraceIdFilter.getCurrentTraceId();
    }

    private ResponseEntity<ApiError> buildResponse(ApiError apiError, org.springframework.http.HttpStatus status, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceIdFilter.TRACE_HEADER, traceId);
        return new ResponseEntity<>(apiError, headers, status);
    }
}
