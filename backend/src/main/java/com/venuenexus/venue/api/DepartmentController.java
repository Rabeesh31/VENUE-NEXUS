package com.venuenexus.venue.api;

import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.SecurityExpressions;
import com.venuenexus.venue.dto.CreateDepartmentRequest;
import com.venuenexus.venue.dto.DepartmentResponse;
import com.venuenexus.venue.dto.UpdateDepartmentRequest;
import com.venuenexus.venue.service.DepartmentManagementService;
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
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Location-scoped operational departments")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentManagementService departmentManagementService;

    @PostMapping
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Create department in location (Tenant Admin or Venue Manager)")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse response = departmentManagementService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "Get department by ID")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(departmentManagementService.getDepartmentById(id));
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    @Operation(summary = "List departments for location")
    public ResponseEntity<PageResponse<DepartmentResponse>> listDepartments(
            @RequestParam UUID locationId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        PageParams pageParams = PageParams.of(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(departmentManagementService.listDepartments(locationId, pageParams));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    @Operation(summary = "Update department details (Tenant Admin or Venue Manager)")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        return ResponseEntity.ok(departmentManagementService.updateDepartment(id, request));
    }
}

