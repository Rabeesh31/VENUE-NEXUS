package com.venuenexus.security.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service managing Micrometer metric counters for authentication, lockout, token rotation,
 * and rejection monitoring.
 */
@Service
public class AuthMetricsService {

    private final MeterRegistry meterRegistry;

    @Autowired
    public AuthMetricsService(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.orElseGet(SimpleMeterRegistry::new);
    }

    public void recordLogin(String outcome) {
        Counter.builder("auth.login")
                .tag("outcome", outcome)
                .description("Staff login attempts by outcome")
                .register(meterRegistry)
                .increment();
    }

    public void recordLockout() {
        Counter.builder("auth.lockout")
                .description("Staff account lockouts triggered")
                .register(meterRegistry)
                .increment();
    }

    public void recordRefresh(String outcome) {
        Counter.builder("auth.refresh")
                .tag("outcome", outcome)
                .description("Refresh token rotation attempts by outcome")
                .register(meterRegistry)
                .increment();
    }

    public void recordTokenRejected(String reason) {
        Counter.builder("auth.token.rejected")
                .tag("reason", reason)
                .description("Rejected staff tokens by reason")
                .register(meterRegistry)
                .increment();
    }
}
