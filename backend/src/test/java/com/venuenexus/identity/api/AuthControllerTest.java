package com.venuenexus.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.error.GlobalExceptionHandler;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.identity.service.AuthService;
import com.venuenexus.security.StaffRole;
import com.venuenexus.VenueNexusApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = VenueNexusApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private com.venuenexus.identity.service.RefreshTokenService refreshTokenService;

    @MockBean
    private com.venuenexus.identity.service.LogoutService logoutService;

    @Test
    void testLogin_Success_Returns200WithTokensAndUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserSummaryDto userSummary = new UserSummaryDto(
                userId, "Admin User", StaffRole.TENANT_ADMIN, tenantId, null, null, null
        );
        LoginResponse response = LoginResponse.of("mock.jwt.token", "mock-opaque-refresh", 900, userSummary);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        LoginRequest request = new LoginRequest("admin@venuenexus.com", "SecretPass123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-opaque-refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.displayName").value("Admin User"))
                .andExpect(jsonPath("$.user.role").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.user.tenantId").value(tenantId.toString()));
    }

    @Test
    void testLogin_InvalidCredentials_Returns401WithApiError() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        LoginRequest request = new LoginRequest("user@venuenexus.com", "WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(TraceIdFilter.TRACE_HEADER))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage()))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testLogin_AccountLocked_Returns401WithApiError() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED));

        LoginRequest request = new LoginRequest("user@venuenexus.com", "AnyPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTH_ACCOUNT_LOCKED.getDefaultMessage()))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testLogin_BlankEmailOrPassword_Returns400ValidationError() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testRefresh_Success_Returns200WithNewTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSummaryDto userSummary = new UserSummaryDto(
                userId, "Admin", StaffRole.TENANT_ADMIN, UUID.randomUUID(), null, null, null
        );
        LoginResponse response = LoginResponse.of("new.jwt.access", "new-opaque-refresh", 900, userSummary);

        when(refreshTokenService.rotate("old-valid-refresh-token")).thenReturn(response);

        RefreshRequest request = new RefreshRequest("old-valid-refresh-token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.jwt.access"))
                .andExpect(jsonPath("$.refreshToken").value("new-opaque-refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void testRefresh_ReuseDetected_Returns401WithApiError() throws Exception {
        when(refreshTokenService.rotate("reused-token"))
                .thenThrow(new ApiException(ErrorCode.AUTH_REFRESH_REUSE_DETECTED));

        RefreshRequest request = new RefreshRequest("reused-token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSE_DETECTED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/refresh"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testRefresh_BlankToken_Returns400ValidationError() throws Exception {
        RefreshRequest request = new RefreshRequest("");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void testLogout_Success_Returns204NoContent() throws Exception {
        com.venuenexus.security.context.CurrentStaff staff = new com.venuenexus.security.context.CurrentStaff(
                UUID.randomUUID(), StaffRole.STAFF, UUID.randomUUID(), null, null, null, "jti-to-logout"
        );
        com.venuenexus.security.context.CurrentStaff.set(staff);

        try {
            LogoutRequest request = new LogoutRequest("valid-refresh-token");

            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());
        } finally {
            com.venuenexus.security.context.CurrentStaff.clear();
        }
    }
}
