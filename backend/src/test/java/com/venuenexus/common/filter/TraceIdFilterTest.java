package com.venuenexus.common.filter;

import com.venuenexus.common.web.TestErrorController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TraceIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TestErrorController controller = new TestErrorController();
        TraceIdFilter filter = new TraceIdFilter();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .build();
    }

    @Test
    @DisplayName("Valid incoming X-Trace-Id is accepted and propagated")
    void validTraceIdAccepted() throws Exception {
        String customTraceId = "req-custom-trace-12345678";

        mockMvc.perform(get("/test/echo-trace")
                        .header(TraceIdFilter.TRACE_HEADER, customTraceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_HEADER, customTraceId))
                .andExpect(jsonPath("$.traceId").value(customTraceId));

        // Assert MDC is cleared after request
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Missing X-Trace-Id results in auto-generated UUID")
    void missingTraceIdGeneratesUuid() throws Exception {
        mockMvc.perform(get("/test/echo-trace"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_HEADER,
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")))
                .andExpect(jsonPath("$.traceId",
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Malicious X-Trace-Id with newline/CRLF is discarded and replaced with safe UUID")
    void maliciousTraceIdWithNewlineReplaced() throws Exception {
        String maliciousHeader = "trace-123\r\nInjected-Header: evil";

        mockMvc.perform(get("/test/echo-trace")
                        .header(TraceIdFilter.TRACE_HEADER, maliciousHeader))
                .andExpect(status().isOk())
                // Assert response header is NOT the malicious string but a safe UUID
                .andExpect(header().string(TraceIdFilter.TRACE_HEADER,
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Overly long X-Trace-Id (>64 chars) is discarded and replaced")
    void overlyLongTraceIdReplaced() throws Exception {
        String longHeader = "a".repeat(65);

        mockMvc.perform(get("/test/echo-trace")
                        .header(TraceIdFilter.TRACE_HEADER, longHeader))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_HEADER,
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("X-Trace-Id too short (<8 chars) is discarded and replaced")
    void shortTraceIdReplaced() throws Exception {
        String shortHeader = "abc";

        mockMvc.perform(get("/test/echo-trace")
                        .header(TraceIdFilter.TRACE_HEADER, shortHeader))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_HEADER,
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}
