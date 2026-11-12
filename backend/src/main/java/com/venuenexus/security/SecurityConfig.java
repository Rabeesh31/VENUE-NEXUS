package com.venuenexus.security;

import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.security.denylist.TokenDenylist;
import com.venuenexus.security.filter.StaffJwtFilter;
import com.venuenexus.security.handler.JsonAccessDeniedHandler;
import com.venuenexus.security.handler.JsonAuthenticationEntryPoint;
import com.venuenexus.security.jwt.StaffJwtService;
import com.venuenexus.security.metrics.AuthMetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Core Spring Security configuration for VENUE NEXUS.
 *
 * <p><strong>CRITICAL SECURITY INVARIANTS:</strong>
 * <ul>
 *   <li>Stateless session management (no HTTP sessions).</li>
 *   <li>CSRF disabled for token-based REST endpoints.</li>
 *   <li>Form login, HTTP basic, and default logout disabled.</li>
 *   <li>Strict deny-by-default: {@code anyRequest().denyAll()} as final authorization rule.</li>
 *   <li>CORS configured with explicit allowed origins, exposing {@code X-Trace-Id} header.</li>
 *   <li>JSON 401 and 403 error handlers producing consistent {@link com.venuenexus.common.error.ApiError} bodies.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.password.bcrypt-strength:12}")
    private int bcryptStrength = 12;

    @Value("${security.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Shared BCrypt password encoder for staff authentication and staff creation.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        int strength = (bcryptStrength >= 4 && bcryptStrength <= 31) ? bcryptStrength : 12;
        return new BCryptPasswordEncoder(strength);
    }

    /**
     * System clock bean for time-dependent security operations.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Trace-Id"));
        config.setExposedHeaders(List.of(TraceIdFilter.TRACE_HEADER));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public StaffJwtFilter staffJwtFilter(
            StaffJwtService staffJwtService,
            TokenDenylist tokenDenylist,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            AuthMetricsService authMetricsService,
            AuditLogService auditLogService) {
        return new StaffJwtFilter(staffJwtService, tokenDenylist, authenticationEntryPoint, authMetricsService, auditLogService);
    }

    @Bean
    public FilterRegistrationBean<StaffJwtFilter> staffJwtFilterRegistration(StaffJwtFilter filter) {
        FilterRegistrationBean<StaffJwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Disables auto-registration in default servlet container
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            StaffJwtFilter staffJwtFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/**", "/test/**").authenticated()
                        .anyRequest().denyAll() // Strict deny-by-default!
                )
                .addFilterBefore(staffJwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
