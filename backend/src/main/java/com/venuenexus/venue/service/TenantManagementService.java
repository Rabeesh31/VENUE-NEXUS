package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.venue.dto.CreateTenantRequest;
import com.venuenexus.venue.dto.TenantResponse;
import com.venuenexus.venue.dto.UpdateTenantRequest;
import com.venuenexus.venue.entity.Tenant;
import com.venuenexus.venue.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "status", "createdAt");

    private final TenantRepository tenantRepository;

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.findByName(request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Tenant with name '" + request.name() + "' already exists");
        }

        Tenant tenant = Tenant.builder()
                .name(request.name())
                .status("ACTIVE")
                .branding(request.branding())
                .build();

        Tenant saved = tenantRepository.save(tenant);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantById(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found"));
        return mapToResponse(tenant);
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> listTenants(String status, PageParams pageParams) {
        pageParams.validate(ALLOWED_SORT_FIELDS);
        Pageable pageable = pageParams.toPageable(ALLOWED_SORT_FIELDS);

        Page<Tenant> page;
        if (status != null && !status.isBlank()) {
            page = tenantRepository.findAllByStatus(status.toUpperCase(), pageable);
        } else {
            page = tenantRepository.findAll(pageable);
        }

        return PageResponse.from(page.map(this::mapToResponse));
    }

    @Transactional
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found"));

        if (!tenant.getName().equalsIgnoreCase(request.name()) && tenantRepository.findByName(request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Tenant with name '" + request.name() + "' already exists");
        }

        tenant.setName(request.name());
        tenant.setBranding(request.branding());

        Tenant updated = tenantRepository.save(tenant);
        return mapToResponse(updated);
    }

    @Transactional
    public TenantResponse suspendTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found"));
        tenant.setStatus("SUSPENDED");
        Tenant updated = tenantRepository.save(tenant);
        return mapToResponse(updated);
    }

    @Transactional
    public TenantResponse reactivateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found"));
        tenant.setStatus("ACTIVE");
        Tenant updated = tenantRepository.save(tenant);
        return mapToResponse(updated);
    }

    private TenantResponse mapToResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getBranding(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}

