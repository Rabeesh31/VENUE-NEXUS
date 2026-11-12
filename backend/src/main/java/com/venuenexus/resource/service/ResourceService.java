package com.venuenexus.resource.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.resource.domain.*;
import com.venuenexus.resource.dto.Dtos.*;
import com.venuenexus.resource.port.Ports.*;
import com.venuenexus.resource.repo.ResourceQrRepository;
import com.venuenexus.resource.repo.ResourceRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_BULK = 100;
    private static final Set<String> SORT_FIELDS = Set.of("code", "resourceCode", "displayName", "createdAt", "type", "status");
    private static final int MAX_CAPABILITY_KEYS = 20;

    private final ResourceRepository resources;
    private final ResourceQrRepository qrs;
    private final CallerContext caller;
    private final VenueLookup venue;
    private final ObjectMapper json;

    public ResourceService(ResourceRepository resources, ResourceQrRepository qrs,
                           CallerContext caller, VenueLookup venue, ObjectMapper json) {
        this.resources = resources;
        this.qrs = qrs;
        this.caller = caller;
        this.venue = venue;
        this.json = json;
    }

    @Transactional
    public ResourceResponse create(CreateResourceRequest req) {
        UUID tenantId = caller.tenantId();
        validateAssociations(tenantId, req.locationId(), req.spaceId(), req.departmentId());
        String code = normaliseCode(req.code());

        Resource r = new Resource(UUID.randomUUID(), tenantId, req.locationId(), code, req.displayName().trim(), req.type());
        r.setSpaceId(req.spaceId());
        r.setDepartmentId(req.departmentId());
        if (req.activationPolicy() != null) r.setActivationPolicy(req.activationPolicy());
        r.setCapabilities(validateCapabilities(req.capabilitiesJson()));

        // The DB unique index (tenant_id, location_id, resource_code) is the real guard against races.
        if (resources.existsByTenantIdAndLocationIdAndResourceCodeIgnoreCase(tenantId, req.locationId(), code)) {
            throw duplicate(code);
        }
        try {
            resources.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw duplicate(code);
        }
        return toResponse(r, null);
    }

    /** All-or-nothing. No QR is issued here: that would hand back many plain tokens at once. */
    @Transactional
    public List<ResourceResponse> bulkCreate(BulkCreateRequest req) {
        UUID tenantId = caller.tenantId();
        if (req.start() > req.end()) throw bad("start must be less than or equal to end");
        int count = req.end() - req.start() + 1;
        if (count > MAX_BULK) throw bad("A bulk request may create at most " + MAX_BULK + " resources");
        validateAssociations(tenantId, req.locationId(), req.spaceId(), req.departmentId());

        List<String> codes = new ArrayList<>(count);
        for (int n = req.start(); n <= req.end(); n++) {
            String num = req.padWidth() > 0 ? String.format("%0" + req.padWidth() + "d", n) : Integer.toString(n);
            String code = normaliseCode(req.prefix() + num);
            if (code.length() > 100) throw bad("Generated code is too long");
            codes.add(code);
        }
        List<String> existing = resources.findExistingCodes(tenantId, req.locationId(), codes);
        if (!existing.isEmpty()) {
            throw new ResourceApiException(ErrorCode.CONFLICT_DUPLICATE,
                    "These codes already exist: " + String.join(", ", existing.stream().sorted().limit(10).toList()));
        }
        List<Resource> created = new ArrayList<>(count);
        for (String code : codes) {
            Resource r = new Resource(UUID.randomUUID(), tenantId, req.locationId(), code, displayFor(req, code), req.type());
            r.setSpaceId(req.spaceId());
            r.setDepartmentId(req.departmentId());
            created.add(r);
        }
        try {
            resources.saveAllAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceApiException(ErrorCode.CONFLICT_DUPLICATE, "One or more codes already exist");
        }
        return created.stream().map(r -> toResponse(r, null)).toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(UUID id) {
        Resource r = requireAccessible(id);
        ResourceQr active = qrs.findActiveByResourceId(r.getId()).orElse(null);
        return toResponse(r, active);
    }

    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> list(UUID locationId, ResourceType type, ResourceStatus status,
                                               int page, int size, String sortField, boolean desc) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw bad("size must be between 1 and " + MAX_PAGE_SIZE);
        if (!SORT_FIELDS.contains(sortField)) throw bad("Unsupported sort field");
        UUID tenantId = caller.tenantId();
        if (locationId != null && !caller.canAccessLocation(locationId)) {
            return PageResponse.from(Page.empty(PageRequest.of(page, size))); // other location: reveal nothing
        }
        Set<UUID> allowed = caller.allowedLocationIds();
        String jpaSortField = "code".equals(sortField) ? "resourceCode" : sortField;
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, jpaSortField).and(Sort.by("id")));
        Page<Resource> result = resources.search(tenantId, allowed, locationId, type, status, pageable);

        List<UUID> resourceIds = result.getContent().stream().map(Resource::getId).toList();
        Map<UUID, ResourceQr> active = resourceIds.isEmpty() ? Collections.emptyMap() :
                qrs.findByResourceIdInAndStatus(resourceIds, QrStatus.ACTIVE)
                .stream().collect(Collectors.toMap(ResourceQr::getResourceId, Function.identity()));
        return PageResponse.from(result.map(r -> toResponse(r, active.get(r.getId()))));
    }

    /** Shared with QrService: tenant filter + location scope; anything else is a 404, never a 403. */
    Resource requireAccessible(UUID id) {
        Resource r = resources.findByIdAndTenantId(id, caller.tenantId()).orElseThrow(ResourceService::notFound);
        if (!caller.canAccessLocation(r.getLocationId())) throw notFound();
        return r;
    }

    Resource requireAccessibleForUpdate(UUID id) {
        Resource r = resources.lockByIdAndTenantId(id, caller.tenantId()).orElseThrow(ResourceService::notFound);
        if (!caller.canAccessLocation(r.getLocationId())) throw notFound();
        return r;
    }

    private void validateAssociations(UUID tenantId, UUID locationId, UUID spaceId, UUID departmentId) {
        if (!caller.canAccessLocation(locationId) || !venue.locationExists(tenantId, locationId)) throw notFound();
        if (spaceId != null && !venue.spaceBelongsToLocation(tenantId, spaceId, locationId))
            throw bad("Space does not belong to the selected location");
        if (departmentId != null && !venue.departmentBelongsToLocation(tenantId, departmentId, locationId))
            throw bad("Department does not belong to the selected location");
    }

    private String validateCapabilities(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        try {
            JsonNode n = json.readTree(raw);
            if (!n.isObject() || n.size() > MAX_CAPABILITY_KEYS) throw bad("capabilities must be a JSON object with at most " + MAX_CAPABILITY_KEYS + " keys");
            return json.writeValueAsString(n);
        } catch (ResourceApiException e) {
            throw e;
        } catch (Exception e) {
            throw bad("capabilities is not valid JSON");
        }
    }

    private static String displayFor(BulkCreateRequest req, String code) {
        String label = req.type().name().charAt(0) + req.type().name().substring(1).toLowerCase();
        return label + " " + code;
    }

    static String normaliseCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    static ResourceResponse toResponse(Resource r, ResourceQr active) {
        return new ResourceResponse(r.getId(), r.getResourceCode(), r.getDisplayName(), r.getType(), r.getLocationId(),
                r.getSpaceId(), r.getDepartmentId(), r.getStatus(), r.getActivationPolicy(),
                active == null ? null : QrStatus.ACTIVE, active == null ? null : active.getQrVersion());
    }

    static ResourceApiException notFound() { return new ResourceApiException(ErrorCode.RESOURCE_NOT_FOUND, "Not found"); }
    private static ResourceApiException bad(String m) { return new ResourceApiException(ErrorCode.VALIDATION_FAILED, m); }
    private static ResourceApiException duplicate(String code) {
        return new ResourceApiException(ErrorCode.CONFLICT_DUPLICATE, "Resource code already exists in this location");
    }
}
