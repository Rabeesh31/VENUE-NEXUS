package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateOrganisationRequest;
import com.venuenexus.venue.dto.OrganisationResponse;
import com.venuenexus.venue.dto.UpdateOrganisationRequest;
import com.venuenexus.venue.entity.Organisation;
import com.venuenexus.venue.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganisationManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "status", "createdAt");

    private final OrganisationRepository organisationRepository;
    private final ScopeEnforcer scopeEnforcer;

    @Transactional
    public OrganisationResponse createOrganisation(CreateOrganisationRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        if (organisationRepository.findByTenantIdAndName(tenantId, request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Organisation with name '" + request.name() + "' already exists in this tenant");
        }

        Organisation org = Organisation.builder()
                .tenantId(tenantId)
                .name(request.name())
                .status("ACTIVE")
                .build();

        Organisation saved = organisationRepository.save(org);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrganisationResponse getOrganisationById(UUID organisationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Organisation org = organisationRepository.findByTenantIdAndId(tenantId, organisationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Organisation not found"));

        scopeEnforcer.assertCanAccess(org.getId(), null);
        return mapToResponse(org);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganisationResponse> listOrganisations(String status, PageParams pageParams) {
        UUID tenantId = TenantContext.requireTenantId();
        pageParams.validate(ALLOWED_SORT_FIELDS);
        Pageable pageable = pageParams.toPageable(ALLOWED_SORT_FIELDS);

        // If user is restricted to a specific organisation, filter down accordingly
        var restrictedOrg = scopeEnforcer.restrictedOrganisationId();

        Page<Organisation> page;
        if (status != null && !status.isBlank()) {
            page = organisationRepository.findByTenantIdAndStatus(tenantId, status.toUpperCase(), pageable);
        } else {
            page = organisationRepository.findAllByTenantId(tenantId, pageable);
        }

        if (restrictedOrg.isPresent()) {
            UUID requiredOrgId = restrictedOrg.get();
            var filtered = page.getContent().stream()
                    .filter(org -> org.getId().equals(requiredOrgId))
                    .map(this::mapToResponse)
                    .toList();
            return PageResponse.of(filtered, page.getNumber(), page.getSize(), filtered.size());
        }

        return PageResponse.from(page.map(this::mapToResponse));
    }

    @Transactional
    public OrganisationResponse updateOrganisation(UUID organisationId, UpdateOrganisationRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Organisation org = organisationRepository.findByTenantIdAndId(tenantId, organisationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Organisation not found"));

        scopeEnforcer.assertCanAccess(org.getId(), null);

        if (!org.getName().equalsIgnoreCase(request.name())
                && organisationRepository.findByTenantIdAndName(tenantId, request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Organisation with name '" + request.name() + "' already exists in this tenant");
        }

        org.setName(request.name());
        Organisation updated = organisationRepository.save(org);
        return mapToResponse(updated);
    }

    private OrganisationResponse mapToResponse(Organisation org) {
        return new OrganisationResponse(
                org.getId(),
                org.getTenantId(),
                org.getName(),
                org.getStatus(),
                org.getCreatedAt(),
                org.getUpdatedAt()
        );
    }
}

