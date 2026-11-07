package com.venuenexus.security;

import com.venuenexus.VenueNexusApplication;
import com.venuenexus.common.filter.TraceIdFilter;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = VenueNexusApplication.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private OpenAPI openAPI;

    @Test
    void testUnauthenticatedCallToProtectedRoute_Returns401Json_T53() throws Exception {
        mockMvc.perform(get("/api/v1/protected-data"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(TraceIdFilter.TRACE_HEADER))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("Authentication token is invalid or malformed."))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testUnlistedRoute_DeniedByDefault_T52() throws Exception {
        // Any request outside the permitAll whitelist is denied by default
        mockMvc.perform(get("/random-unlisted-endpoint"))
                .andExpect(status().isUnauthorized()); // Or 403 depending on auth state
    }

    @Test
    void testCorsPreflight_ExposesXTraceId_T63() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type,X-Trace-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Trace-Id"));
    }

    @Test
    void testCorsConfigurationSource_Settings() {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/auth/login");
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).contains("http://localhost:3000");
        assertThat(config.getExposedHeaders()).contains("X-Trace-Id");
        assertThat(config.getAllowedHeaders()).contains("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    void testOpenApiConfiguration_RegistersBearerAuth() {
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        var scheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme.getType().toString()).isEqualToIgnoringCase("HTTP");
        assertThat(scheme.getScheme()).isEqualToIgnoringCase("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }
}
