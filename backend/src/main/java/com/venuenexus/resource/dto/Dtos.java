package com.venuenexus.resource.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.venuenexus.resource.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Request/response shapes. NO tenantId field anywhere: tenant comes from the security context.
 * Unknown JSON properties must be rejected (spring.jackson.deserialization.fail-on-unknown-properties=true).
 */
public final class Dtos {
    private Dtos() {}

    public record CreateResourceRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$") String code,
            @NotBlank @Size(max = 255) String displayName,
            @NotNull ResourceType type,
            @NotNull UUID locationId,
            UUID spaceId,
            UUID departmentId,
            ActivationPolicy activationPolicy,
            @Size(max = 2000) String capabilitiesJson) {}

    public record BulkCreateRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String prefix,
            @Min(0) @Max(100000) int start,
            @Min(0) @Max(100000) int end,
            @Min(0) @Max(6) int padWidth,
            @NotNull ResourceType type,
            @NotNull UUID locationId,
            UUID spaceId,
            UUID departmentId) {}

    public record ResourceResponse(
            UUID id, String code, String displayName, ResourceType type, UUID locationId, UUID spaceId,
            UUID departmentId, ResourceStatus status, ActivationPolicy activationPolicy,
            QrStatus qrStatus, Integer qrVersion) {}

    /** Returned exactly once, by issue and reissue. Never logged. */
    public record QrIssueResponse(
            UUID resourceId, int qrVersion, String qrUrl, String svg, String pngBase64, Instant issuedAt) {
        @Override
        public String toString() {
            return "QrIssueResponse[resourceId=" + resourceId + ", qrVersion=" + qrVersion + "]";
        }
    }

    public record QrStatusResponse(UUID resourceId, QrStatus status, Integer qrVersion, Instant issuedAt, Instant revokedAt) {}

    /**
     * Standard platform paginated response envelope.
     * Matches Phase 1 PageResponse contract: {"items": [...], "page": 0, "size": 20, "totalElements": N, "totalPages": M}.
     */
    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public PageResponse {
            items = (items != null) ? List.copyOf(items) : Collections.emptyList();
        }

        public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
            int computedPages = (size > 0) ? (int) Math.ceil((double) totalElements / size) : 0;
            return new PageResponse<>(items, page, size, totalElements, computedPages);
        }

        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> springPage) {
            return new PageResponse<>(
                    springPage.getContent(),
                    springPage.getNumber(),
                    springPage.getSize(),
                    springPage.getTotalElements(),
                    springPage.getTotalPages()
            );
        }

        /** Alias for backward compatibility with content field in Spring Data tests. */
        @JsonProperty("content")
        public List<T> getContent() {
            return items;
        }
    }

    /**
     * Standard platform API error payload returned on HTTP errors.
     * Matches Phase 1 ApiError.java and docs/api-contract.md §3.3.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiErrorResponse(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String traceId,
            List<FieldErrorDetail> details
    ) {
        public record FieldErrorDetail(String field, String issue) {}

        public ApiErrorResponse(int status, String code, String message, String path, String traceId) {
            this(Instant.now(), status, code, message, path, traceId, Collections.emptyList());
        }
    }
}
