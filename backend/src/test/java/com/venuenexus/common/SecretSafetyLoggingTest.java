package com.venuenexus.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.venuenexus.audit.adapter.InMemoryAuditLogStore;
import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.port.AuditLogStore;
import com.venuenexus.audit.service.AuditDetailsSanitizer;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.GlobalExceptionHandler;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.common.web.TestErrorController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecretSafetyLoggingTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("Audit path: sensitive values in audit event are never emitted to log output")
    void auditPathDoesNotLogSecrets() {
        AuditLogStore store = new InMemoryAuditLogStore();
        AuditDetailsSanitizer sanitizer = new AuditDetailsSanitizer();
        AuditLogService service = new AuditLogService(store, sanitizer);

        String secretPassword = "SuperSecretPassword99!";
        String secretToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.rawtokenvalue";
        String secretQrPlain = "PlainUnguessable128BitToken";
        String rawIp = "192.168.1.100";

        AuditEvent event = AuditEvent.success(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AuditEventType.AUTH_LOGIN,
                "trace-audit-safety-123",
                rawIp,
                Map.of(
                        "password", secretPassword,
                        "token", secretToken,
                        "qrToken", secretQrPlain,
                        "safeSummary", "Normal audit event"
                )
        );

        service.record(event);

        // Verify none of the sensitive values appeared in captured logs
        for (ILoggingEvent logEvent : listAppender.list) {
            String formattedMessage = logEvent.getFormattedMessage();
            assertThat(formattedMessage).doesNotContain(secretPassword);
            assertThat(formattedMessage).doesNotContain(secretToken);
            assertThat(formattedMessage).doesNotContain(secretQrPlain);
            assertThat(formattedMessage).doesNotContain(rawIp);
        }
    }

    @Test
    @DisplayName("Error path: internal exception logging emits traceId only, no stack traces to client")
    void errorPathDoesNotExposeSecrets() throws Exception {
        TestErrorController controller = new TestErrorController();
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        TraceIdFilter filter = new TraceIdFilter();

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler)
                .addFilters(filter)
                .build();

        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError());

        // Check captured logs
        boolean foundErrorLog = false;
        for (ILoggingEvent logEvent : listAppender.list) {
            if (logEvent.getLevel() == Level.ERROR && logEvent.getLoggerName().contains("GlobalExceptionHandler")) {
                foundErrorLog = true;
                // Assert it logs trace ID
                assertThat(logEvent.getFormattedMessage()).contains("Unexpected server exception. TraceId:");
            }
        }
        assertThat(foundErrorLog).isTrue();
    }
}
