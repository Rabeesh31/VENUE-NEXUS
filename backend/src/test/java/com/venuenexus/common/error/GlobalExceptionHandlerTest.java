package com.venuenexus.common.error;

import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.common.web.TestErrorController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TestErrorController controller = new TestErrorController();
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        TraceIdFilter traceIdFilter = new TraceIdFilter();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler)
                .addFilters(traceIdFilter)
                .build();
    }

    @Test
    @DisplayName("ApiException maps to designated ErrorCode, status, path, and traceId")
    void handleApiException() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isConflict())
                .andExpect(header().exists(TraceIdFilter.TRACE_HEADER))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Duplicate resource code detected."))
                .andExpect(jsonPath("$.path").value("/test/api-exception"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("ApiException(AUTH_INVALID_CREDENTIALS) returns 401 and body matching contract example exactly")
    void handleAuthInvalidCredentialsMatchesContract() throws Exception {
        mockMvc.perform(get("/test/auth-invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(TraceIdFilter.TRACE_HEADER))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid login credentials"))
                .andExpect(jsonPath("$.path").value("/test/auth-invalid-credentials"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details", empty()));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 VALIDATION_FAILED with field details")
    void handleValidationFailure() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(TraceIdFilter.TRACE_HEADER))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.details[?(@.field == 'name')].issue").isNotEmpty());
    }

    @Test
    @DisplayName("HttpMessageNotReadableException returns 400 MALFORMED_REQUEST")
    void handleMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json-payload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Type mismatch on pagination parameter returns 400 PAGINATION_INVALID")
    void handleTypeMismatchOnPagination() throws Exception {
        mockMvc.perform(get("/test/type-mismatch").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PAGINATION_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    @DisplayName("Missing required query parameter returns 400 MALFORMED_REQUEST")
    void handleMissingParameter() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("requiredParam"));
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupported returns 405 METHOD_NOT_ALLOWED")
    void handleMethodNotSupported() throws Exception {
        mockMvc.perform(post("/test/api-exception"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupported returns 415 UNSUPPORTED_MEDIA_TYPE")
    void handleMediaTypeNotSupported() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<name>Test</name>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("AccessDeniedException returns 403 ACCESS_DENIED")
    void handleAccessDenied() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("AuthenticationException returns 401 AUTH_INVALID_CREDENTIALS")
    void handleAuthenticationException() throws Exception {
        mockMvc.perform(get("/test/auth-failure"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Unexpected internal exception returns 500 INTERNAL_ERROR with generic message and no stack trace")
    void handleUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists(TraceIdFilter.TRACE_HEADER))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An internal server error occurred."))
                // Assert no internal class names or stack trace properties leak
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
