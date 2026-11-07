package com.venuenexus.venue.api;

import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.SecurityExpressions;
import com.venuenexus.venue.dto.CreateSpaceRequest;
import com.venuenexus.venue.dto.SpaceResponse;
import com.venuenexus.venue.dto.UpdateSpaceRequest;
import com.venuenexus.venue.service.SpaceManagementService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
@Tag(name = "Spaces", description = "Venue spatial hierarchy (Building -> Floor -> Zone)")
@SecurityRequirement(name = "bearerAuth")
public class SpaceController {

    private final SpaceManagementService spaceManagementService;

    @PostMapping
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Create space within location hierarchy (Tenant Admin or Venue Manager)")
    public ResponseEntity<SpaceResponse> createSpace(@Valid @RequestBody CreateSpaceRequest request) {
        SpaceResponse response = spaceManagementService.createSpace(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "Get space by ID")
    public ResponseEntity<SpaceResponse> getSpaceById(@PathVariable UUID id) {
        return ResponseEntity.ok(spaceManagementService.getSpaceById(id));
    }

    @GetMapping("/by-location/{locationId}")
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "List all spaces in a location")
    public ResponseEntity<List<SpaceResponse>> listSpacesByLocation(@PathVariable UUID locationId) {
        return ResponseEntity.ok(spaceManagementService.listSpacesByLocation(locationId));
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "List spaces with pagination")
    public ResponseEntity<PageResponse<SpaceResponse>> listSpaces(
            @RequestParam UUID locationId,
            @RequestParam(required = false) String spaceType,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        PageParams pageParams = PageParams.of(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(spaceManagementService.listSpaces(locationId, spaceType, pageParams));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Update space details (Tenant Admin or Venue Manager)")
    public ResponseEntity<SpaceResponse> updateSpace(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSpaceRequest request
    ) {
        return ResponseEntity.ok(spaceManagementService.updateSpace(id, request));
    }
}

