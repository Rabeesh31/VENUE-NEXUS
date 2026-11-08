package com.venuenexus.resource.port;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interfaces the resource module needs from other modules. Adapters live OUTSIDE this module
 * and are wired once Phase 1 (audit, errors), Phase 3 (security context) and Phase 4 (venue) land.
 * Keeping them as ports respects the ArchUnit rule: no module touches another module's repositories.
 */
public final class Ports {
    private Ports() {}

    /** Phase 3 adapter port: values come from the validated JWT, never from the request. */
    public interface CallerContext {
        UUID tenantId();
        UUID userId();
        /** Null = whole tenant. Otherwise the set of location ids the caller may touch. */
        Set<UUID> allowedLocationIds();
        default boolean canAccessLocation(UUID locationId) {
            Set<UUID> ids = allowedLocationIds();
            return ids == null || ids.contains(locationId);
        }
    }

    /** Phase 4 adapter port. All checks are tenant-scoped. */
    public interface VenueLookup {
        boolean locationExists(UUID tenantId, UUID locationId);
        boolean spaceBelongsToLocation(UUID tenantId, UUID spaceId, UUID locationId);
        boolean departmentBelongsToLocation(UUID tenantId, UUID departmentId, UUID locationId);
    }

    /** Phase 1 adapter port (AuditLogService). Detail must never contain tokens or hashes. */
    public interface AuditPort {
        void record(String eventType, UUID tenantId, UUID actorUserId, Map<String, String> detail);
    }

    /**
     * Standard error codes matching Phase 1 ErrorCode catalog.
     */
    public enum ErrorCode {
        VALIDATION_FAILED(400, "Validation failed for one or more fields."),
        MALFORMED_REQUEST(400, "Malformed JSON or invalid request format."),
        PAGINATION_INVALID(400, "Invalid pagination parameters."),
        INVALID_PARENT_CHILD(400, "Invalid parent-child relationship."),
        ACCESS_DENIED(403, "Access is denied for this role and permission."),
        LOCATION_SCOPE_VIOLATION(403, "Access denied: user scope does not permit accessing this location."),
        RESOURCE_NOT_FOUND(404, "The requested resource could not be found."),
        QR_NOT_FOUND(404, "QR identity not found or does not resolve to an active resource."),
        CONFLICT_DUPLICATE(409, "A resource with the specified unique identifier already exists."),
        QR_REVOKED(410, "QR identity has been revoked and is no longer valid."),
        INTERNAL_ERROR(500, "An internal server error occurred.");

        private final int status;
        private final String defaultMessage;

        ErrorCode(int status, String defaultMessage) {
            this.status = status;
            this.defaultMessage = defaultMessage;
        }

        public int getStatus() { return status; }
        public String getDefaultMessage() { return defaultMessage; }
    }

    /**
     * Domain exception carrying platform ErrorCode or status/code.
     * Handled by controller-local @ExceptionHandler in ResourceController,
     * producing the exact platform ApiError JSON structure.
     */
    public static class ResourceApiException extends RuntimeException {
        private final int status;
        private final String code;

        public ResourceApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public ResourceApiException(ErrorCode errorCode) {
            super(errorCode.getDefaultMessage());
            this.status = errorCode.getStatus();
            this.code = errorCode.name();
        }

        public ResourceApiException(ErrorCode errorCode, String message) {
            super(message);
            this.status = errorCode.getStatus();
            this.code = errorCode.name();
        }

        public int status() { return status; }
        public String code() { return code; }
    }
}
