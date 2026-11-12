package com.venuenexus.venue.api;

import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.SecurityExpressions;
import com.venuenexus.venue.dto.CreateLocationRequest;
import com.venuenexus.venue.dto.LocationResponse;
import com.venuenexus.venue.dto.UpdateLocationRequest;
import com.venuenexus.venue.service.LocationManagementService;
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
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Tenant and organisation-scoped physical venue locations")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    private final LocationManagementService locationManagementService;

    @PostMapping
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Create location (Tenant Admin or Venue Manager)")
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        LocationResponse response = locationManagementService.createLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "Get location by ID")
    public ResponseEntity<LocationResponse> getLocationById(@PathVariable UUID id) {
        return ResponseEntity.ok(locationManagementService.getLocationById(id));
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "List locations")
    public ResponseEntity<PageResponse<LocationResponse>> listLocations(
            @RequestParam(required = false) UUID organisationId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        PageParams pageParams = PageParams.of(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(locationManagementService.listLocations(organisationId, pageParams));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Update location details (Tenant Admin or Venue Manager)")
    public ResponseEntity<LocationResponse> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request
    ) {
        return ResponseEntity.ok(locationManagementService.updateLocation(id, request));
    }
}

