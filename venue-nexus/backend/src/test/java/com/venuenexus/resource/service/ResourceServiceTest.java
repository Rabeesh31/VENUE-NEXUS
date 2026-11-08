package com.venuenexus.resource.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.resource.domain.*;
import com.venuenexus.resource.dto.Dtos.*;
import com.venuenexus.resource.port.Ports.*;
import com.venuenexus.resource.repo.ResourceQrRepository;
import com.venuenexus.resource.repo.ResourceRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ResourceServiceTest {
    UUID tenant = UUID.randomUUID(), loc = UUID.randomUUID();
    ResourceRepository repo = mock(ResourceRepository.class);
    ResourceQrRepository qrs = mock(ResourceQrRepository.class);
    CallerContext caller = mock(CallerContext.class);
    VenueLookup venue = mock(VenueLookup.class);
    ResourceService service;

    @BeforeEach void setUp() {
        when(caller.tenantId()).thenReturn(tenant);
        when(caller.canAccessLocation(any())).thenReturn(true);
        when(venue.locationExists(tenant, loc)).thenReturn(true);
        service = new ResourceService(repo, qrs, caller, venue, new ObjectMapper());
    }

    private CreateResourceRequest req(UUID space, String caps) {
        return new CreateResourceRequest("t12", "Table 12", ResourceType.TABLE, loc, space, null, null, caps);
    }

    @Test void createStoresUpperCaseCodeAndDefaults() {
        ResourceResponse r = service.create(req(null, null));
        assertEquals("T12", r.code());
        assertEquals(ResourceStatus.AVAILABLE, r.status());
        assertEquals(ActivationPolicy.INSTANT, r.activationPolicy());
        assertNull(r.qrStatus());
    }

    @Test void spaceFromAnotherLocationIsRejected() {
        UUID space = UUID.randomUUID();
        when(venue.spaceBelongsToLocation(tenant, space, loc)).thenReturn(false);
        assertEquals(400, assertThrows(ResourceApiException.class, () -> service.create(req(space, null))).status());
    }

    @Test void locationOutsideCallerScopeIs404() {
        when(caller.canAccessLocation(loc)).thenReturn(false);
        assertEquals(404, assertThrows(ResourceApiException.class, () -> service.create(req(null, null))).status());
    }

    @Test void duplicateCodeIs409FromPreCheckAndFromDbRace() {
        when(repo.existsByTenantIdAndLocationIdAndResourceCodeIgnoreCase(tenant, loc, "T12")).thenReturn(true);
        assertEquals("CONFLICT_DUPLICATE", assertThrows(ResourceApiException.class, () -> service.create(req(null, null))).code());

        when(repo.existsByTenantIdAndLocationIdAndResourceCodeIgnoreCase(tenant, loc, "T12")).thenReturn(false);
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertEquals("CONFLICT_DUPLICATE", assertThrows(ResourceApiException.class, () -> service.create(req(null, null))).code());
    }

    @Test void capabilitiesMustBeASmallJsonObject() {
        assertThrows(ResourceApiException.class, () -> service.create(req(null, "[1,2]")));
        assertThrows(ResourceApiException.class, () -> service.create(req(null, "not json")));
        assertDoesNotThrow(() -> service.create(req(null, "{\"seats\":4}")));
    }

    @Test void getOfOtherTenantOrLocationIs404() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResourceApiException.class, () -> service.get(id)).status());
    }

    @Test void bulkRejectsBadRangesAndExistingCodes() {
        var inverted = new BulkCreateRequest("T", 5, 1, 0, ResourceType.TABLE, loc, null, null);
        assertEquals(400, assertThrows(ResourceApiException.class, () -> service.bulkCreate(inverted)).status());

        var tooMany = new BulkCreateRequest("T", 1, 101, 0, ResourceType.TABLE, loc, null, null);
        assertEquals(400, assertThrows(ResourceApiException.class, () -> service.bulkCreate(tooMany)).status());

        when(repo.findExistingCodes(eq(tenant), eq(loc), anyCollection())).thenReturn(List.of("T2"));
        var clash = new BulkCreateRequest("T", 1, 3, 0, ResourceType.TABLE, loc, null, null);
        assertEquals("CONFLICT_DUPLICATE", assertThrows(ResourceApiException.class, () -> service.bulkCreate(clash)).code());
        verify(repo, never()).saveAllAndFlush(any());
    }

    @Test void bulkCreatesPaddedCodes() {
        when(repo.findExistingCodes(any(), any(), anyCollection())).thenReturn(List.of());
        var out = service.bulkCreate(new BulkCreateRequest("T", 1, 3, 2, ResourceType.TABLE, loc, null, null));
        assertEquals(List.of("T01", "T02", "T03"), out.stream().map(ResourceResponse::code).toList());
    }

    @Test void listRejectsOversizedPageAndUnknownSortField() {
        assertEquals(400, assertThrows(ResourceApiException.class, () -> service.list(null, null, null, 0, 101, "code", false)).status());
        assertEquals(400, assertThrows(ResourceApiException.class, () -> service.list(null, null, null, 0, 20, "password", false)).status());
    }
}
