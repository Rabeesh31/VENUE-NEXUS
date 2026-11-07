package com.venuenexus.identity.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private AuthMetricsService authMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        authMetricsService = new AuthMetricsService(Optional.of(meterRegistry));
    }

    @Test
    void testRecordLogin_IncrementsCounterWithOutcomeTag() {
        authMetricsService.recordLogin("success");
        authMetricsService.recordLogin("success");
        authMetricsService.recordLogin("invalid_credentials");

        double successCount = meterRegistry.get("auth.login").tag("outcome", "success").counter().count();
        double failureCount = meterRegistry.get("auth.login").tag("outcome", "invalid_credentials").counter().count();

        assertThat(successCount).isEqualTo(2.0);
        assertThat(failureCount).isEqualTo(1.0);
    }

    @Test
    void testRecordLockout_IncrementsCounter() {
        authMetricsService.recordLockout();
        authMetricsService.recordLockout();

        double count = meterRegistry.get("auth.lockout").counter().count();
        assertThat(count).isEqualTo(2.0);
    }

    @Test
    void testRecordRefresh_IncrementsCounterWithOutcomeTag() {
        authMetricsService.recordRefresh("success");
        authMetricsService.recordRefresh("reuse_detected");

        double successCount = meterRegistry.get("auth.refresh").tag("outcome", "success").counter().count();
        double reuseCount = meterRegistry.get("auth.refresh").tag("outcome", "reuse_detected").counter().count();

        assertThat(successCount).isEqualTo(1.0);
        assertThat(reuseCount).isEqualTo(1.0);
    }

    @Test
    void testRecordTokenRejected_IncrementsCounterWithReasonTag() {
        authMetricsService.recordTokenRejected("AUTH_TOKEN_EXPIRED");
        authMetricsService.recordTokenRejected("AUTH_TOKEN_REVOKED");

        double expiredCount = meterRegistry.get("auth.token.rejected").tag("reason", "AUTH_TOKEN_EXPIRED").counter().count();
        double revokedCount = meterRegistry.get("auth.token.rejected").tag("reason", "AUTH_TOKEN_REVOKED").counter().count();

        assertThat(expiredCount).isEqualTo(1.0);
        assertThat(revokedCount).isEqualTo(1.0);
    }
}

