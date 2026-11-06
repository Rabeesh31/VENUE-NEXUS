package com.venuenexus.common.error;

import org.springframework.http.HttpStatus;

/**
 * Standard platform ErrorCode enum.
 * Single source of truth matching the error catalogue in docs/api-contract.md.
 */
public enum ErrorCode {

    // --- Active Sprint 1 Error Codes ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed JSON or invalid request format."),
    PAGINATION_INVALID(HttpStatus.BAD_REQUEST, "Invalid pagination parameters."),
    INVALID_PARENT_CHILD(HttpStatus.BAD_REQUEST, "Invalid parent-child relationship."),

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password."),
    AUTH_ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "Account has been locked due to repeated failed login attempts."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token has expired."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Authentication token is invalid or malformed."),
    AUTH_TOKEN_WRONG_TYPE(HttpStatus.UNAUTHORIZED, "Authentication token type is not supported for this operation."),
    AUTH_TOKEN_INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED, "Authentication token audience is invalid."),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Authentication token has been revoked."),
    AUTH_REFRESH_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; security token family revoked."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied for this role and permission."),
    LOCATION_SCOPE_VIOLATION(HttpStatus.FORBIDDEN, "Access denied: user scope does not permit accessing this location."),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource could not be found."),
    QR_NOT_FOUND(HttpStatus.NOT_FOUND, "QR identity not found or does not resolve to an active resource."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint."),

    CONFLICT_DUPLICATE(HttpStatus.CONFLICT, "A resource with the specified unique identifier already exists."),

    QR_REVOKED(HttpStatus.GONE, "QR identity has been revoked and is no longer valid."),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred."),

    // --- Reserved Codes (CR-02, CR-03, CR-04, CR-05 for Sprints 2-4) ---
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Payment provider gateway error."),
    PAYMENT_DECLINED(HttpStatus.PAYMENT_REQUIRED, "Payment transaction was declined."),
    REFUND_NOT_PERMITTED(HttpStatus.BAD_REQUEST, "Refund is not permitted for this transaction."),
    REFUND_WINDOW_EXPIRED(HttpStatus.BAD_REQUEST, "Refund window has expired."),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Webhook signature verification failed."),
    SESSION_CANNOT_CLOSE(HttpStatus.BAD_REQUEST, "Customer session cannot be closed in its current state."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI service is temporarily unavailable."),
    AI_REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "AI request was rejected.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public int getStatusCode() {
        return httpStatus.value();
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
