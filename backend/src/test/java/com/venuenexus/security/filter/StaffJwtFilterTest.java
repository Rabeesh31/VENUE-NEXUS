package com.venuenexus.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.context.ScopeContext;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.security.denylist.TokenDenylist;
import com.venuenexus.security.handler.JsonAuthenticationEntryPoint;
import com.venuenexus.security.jwt.StaffJwtService;
import com.venuenexus.security.jwt.StaffTokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffJwtFilterTest {

    @Mock
    private StaffJwtService staffJwtService;

    @Mock
    private TokenDenylist tokenDenylist;

    @Mock
    private FilterChain filterChain;

    private JsonAuthenticationEntryPoint authenticationEntryPoint;
    private StaffJwtFilter staffJwtFilter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        authenticationEntryPoint = new JsonAuthenticationEntryPoint(objectMapper);
        staffJwtFilter = new StaffJwtFilter(staffJwtService, tokenDenylist, authenticationEntryPoint);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        ScopeContext.clear();
        CurrentStaff.clear();
        MDC.remove(TraceIdFilter.MDC_KEY);
    }

    @Test
    void testFilter_ValidToken_EstablishesContexts_AndClearsInFinally() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String jti = "test-jti";

        StaffTokenClaims claims = new StaffTokenClaims(
                userId, "STAFF", StaffRole.VENUE_MANAGER, tenantId, null, null, null,
                jti, "venue-nexus-staff", "venue-nexus", Instant.now(), Instant.now().plusSeconds(900)
        );

        when(staffJwtService.parseAndValidate("valid.jwt")).thenReturn(claims);
        when(tokenDenylist.isDenylisted(jti)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // During filter execution, check contexts inside filterChain.doFilter
        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("ROLE_VENUE_MANAGER"));
            assertThat(TenantContext.requireTenantId()).isEqualTo(tenantId);
            assertThat(ScopeContext.require().role()).isEqualTo(StaffRole.VENUE_MANAGER);
            assertThat(CurrentStaff.require().userId()).isEqualTo(userId);
            return null;
        }).when(filterChain).doFilter(any(), any());

        staffJwtFilter.doFilter(request, response, filterChain);

        // After filter completes, all contexts MUST be cleared
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.currentTenantId()).isEmpty();
        assertThat(ScopeContext.current()).isEmpty();
        assertThat(CurrentStaff.current()).isEmpty();
    }

    @Test
    void testFilter_DenylistedToken_Returns401AuthTokenRevoked_T27() throws ServletException, IOException {
        MDC.put(TraceIdFilter.MDC_KEY, "trace_denylist_test");

        StaffTokenClaims claims = new StaffTokenClaims(
                UUID.randomUUID(), "STAFF", StaffRole.STAFF, UUID.randomUUID(), null, null, null,
                "revoked-jti", "venue-nexus-staff", "venue-nexus", Instant.now(), Instant.now().plusSeconds(900)
        );

        when(staffJwtService.parseAndValidate("revoked.jwt")).thenReturn(claims);
        when(tokenDenylist.isDenylisted("revoked-jti")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/protected/resource");
        request.addHeader("Authorization", "Bearer revoked.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        staffJwtFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER)).isEqualTo("trace_denylist_test");
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_REVOKED");
        assertThat(response.getContentAsString()).contains("trace_denylist_test");

        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testFilter_ContextHygiene_TwoSequentialRequests_T61() throws ServletException, IOException {
        StaffTokenClaims claims1 = new StaffTokenClaims(
                UUID.randomUUID(), "STAFF", StaffRole.STAFF, UUID.randomUUID(), null, null, null,
                "jti-1", "venue-nexus-staff", "venue-nexus", Instant.now(), Instant.now().plusSeconds(900)
        );

        when(staffJwtService.parseAndValidate("user1.jwt")).thenReturn(claims1);
        when(tokenDenylist.isDenylisted("jti-1")).thenReturn(false);

        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.addHeader("Authorization", "Bearer user1.jwt");
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        staffJwtFilter.doFilter(req1, res1, filterChain);

        // Assert all cleaned up
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.currentTenantId()).isEmpty();

        // Second unauthenticated request on same thread
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            // Confirm previous context has NOT leaked
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(TenantContext.currentTenantId()).isEmpty();
            assertThat(CurrentStaff.current()).isEmpty();
            return null;
        }).when(filterChain).doFilter(req2, res2);

        staffJwtFilter.doFilter(req2, res2, filterChain);
    }

    @Test
    void testFilter_ExpiredToken_Returns401WithApiErrorShape_T62() throws ServletException, IOException {
        MDC.put(TraceIdFilter.MDC_KEY, "trace_error_shape_test");

        when(staffJwtService.parseAndValidate("expired.jwt"))
                .thenThrow(new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/protected");
        request.addHeader("Authorization", "Bearer expired.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        staffJwtFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER)).isEqualTo("trace_error_shape_test");

        // Verify JSON fields matching ApiError
        String json = response.getContentAsString();
        assertThat(json).contains("\"status\":401");
        assertThat(json).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
        assertThat(json).contains("\"message\":\"Authentication token has expired.\"");
        assertThat(json).contains("\"path\":\"/api/v1/auth/protected\"");
        assertThat(json).contains("\"traceId\":\"trace_error_shape_test\"");
    }
}
