package com.venuenexus.common.error;

import java.util.Collections;
import java.util.List;

/**
 * Base domain exception carrying platform ErrorCode and field details.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiError.FieldErrorDetail> details;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = Collections.emptyList();
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.emptyList();
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = Collections.emptyList();
    }

    public ApiException(ErrorCode errorCode, String message, List<ApiError.FieldErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = (details != null) ? List.copyOf(details) : Collections.emptyList();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<ApiError.FieldErrorDetail> getDetails() {
        return details;
    }
}
