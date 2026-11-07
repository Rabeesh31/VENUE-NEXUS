package com.venuenexus.identity.api;

import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.identity.dto.CreateStaffUserRequest;
import com.venuenexus.identity.dto.StaffUserResponse;
import com.venuenexus.identity.dto.UpdateStaffUserRequest;
import com.venuenexus.identity.service.StaffUserManagementService;
import com.venuenexus.security.authz.SecurityExpressions;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Staff Users", description = "Tenant and location-scoped staff user management")
@SecurityRequirement(name = "bearerAuth")
public class StaffUserController {

    private final StaffUserManagementService staffUserManagementService;

    @PostMapping
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Create staff user (Tenant Admin or Venue Manager)")
    public ResponseEntity<StaffUserResponse> createStaffUser(@Valid @RequestBody CreateStaffUserRequest request) {
        StaffUserResponse response = staffUserManagementService.createStaffUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "Get staff user by ID")
    public ResponseEntity<StaffUserResponse> getStaffUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffUserManagementService.getStaffUserById(id));
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "List staff users with pagination")
    public ResponseEntity<PageResponse<StaffUserResponse>> listStaffUsers(
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "displayName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        PageParams pageParams = PageParams.of(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(staffUserManagementService.listStaffUsers(locationId, role, pageParams));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Update staff user details (Tenant Admin or Venue Manager)")
    public ResponseEntity<StaffUserResponse> updateStaffUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffUserRequest request
    ) {
        return ResponseEntity.ok(staffUserManagementService.updateStaffUser(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Deactivate staff user")
    public ResponseEntity<StaffUserResponse> deactivateStaffUser(@PathVariable UUID id) {
        return ResponseEntity.ok(staffUserManagementService.deactivateStaffUser(id));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Reactivate staff user")
    public ResponseEntity<StaffUserResponse> reactivateStaffUser(@PathVariable UUID id) {
        return ResponseEntity.ok(staffUserManagementService.reactivateStaffUser(id));
    }
}

