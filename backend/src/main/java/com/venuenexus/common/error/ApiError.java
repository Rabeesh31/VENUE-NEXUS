package com.venuenexus.common.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Standard API error payload returned on all HTTP errors.
 * Matches docs/api-contract.md §3.3.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private final Instant timestamp;
    private final int status;
    private final String code;
    private final String message;
    private final String path;
    private final String traceId;
    private final List<FieldErrorDetail> details;

    public ApiError(int status, String code, String message, String path, String traceId, List<FieldErrorDetail> details) {
        this.timestamp = Instant.now();
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.traceId = traceId;
        this.details = (details != null) ? List.copyOf(details) : Collections.emptyList();
    }

    public static ApiError of(ErrorCode errorCode, String path, String traceId) {
        return new ApiError(errorCode.getStatusCode(), errorCode.name(), errorCode.getDefaultMessage(), path, traceId, Collections.emptyList());
    }

    public static ApiError of(ErrorCode errorCode, String customMessage, String path, String traceId) {
        return new ApiError(errorCode.getStatusCode(), errorCode.name(), customMessage, path, traceId, Collections.emptyList());
    }

    public static ApiError of(ErrorCode errorCode, String customMessage, String path, String traceId, List<FieldErrorDetail> details) {
        return new ApiError(errorCode.getStatusCode(), errorCode.name(), customMessage, path, traceId, details);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public String getTraceId() {
        return traceId;
    }

    public List<FieldErrorDetail> getDetails() {
        return details;
    }

    public record FieldErrorDetail(String field, String issue) {
    }
}
