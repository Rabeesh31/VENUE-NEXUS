package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateDepartmentRequest;
import com.venuenexus.venue.dto.DepartmentResponse;
import com.venuenexus.venue.dto.UpdateDepartmentRequest;
import com.venuenexus.venue.entity.Department;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.repository.DepartmentRepository;
import com.venuenexus.venue.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "departmentType", "createdAt");

    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final ScopeEnforcer scopeEnforcer;

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        // 1. Verify parent location exists in current tenant
        Location location = locationRepository.findByTenantIdAndId(tenantId, request.locationId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent location not found in this tenant"));

        // 2. Scope check on location
        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        // 3. Name uniqueness within location
        if (departmentRepository.findByTenantIdAndLocationIdAndName(tenantId, request.locationId(), request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Department with name '" + request.name() + "' already exists in this location");
        }

        Department department = Department.builder()
                .tenantId(tenantId)
                .locationId(request.locationId())
                .name(request.name())
                .departmentType(request.departmentType())
                .build();

        Department saved = departmentRepository.save(department);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(UUID departmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        Department department = departmentRepository.findByTenantIdAndId(tenantId, departmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Department not found"));

        Location location = locationRepository.findByTenantIdAndId(tenantId, department.getLocationId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());
        return mapToResponse(department);
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> listDepartments(UUID locationId, PageParams pageParams) {
        UUID tenantId = TenantContext.requireTenantId();
        pageParams.validate(ALLOWED_SORT_FIELDS);
        Pageable pageable = pageParams.toPageable(ALLOWED_SORT_FIELDS);

        Location location = locationRepository.findByTenantIdAndId(tenantId, locationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        Page<Department> page = departmentRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable);
        return PageResponse.from(page.map(this::mapToResponse));
    }

    @Transactional
    public DepartmentResponse updateDepartment(UUID departmentId, UpdateDepartmentRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Department department = departmentRepository.findByTenantIdAndId(tenantId, departmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Department not found"));

        Location location = locationRepository.findByTenantIdAndId(tenantId, department.getLocationId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        if (!department.getName().equalsIgnoreCase(request.name()) &&
                departmentRepository.findByTenantIdAndLocationIdAndName(tenantId, department.getLocationId(), request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Department with name '" + request.name() + "' already exists in this location");
        }

        department.setName(request.name());
        department.setDepartmentType(request.departmentType());

        Department updated = departmentRepository.save(department);
        return mapToResponse(updated);
    }

    private DepartmentResponse mapToResponse(Department dept) {
        return new DepartmentResponse(
                dept.getId(),
                dept.getTenantId(),
                dept.getLocationId(),
                dept.getName(),
                dept.getDepartmentType(),
                dept.getCreatedAt(),
                dept.getUpdatedAt()
        );
    }
}

