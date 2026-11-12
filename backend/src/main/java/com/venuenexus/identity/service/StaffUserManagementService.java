package com.venuenexus.identity.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.identity.dto.CreateStaffUserRequest;
import com.venuenexus.identity.dto.StaffUserResponse;
import com.venuenexus.identity.dto.UpdateStaffUserRequest;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.repository.DepartmentRepository;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffUserManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("displayName", "email", "role", "status", "createdAt");

    private final AppUserRepository appUserRepository;
    private final OrganisationRepository organisationRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScopeEnforcer scopeEnforcer;

    @Transactional
    public StaffUserResponse createStaffUser(CreateStaffUserRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        CurrentStaff caller = CurrentStaff.require();

        // Check if email already exists
        if (appUserRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "User with email '" + request.email() + "' already exists");
        }

        // Validate target role assignment permissions
        StaffRole targetRole = StaffRole.valueOf(request.role());
        validateRoleCreationPermission(caller.role(), targetRole);

        // Validate organization if provided
        if (request.organisationId() != null) {
            organisationRepository.findByTenantIdAndId(tenantId, request.organisationId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Organisation not found in this tenant"));
        }

        // Validate location if provided
        if (request.locationId() != null) {
            var location = locationRepository.findByTenantIdAndId(tenantId, request.locationId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Location not found in this tenant"));
            if (request.organisationId() != null && !location.getOrganisationId().equals(request.organisationId())) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Location does not belong to specified organisation");
            }
        }

        // Validate department if provided
        if (request.departmentId() != null) {
            var department = departmentRepository.findByTenantIdAndId(tenantId, request.departmentId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Department not found in this tenant"));
            if (request.locationId() != null && !department.getLocationId().equals(request.locationId())) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Department does not belong to specified location");
            }
        }

        // Scope check for caller
        scopeEnforcer.assertCanAccess(request.organisationId(), request.locationId());

        AppUser user = AppUser.builder()
                .tenantId(tenantId)
                .organisationId(request.organisationId())
                .locationId(request.locationId())
                .departmentId(request.departmentId())
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(targetRole.name())
                .status("ACTIVE")
                .available(true)
                .failedLoginCount(0)
                .build();

        AppUser saved = appUserRepository.save(user);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public StaffUserResponse getStaffUserById(UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        AppUser user = appUserRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Staff user not found"));

        scopeEnforcer.assertCanAccess(user.getOrganisationId(), user.getLocationId());
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffUserResponse> listStaffUsers(UUID locationId, String role, PageParams pageParams) {
        UUID tenantId = TenantContext.requireTenantId();
        pageParams.validate(ALLOWED_SORT_FIELDS);
        Pageable pageable = pageParams.toPageable(ALLOWED_SORT_FIELDS);

        if (locationId != null) {
            var loc = locationRepository.findByTenantIdAndId(tenantId, locationId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));
            scopeEnforcer.assertCanAccess(loc.getOrganisationId(), loc.getId());
        }

        Page<AppUser> page;
        if (locationId != null) {
            page = appUserRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable);
        } else if (role != null && !role.isBlank()) {
            page = appUserRepository.findByTenantIdAndRole(tenantId, role.toUpperCase(), pageable);
        } else {
            page = appUserRepository.findAllByTenantId(tenantId, pageable);
        }

        var restrictedLoc = scopeEnforcer.restrictedLocationId();
        var restrictedOrg = scopeEnforcer.restrictedOrganisationId();

        var filtered = page.getContent().stream()
                .filter(u -> {
                    if (restrictedOrg.isPresent() && u.getOrganisationId() != null && !u.getOrganisationId().equals(restrictedOrg.get())) {
                        return false;
                    }
                    if (restrictedLoc.isPresent() && u.getLocationId() != null && !u.getLocationId().equals(restrictedLoc.get())) {
                        return false;
                    }
                    return true;
                })
                .map(this::mapToResponse)
                .toList();

        return PageResponse.of(filtered, page.getNumber(), page.getSize(), filtered.size());
    }

    @Transactional
    public StaffUserResponse updateStaffUser(UUID userId, UpdateStaffUserRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        CurrentStaff caller = CurrentStaff.require();

        AppUser user = appUserRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Staff user not found"));

        scopeEnforcer.assertCanAccess(user.getOrganisationId(), user.getLocationId());

        if (request.role() != null) {
            StaffRole newRole = StaffRole.valueOf(request.role());
            validateRoleCreationPermission(caller.role(), newRole);
            user.setRole(newRole.name());
        }

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }
        if (request.organisationId() != null) {
            organisationRepository.findByTenantIdAndId(tenantId, request.organisationId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Organisation not found in this tenant"));
            user.setOrganisationId(request.organisationId());
        }
        if (request.locationId() != null) {
            var location = locationRepository.findByTenantIdAndId(tenantId, request.locationId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Location not found in this tenant"));
            user.setLocationId(request.locationId());
        }
        if (request.departmentId() != null) {
            departmentRepository.findByTenantIdAndId(tenantId, request.departmentId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Department not found in this tenant"));
            user.setDepartmentId(request.departmentId());
        }
        if (request.isAvailable() != null) {
            user.setAvailable(request.isAvailable());
        }

        AppUser updated = appUserRepository.save(user);
        return mapToResponse(updated);
    }

    @Transactional
    public StaffUserResponse deactivateStaffUser(UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        AppUser user = appUserRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Staff user not found"));

        scopeEnforcer.assertCanAccess(user.getOrganisationId(), user.getLocationId());

        user.setStatus("DEACTIVATED");
        user.setAvailable(false);
        AppUser updated = appUserRepository.save(user);
        return mapToResponse(updated);
    }

    @Transactional
    public StaffUserResponse reactivateStaffUser(UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        AppUser user = appUserRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Staff user not found"));

        scopeEnforcer.assertCanAccess(user.getOrganisationId(), user.getLocationId());

        user.setStatus("ACTIVE");
        AppUser updated = appUserRepository.save(user);
        return mapToResponse(updated);
    }

    private void validateRoleCreationPermission(StaffRole callerRole, StaffRole targetRole) {
        if (callerRole == StaffRole.PLATFORM_ADMIN) {
            return;
        }
        if (callerRole == StaffRole.TENANT_ADMIN) {
            if (targetRole == StaffRole.PLATFORM_ADMIN) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "Tenant Admin cannot create Platform Admins");
            }
            return;
        }
        if (callerRole == StaffRole.VENUE_MANAGER) {
            if (targetRole == StaffRole.PLATFORM_ADMIN || targetRole == StaffRole.TENANT_ADMIN || targetRole == StaffRole.VENUE_MANAGER) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "Venue Manager cannot create administrators or other venue managers");
            }
            return;
        }
        throw new ApiException(ErrorCode.ACCESS_DENIED, "Caller role is not permitted to create staff users");
    }

    private StaffUserResponse mapToResponse(AppUser user) {
        return new StaffUserResponse(
                user.getId(),
                user.getTenantId(),
                user.getOrganisationId(),
                user.getLocationId(),
                user.getDepartmentId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.isAvailable(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

