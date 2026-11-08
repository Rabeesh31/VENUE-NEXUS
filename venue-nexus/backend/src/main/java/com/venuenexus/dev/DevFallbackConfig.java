package com.venuenexus.dev;

import com.venuenexus.resource.port.Ports.AuditPort;
import com.venuenexus.resource.port.Ports.CallerContext;
import com.venuenexus.resource.port.Ports.VenueLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;
import java.util.UUID;

/**
 * Isolated development-and-test-only fallback configuration.
 * Strictly active ONLY under "dev" and "test" profiles.
 * Never active in production environments.
 *
 * All fallback beans, including fallback security, are annotated with @ConditionalOnMissingBean,
 * guaranteeing they automatically back off whenever Phase 1, 3, or 4 provide real implementations.
 */
@Configuration
@Profile({"dev", "test"})
@EnableWebSecurity
@EnableMethodSecurity
public class DevFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(DevFallbackConfig.class);

    /** Dedicated default location for development testing. */
    public static final UUID DEV_DEFAULT_LOCATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID DEV_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID DEV_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Bean
    @ConditionalOnMissingBean
    public CallerContext callerContext() {
        log.warn("DEVELOPMENT ONLY: Activating dev fallback CallerContext bean with explicit non-null DEV_DEFAULT_LOCATION_ID.");
        return new CallerContext() {
            private final Set<UUID> allowedLocations = Set.of(DEV_DEFAULT_LOCATION_ID);

            @Override
            public UUID tenantId() {
                return DEV_TENANT_ID;
            }

            @Override
            public UUID userId() {
                return DEV_USER_ID;
            }

            @Override
            public Set<UUID> allowedLocationIds() {
                return allowedLocations;
            }

            @Override
            public boolean canAccessLocation(UUID locationId) {
                return locationId != null && allowedLocations.contains(locationId);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public VenueLookup venueLookup() {
        log.warn("DEVELOPMENT ONLY: Activating dev fallback VenueLookup bean.");
        return new VenueLookup() {
            @Override
            public boolean locationExists(UUID tenantId, UUID locationId) {
                return DEV_TENANT_ID.equals(tenantId) && DEV_DEFAULT_LOCATION_ID.equals(locationId);
            }

            @Override
            public boolean spaceBelongsToLocation(UUID tenantId, UUID spaceId, UUID locationId) {
                return DEV_TENANT_ID.equals(tenantId) && DEV_DEFAULT_LOCATION_ID.equals(locationId);
            }

            @Override
            public boolean departmentBelongsToLocation(UUID tenantId, UUID departmentId, UUID locationId) {
                return DEV_TENANT_ID.equals(tenantId) && DEV_DEFAULT_LOCATION_ID.equals(locationId);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditPort auditPort() {
        log.warn("DEVELOPMENT ONLY: Activating dev fallback AuditPort bean.");
        Logger auditLog = LoggerFactory.getLogger("AUDIT");
        return (eventType, tenantId, actorUserId, detail) -> {
            auditLog.info("[AUDIT_LOG - DEV STUB] event={} tenantId={} actorId={} detail={}",
                    eventType, tenantId, actorUserId, detail);
        };
    }

    /**
     * Fallback security filter chain for standalone dev and test execution.
     * Backs off automatically whenever Phase 3's real SecurityConfig provides a SecurityFilterChain bean.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        log.warn("STANDALONE DEV/TEST: Activating fallback SecurityFilterChain. Backs off when Phase 3 SecurityConfig is present.");
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /**
     * Fallback in-memory user details for standalone dev and test execution.
     * Backs off automatically whenever a real UserDetailsService bean is present.
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService fallbackUserDetailsService() {
        log.warn("STANDALONE DEV/TEST: Activating fallback UserDetailsService. Backs off when real UserDetailsService is present.");
        UserDetails admin = User.builder()
            .username("admin")
            .password("{noop}admin123")
            .roles("TENANT_ADMIN")
            .build();

        UserDetails manager = User.builder()
            .username("manager")
            .password("{noop}manager123")
            .roles("VENUE_MANAGER")
            .build();

        UserDetails supervisor = User.builder()
            .username("supervisor")
            .password("{noop}supervisor123")
            .roles("SUPERVISOR")
            .build();

        UserDetails staff = User.builder()
            .username("staff")
            .password("{noop}staff123")
            .roles("STAFF")
            .build();

        return new InMemoryUserDetailsManager(admin, manager, supervisor, staff);
    }
}
