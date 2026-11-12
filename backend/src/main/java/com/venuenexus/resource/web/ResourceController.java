package com.venuenexus.resource.web;

import com.venuenexus.resource.domain.*;
import com.venuenexus.resource.dto.Dtos.*;
import com.venuenexus.resource.port.Ports;
import com.venuenexus.resource.service.QrService;
import com.venuenexus.resource.service.ResourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for physical resources and QR identities.
 * Matches RBAC matrix:
 * MANAGE: TENANT_ADMIN, VENUE_MANAGER
 * READ:   TENANT_ADMIN, VENUE_MANAGER, SUPERVISOR, STAFF
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    static final String MANAGE = "hasAnyRole('TENANT_ADMIN','VENUE_MANAGER')";
    static final String READ = "hasAnyRole('TENANT_ADMIN','VENUE_MANAGER','SUPERVISOR','STAFF')";

    private final ResourceService resources;
    private final QrService qr;

    public ResourceController(ResourceService resources, QrService qr) {
        this.resources = resources;
        this.qr = qr;
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resources.create(req));
    }

    @PostMapping("/bulk")
    @PreAuthorize(MANAGE)
    public ResponseEntity<List<ResourceResponse>> bulk(@Valid @RequestBody BulkCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resources.bulkCreate(req));
    }

    @GetMapping
    @PreAuthorize(READ)
    public PageResponse<ResourceResponse> list(@RequestParam(required = false) UUID locationId,
                                               @RequestParam(required = false) ResourceType type,
                                               @RequestParam(required = false) ResourceStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(defaultValue = "code") String sort,
                                               @RequestParam(defaultValue = "asc") String dir) {
        return resources.list(locationId, type, status, page, size, sort, "desc".equalsIgnoreCase(dir));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResourceResponse get(@PathVariable UUID id) {
        return resources.get(id);
    }

    @GetMapping("/{id}/qr")
    @PreAuthorize(READ)
    public QrStatusResponse qrStatus(@PathVariable UUID id) {
        return qr.status(id);
    }

    @PostMapping("/{id}/qr")
    @PreAuthorize(MANAGE)
    public ResponseEntity<QrIssueResponse> issue(@PathVariable UUID id) {
        return noStore(HttpStatus.CREATED, qr.issue(id));
    }

    @PostMapping("/{id}/qr/reissue")
    @PreAuthorize(MANAGE)
    public ResponseEntity<QrIssueResponse> reissue(@PathVariable UUID id) {
        return noStore(HttpStatus.CREATED, qr.reissue(id));
    }

    @PostMapping("/{id}/qr/revoke")
    @PreAuthorize(MANAGE)
    public QrStatusResponse revoke(@PathVariable UUID id) {
        return qr.revoke(id);
    }

    /** The response carries the one-time token: never cache it. */
    private static ResponseEntity<QrIssueResponse> noStore(HttpStatus s, QrIssueResponse body) {
        return ResponseEntity.status(s).cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body);
    }

    private static final Pattern SAFE_TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /**
     * Controller-local exception handler for domain ResourceApiException.
     * Produces exact platform ApiError JSON contract (status, code, message, path, traceId, timestamp, details)
     * and sets X-Trace-Id response header.
     * Being controller-local, this takes precedence in Spring MVC before any GlobalExceptionHandler.
     */
    @ExceptionHandler(Ports.ResourceApiException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceApiException(
            Ports.ResourceApiException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = resolveTraceId(request, response);
        ApiErrorResponse error = new ApiErrorResponse(
                Instant.now(),
                ex.status(),
                ex.code(),
                ex.getMessage(),
                request.getRequestURI(),
                traceId,
                Collections.emptyList()
        );
        return ResponseEntity.status(ex.status())
                .header("X-Trace-Id", traceId)
                .body(error);
    }

    private String resolveTraceId(HttpServletRequest request, HttpServletResponse response) {
        // 1. Reuse established MDC trace context if set by Phase 1 TraceIdFilter
        String mdcTrace = MDC.get("traceId");
        if (mdcTrace != null && !mdcTrace.isBlank()) {
            return mdcTrace;
        }
        // 2. Reuse established request attribute if available
        Object attr = request.getAttribute("traceId");
        if (attr != null && !attr.toString().isBlank()) {
            return attr.toString();
        }
        // 3. Reuse response header if already populated
        if (response != null) {
            String respHeader = response.getHeader("X-Trace-Id");
            if (respHeader != null && !respHeader.isBlank()) {
                return respHeader;
            }
        }
        // 4. Validate incoming X-Trace-Id header using Phase 1 safe pattern
        String incoming = request.getHeader("X-Trace-Id");
        if (incoming != null && SAFE_TRACE_ID_PATTERN.matcher(incoming.trim()).matches()) {
            return incoming.trim();
        }
        // 5. Fallback fresh UUID
        return UUID.randomUUID().toString();
    }
}
