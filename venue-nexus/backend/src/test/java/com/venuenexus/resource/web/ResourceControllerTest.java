package com.venuenexus.resource.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.VenueNexusApplication;
import com.venuenexus.dev.DevFallbackConfig;
import com.venuenexus.resource.domain.ActivationPolicy;
import com.venuenexus.resource.domain.QrStatus;
import com.venuenexus.resource.domain.ResourceStatus;
import com.venuenexus.resource.domain.ResourceType;
import com.venuenexus.resource.dto.Dtos.*;
import com.venuenexus.resource.port.Ports.ResourceApiException;
import com.venuenexus.resource.service.QrService;
import com.venuenexus.resource.service.ResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResourceController.class)
@Import({VenueNexusApplication.class, DevFallbackConfig.class})
@ActiveProfiles("test")
class ResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ResourceService resourceService;

    @MockBean
    private QrService qrService;

    private final UUID resourceId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/resources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void createResourceSucceedsForTenantAdmin() throws Exception {
        var req = new CreateResourceRequest("T12", "Table 12", ResourceType.TABLE, locationId, null, null, ActivationPolicy.INSTANT, "{}");
        var res = new ResourceResponse(resourceId, "T12", "Table 12", ResourceType.TABLE, locationId, null, null, ResourceStatus.AVAILABLE, ActivationPolicy.INSTANT, null, null);

        when(resourceService.create(any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(resourceId.toString()))
                .andExpect(jsonPath("$.code").value("T12"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser(roles = "VENUE_MANAGER")
    void createResourceSucceedsForVenueManager() throws Exception {
        var req = new CreateResourceRequest("R101", "Room 101", ResourceType.ROOM, locationId, null, null, ActivationPolicy.INSTANT, null);
        var res = new ResourceResponse(resourceId, "R101", "Room 101", ResourceType.ROOM, locationId, null, null, ResourceStatus.AVAILABLE, ActivationPolicy.INSTANT, null, null);

        when(resourceService.create(any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("R101"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void createResourceForbiddenForStaffRole() throws Exception {
        var req = new CreateResourceRequest("T12", "Table 12", ResourceType.TABLE, locationId, null, null, null, null);

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void createResourceValidationFailsOnBlankCode() throws Exception {
        var req = new CreateResourceRequest("", "Table 12", ResourceType.TABLE, locationId, null, null, null, null);

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void createResourceValidationFailsOnInvalidCodePattern() throws Exception {
        var req = new CreateResourceRequest("-invalid_code", "Table 12", ResourceType.TABLE, locationId, null, null, null, null);

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void createResourceConflictReturns409WithFullErrorJson() throws Exception {
        var req = new CreateResourceRequest("T12", "Table 12", ResourceType.TABLE, locationId, null, null, null, null);
        when(resourceService.create(any())).thenThrow(new ResourceApiException(409, "CONFLICT_DUPLICATE", "A resource with code 'T12' already exists in this location"));

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .header("X-Trace-Id", "client-trace-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", "client-trace-12345678"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("A resource with code 'T12' already exists in this location"))
                .andExpect(jsonPath("$.path").value("/api/v1/resources"))
                .andExpect(jsonPath("$.traceId").value("client-trace-12345678"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void createResourceThrowsValidationFailedReturns400WithFullErrorJson() throws Exception {
        var req = new CreateResourceRequest("T12", "Table 12", ResourceType.TABLE, locationId, null, null, null, null);
        when(resourceService.create(any())).thenThrow(new ResourceApiException(400, "VALIDATION_FAILED", "Invalid resource parameters"));

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", notNullValue()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid resource parameters"))
                .andExpect(jsonPath("$.path").value("/api/v1/resources"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void bulkCreateSucceedsForTenantAdmin() throws Exception {
        var req = new BulkCreateRequest("T", 1, 3, 2, ResourceType.TABLE, locationId, null, null);
        var item1 = new ResourceResponse(UUID.randomUUID(), "T01", "Table T01", ResourceType.TABLE, locationId, null, null, ResourceStatus.AVAILABLE, ActivationPolicy.INSTANT, null, null);
        var item2 = new ResourceResponse(UUID.randomUUID(), "T02", "Table T02", ResourceType.TABLE, locationId, null, null, ResourceStatus.AVAILABLE, ActivationPolicy.INSTANT, null, null);

        when(resourceService.bulkCreate(any())).thenReturn(List.of(item1, item2));

        mockMvc.perform(post("/api/v1/resources/bulk")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("T01"))
                .andExpect(jsonPath("$[1].code").value("T02"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void listResourcesPermittedForSupervisor() throws Exception {
        var item = new ResourceResponse(resourceId, "T12", "Table 12", ResourceType.TABLE, locationId, null, null, ResourceStatus.AVAILABLE, ActivationPolicy.INSTANT, null, null);
        when(resourceService.list(any(), any(), any(), eq(0), eq(20), eq("code"), eq(false)))
                .thenReturn(PageResponse.of(List.of(item), 0, 20, 1));

        mockMvc.perform(get("/api/v1/resources")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "code")
                        .param("dir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(resourceId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getResourcePermittedForStaff() throws Exception {
        var res = new ResourceResponse(resourceId, "T12", "Table 12", ResourceType.TABLE, locationId, null, null, ResourceStatus.AVAILABLE, ActivationPolicy.INSTANT, QrStatus.ACTIVE, 1);
        when(resourceService.get(resourceId)).thenReturn(res);

        mockMvc.perform(get("/api/v1/resources/{id}", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resourceId.toString()))
                .andExpect(jsonPath("$.qrStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.qrVersion").value(1));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getResourceReturns404WhenNotFoundWithFullErrorJson() throws Exception {
        when(resourceService.get(resourceId)).thenThrow(new ResourceApiException(404, "RESOURCE_NOT_FOUND", "The requested resource could not be found."));

        mockMvc.perform(get("/api/v1/resources/{id}", resourceId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", notNullValue()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/resources/" + resourceId))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getResourceThrowsLocationScopeViolationReturns403WithFullErrorJson() throws Exception {
        when(resourceService.get(resourceId)).thenThrow(new ResourceApiException(403, "LOCATION_SCOPE_VIOLATION", "Access denied: user scope does not permit accessing this location."));

        mockMvc.perform(get("/api/v1/resources/{id}", resourceId))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Trace-Id", notNullValue()))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("LOCATION_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Access denied: user scope does not permit accessing this location."))
                .andExpect(jsonPath("$.path").value("/api/v1/resources/" + resourceId))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void unsafeIncomingTraceIdIsSanitizedWithFreshUuid() throws Exception {
        when(resourceService.get(resourceId)).thenThrow(new ResourceApiException(404, "RESOURCE_NOT_FOUND", "Not found"));

        mockMvc.perform(get("/api/v1/resources/{id}", resourceId)
                        .header("X-Trace-Id", "invalid@trace#id$"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", not("invalid@trace#id$")))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("^[0-9a-fA-F-]{36}$")));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getQrStatusPermittedForStaff() throws Exception {
        var statusResp = new QrStatusResponse(resourceId, QrStatus.ACTIVE, 1, Instant.now(), null);
        when(qrService.status(resourceId)).thenReturn(statusResp);

        mockMvc.perform(get("/api/v1/resources/{id}/qr", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.qrVersion").value(1));
    }

    @Test
    @WithMockUser(roles = "VENUE_MANAGER")
    void issueQrReturnsOneTimeResponseWithNoStoreCacheHeaders() throws Exception {
        var issueResp = new QrIssueResponse(resourceId, 1, "https://venue.test/q/AbC123Token", "<svg/>", "pngBase64Data", Instant.now());
        when(qrService.issue(resourceId)).thenReturn(issueResp);

        mockMvc.perform(post("/api/v1/resources/{id}/qr", resourceId)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.resourceId").value(resourceId.toString()))
                .andExpect(jsonPath("$.qrVersion").value(1))
                .andExpect(jsonPath("$.qrUrl").value("https://venue.test/q/AbC123Token"))
                .andExpect(jsonPath("$.svg").value("<svg/>"))
                .andExpect(jsonPath("$.pngBase64").value("pngBase64Data"));
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void reissueQrReturnsNewTokenWithNoStoreHeaders() throws Exception {
        var reissueResp = new QrIssueResponse(resourceId, 2, "https://venue.test/q/NewToken456", "<svg2/>", "pngBase64Data2", Instant.now());
        when(qrService.reissue(resourceId)).thenReturn(reissueResp);

        mockMvc.perform(post("/api/v1/resources/{id}/qr/reissue", resourceId)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.qrVersion").value(2))
                .andExpect(jsonPath("$.qrUrl").value("https://venue.test/q/NewToken456"));
    }

    @Test
    @WithMockUser(roles = "VENUE_MANAGER")
    void revokeQrReturnsStatusAndPermitsManager() throws Exception {
        var revokedResp = new QrStatusResponse(resourceId, QrStatus.REVOKED, 1, Instant.now().minusSeconds(60), Instant.now());
        when(qrService.revoke(resourceId)).thenReturn(revokedResp);

        mockMvc.perform(post("/api/v1/resources/{id}/qr/revoke", resourceId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.qrVersion").value(1));
    }

    @Test
    @WithMockUser(roles = "VENUE_MANAGER")
    void revokeQrWhenNotActiveReturns409WithFullErrorJson() throws Exception {
        when(qrService.revoke(resourceId)).thenThrow(new ResourceApiException(409, "CONFLICT_DUPLICATE", "No active QR"));

        mockMvc.perform(post("/api/v1/resources/{id}/qr/revoke", resourceId)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", notNullValue()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("No active QR"))
                .andExpect(jsonPath("$.path").value("/api/v1/resources/" + resourceId + "/qr/revoke"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @WithMockUser(roles = "VENUE_MANAGER")
    void revokeQrThrowsQrRevokedReturns410WithFullErrorJson() throws Exception {
        when(qrService.revoke(resourceId)).thenThrow(new ResourceApiException(410, "QR_REVOKED", "QR identity has been revoked and is no longer valid."));

        mockMvc.perform(post("/api/v1/resources/{id}/qr/revoke", resourceId)
                        .with(csrf()))
                .andExpect(status().isGone())
                .andExpect(header().string("X-Trace-Id", notNullValue()))
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.code").value("QR_REVOKED"))
                .andExpect(jsonPath("$.message").value("QR identity has been revoked and is no longer valid."))
                .andExpect(jsonPath("$.path").value("/api/v1/resources/" + resourceId + "/qr/revoke"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }
}
