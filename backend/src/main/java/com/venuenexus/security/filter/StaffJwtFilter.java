package com.venuenexus.security.filter;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.context.ScopeContext;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.security.denylist.TokenDenylist;
import com.venuenexus.security.handler.JsonAuthenticationEntryPoint;
import com.venuenexus.security.jwt.StaffJwtService;
import com.venuenexus.security.jwt.StaffTokenClaims;
import com.venuenexus.security.metrics.AuthMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Filter that validates incoming staff JWT bearer tokens, checks token revocation,
 * establishes Spring Security authorities ({@code ROLE_<ROLE>}), and populates
 * {@link TenantContext}, {@link ScopeContext}, and {@link CurrentStaff}.
 *
 * <p><strong>CRITICAL SECURITY INVARIANTS:</strong>
 * <ul>
 *   <li>Runs inside Spring Security filter chain before {@code UsernamePasswordAuthenticationFilter}.</li>
 *   <li>Runs after {@link TraceIdFilter} so rejections always include a valid trace ID.</li>
 *   <li>Guarantees strict cleanup of all ThreadLocal contexts in a {@code finally} block.</li>
 *   <li>Tokens are NEVER logged.</li>
 * </ul>
 */
public class StaffJwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(StaffJwtFilter.class);
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final StaffJwtService staffJwtService;
    private final TokenDenylist tokenDenylist;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final AuthMetricsService authMetricsService;
    private final AuditLogService auditLogService;

    public StaffJwtFilter(
            StaffJwtService staffJwtService,
            TokenDenylist tokenDenylist,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            AuthMetricsService authMetricsService,
            AuditLogService auditLogService) {
        this.staffJwtService = staffJwtService;
        this.tokenDenylist = tokenDenylist;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.authMetricsService = authMetricsService;
        this.auditLogService = auditLogService;
    }

    public StaffJwtFilter(
            StaffJwtService staffJwtService,
            TokenDenylist tokenDenylist,
            JsonAuthenticationEntryPoint authenticationEntryPoint) {
        this(staffJwtService, tokenDenylist, authenticationEntryPoint, null, null);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                clearAllContexts();
            }
            return;
        }

        String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();

        StaffTokenClaims claims = null;
        try {
            // 1. Cryptographically parse and validate token claims and algorithm pinning
            claims = staffJwtService.parseAndValidate(rawToken);

            // 2. Denylist check in Redis (fails closed on Redis outage)
            if (tokenDenylist.isDenylisted(claims.jti())) {
                throw new ApiException(ErrorCode.AUTH_TOKEN_REVOKED);
            }

            // 3. Establish Spring Security Authentication
            var authority = new SimpleGrantedAuthority("ROLE_" + claims.rol().name());
            var authToken = new UsernamePasswordAuthenticationToken(claims.uid(), null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // 4. Populate ThreadLocal security contexts
            TenantContext.setTenantId(claims.tid());
            ScopeContext.set(new ScopeContext(claims.rol(), claims.tid(), claims.oid(), claims.lid(), claims.did()));
            CurrentStaff.set(new CurrentStaff(claims.uid(), claims.rol(), claims.tid(), claims.oid(), claims.lid(), claims.did(), claims.jti()));

            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            log.warn("Staff JWT validation failed [traceId={}, error={}]: {}",
                    TraceIdFilter.getCurrentTraceId(), ex.getErrorCode().name(), ex.getMessage());

            if (authMetricsService != null) {
                authMetricsService.recordTokenRejected(ex.getErrorCode().name());
            }

            if (auditLogService != null && claims != null && claims.tid() != null) {
                auditLogService.record(AuditEvent.failure(
                        claims.tid(),
                        claims.uid(),
                        AuditEventType.AUTH_LOGIN_FAILED,
                        TraceIdFilter.getCurrentTraceId(),
                        "127.0.0.1",
                        Map.of(
                                "action", "TOKEN_REJECTED",
                                "reason", ex.getErrorCode().name()
                        )
                ));
            }

            request.setAttribute(JsonAuthenticationEntryPoint.AUTH_EXCEPTION_ATTR, ex);
            authenticationEntryPoint.commence(request, response, new InsufficientAuthenticationException(ex.getMessage(), ex));
        } finally {
            clearAllContexts();
        }
    }

    private void clearAllContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        ScopeContext.clear();
        CurrentStaff.clear();
    }
}
