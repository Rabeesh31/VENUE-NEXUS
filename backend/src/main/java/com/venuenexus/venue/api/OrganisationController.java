package com.venuenexus.venue.api;

import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.SecurityExpressions;
import com.venuenexus.venue.dto.CreateOrganisationRequest;
import com.venuenexus.venue.dto.OrganisationResponse;
import com.venuenexus.venue.dto.UpdateOrganisationRequest;
import com.venuenexus.venue.service.OrganisationManagementService;
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
@RequestMapping("/api/v1/organisations")
@RequiredArgsConstructor
@Tag(name = "Organisations", description = "Tenant-scoped organisation management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class OrganisationController {

    private final OrganisationManagementService organisationManagementService;

    @PostMapping
    @PreAuthorize(SecurityExpressions.TENANT_ADMIN_ONLY)
    @Operation(summary = "Create organisation (Tenant Admin only)")
    public ResponseEntity<OrganisationResponse> createOrganisation(@Valid @RequestBody CreateOrganisationRequest request) {
        OrganisationResponse response = organisationManagementService.createOrganisation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "Get organisation by ID")
    public ResponseEntity<OrganisationResponse> getOrganisationById(@PathVariable UUID id) {
        return ResponseEntity.ok(organisationManagementService.getOrganisationById(id));
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "List organisations in tenant")
    public ResponseEntity<PageResponse<OrganisationResponse>> listOrganisations(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        PageParams pageParams = PageParams.of(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(organisationManagementService.listOrganisations(status, pageParams));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityExpressions.TENANT_ADMIN_ONLY)
    @Operation(summary = "Update organisation details (Tenant Admin only)")
    public ResponseEntity<OrganisationResponse> updateOrganisation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganisationRequest request
    ) {
        return ResponseEntity.ok(organisationManagementService.updateOrganisation(id, request));
    }
}

