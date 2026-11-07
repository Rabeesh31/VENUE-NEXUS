package com.venuenexus.venue.api;

import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.SecurityExpressions;
import com.venuenexus.venue.dto.CreateTenantRequest;
import com.venuenexus.venue.dto.TenantResponse;
import com.venuenexus.venue.dto.UpdateTenantRequest;
import com.venuenexus.venue.service.TenantManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Platform-scoped tenant lifecycle endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

    private final TenantManagementService tenantManagementService;

    @PostMapping
    @PreAuthorize(SecurityExpressions.PLATFORM_ONLY)
    @Operation(summary = "Create tenant (Platform Admin only)")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantResponse response = tenantManagementService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityExpressions.PLATFORM_ONLY)
    @Operation(summary = "Get tenant by ID (Platform Admin only)")
    public ResponseEntity<TenantResponse> getTenantById(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantManagementService.getTenantById(id));
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.PLATFORM_ONLY)
    @Operation(summary = "List tenants with pagination (Platform Admin only)")
    public ResponseEntity<PageResponse<TenantResponse>> listTenants(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        PageParams pageParams = PageParams.of(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(tenantManagementService.listTenants(status, pageParams));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityExpressions.PLATFORM_ONLY)
    @Operation(summary = "Update tenant details (Platform Admin only)")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return ResponseEntity.ok(tenantManagementService.updateTenant(id, request));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize(SecurityExpressions.PLATFORM_ONLY)
    @Operation(summary = "Suspend tenant (Platform Admin only)")
    public ResponseEntity<TenantResponse> suspendTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantManagementService.suspendTenant(id));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(SecurityExpressions.PLATFORM_ONLY)
    @Operation(summary = "Reactivate tenant (Platform Admin only)")
    public ResponseEntity<TenantResponse> reactivateTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantManagementService.reactivateTenant(id));
    }
}

