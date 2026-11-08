package com.venuenexus.resource.service;

import com.venuenexus.resource.domain.*;
import com.venuenexus.resource.dto.Dtos.*;
import com.venuenexus.resource.port.Ports.*;
import com.venuenexus.resource.qr.QrRenderer;
import com.venuenexus.resource.qr.QrTokenService;
import com.venuenexus.resource.repo.ResourceQrRepository;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR lifecycle. The plain token exists only as a local variable inside issue()/reissue() and in the
 * response. It is never stored, logged, or put in audit detail. Because it is not stored, the image
 * cannot be re-rendered later: losing it means reissue.
 */
@Service
public class QrService {

    private final ResourceQrRepository qrs;
    private final ResourceService resourceService;
    private final QrTokenService tokens;
    private final QrRenderer renderer;
    private final CallerContext caller;
    private final AuditPort audit;
    private final String publicBaseUrl;
    private final String pathPrefix;

    public QrService(ResourceQrRepository qrs, ResourceService resourceService, QrTokenService tokens,
                     QrRenderer renderer, CallerContext caller, AuditPort audit,
                     @Value("${PUBLIC_BASE_URL:http://localhost:8080}") String publicBaseUrl,
                     @Value("${venuenexus.qr.path-prefix:/q/}") String pathPrefix) {
        this.qrs = qrs;
        this.resourceService = resourceService;
        this.tokens = tokens;
        this.renderer = renderer;
        this.caller = caller;
        this.audit = audit;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.pathPrefix = pathPrefix;
    }

    /** First QR for a resource. 409 if one is already active (use reissue to replace it). */
    @Transactional
    public QrIssueResponse issue(UUID resourceId) {
        Resource r = resourceService.requireAccessibleForUpdate(resourceId); // row lock serialises concurrent calls
        UUID tenantId = r.getTenantId();
        if (qrs.findActiveByResourceId(r.getId()).isPresent()) {
            throw new ResourceApiException(ErrorCode.CONFLICT_DUPLICATE, "This resource already has an active QR. Reissue it to replace it.");
        }
        QrIssueResponse out = createActive(r);
        audit.record("QR_ISSUED", tenantId, caller.userId(), detail(r.getId(), out.qrVersion()));
        return out;
    }

    @Transactional
    public QrStatusResponse revoke(UUID resourceId) {
        Resource r = resourceService.requireAccessibleForUpdate(resourceId);
        ResourceQr active = qrs.findActiveByResourceId(r.getId())
                .orElseThrow(() -> new ResourceApiException(ErrorCode.CONFLICT_DUPLICATE, "This resource has no active QR"));
        active.revoke("Staff revocation");
        qrs.saveAndFlush(active);
        audit.record("QR_REVOKED", r.getTenantId(), caller.userId(), detail(r.getId(), active.getQrVersion()));
        return new QrStatusResponse(r.getId(), QrStatus.REVOKED, active.getQrVersion(), active.getIssuedAt(), active.getRevokedAt());
    }

    /** Revoke old + issue new in ONE transaction. Supported by Phase 1 QR_REISSUED audit event. */
    @Transactional
    public QrIssueResponse reissue(UUID resourceId) {
        Resource r = resourceService.requireAccessibleForUpdate(resourceId);
        UUID tenantId = r.getTenantId();
        Optional<ResourceQr> old = qrs.findActiveByResourceId(r.getId());
        if (old.isPresent()) {
            ResourceQr q = old.get();
            q.revoke("Reissued");
            qrs.saveAndFlush(q); // flush BEFORE insert, or the partial unique index fires inside our own tx
        }
        QrIssueResponse out = createActive(r);
        audit.record("QR_REISSUED", tenantId, caller.userId(), detail(r.getId(), out.qrVersion()));
        return out;
    }

    @Transactional(readOnly = true)
    public QrStatusResponse status(UUID resourceId) {
        Resource r = resourceService.requireAccessible(resourceId);
        return qrs.findActiveByResourceId(r.getId())
                .map(q -> new QrStatusResponse(r.getId(), QrStatus.ACTIVE, q.getQrVersion(), q.getIssuedAt(), null))
                .orElseGet(() -> new QrStatusResponse(r.getId(), null, null, null, null));
    }

    /**
     * Internal resolve check (the public endpoint arrives in Sprint 2, with rate limiting).
     * Returns the identity of an ACTIVE QR only. Revoked, reissued-over, unknown and malformed tokens
     * all give empty, indistinguishably.
     */
    @Transactional(readOnly = true)
    public Optional<ValidatedQr> validate(String token) {
        if (!tokens.isWellFormed(token)) return Optional.empty();
        return qrs.findActiveByTokenHash(tokens.hash(token))
                .map(q -> new ValidatedQr(q.getResource() != null ? q.getResource().getTenantId() : null, q.getResourceId()));
    }

    public record ValidatedQr(UUID tenantId, UUID resourceId) {}

    private QrIssueResponse createActive(Resource r) {
        String token = tokens.generateToken();
        int version = qrs.maxVersion(r.getId()) + 1;
        ResourceQr q = new ResourceQr(UUID.randomUUID(), r.getId(), tokens.hash(token), version, caller.userId());
        try {
            qrs.saveAndFlush(q);
        } catch (DataIntegrityViolationException e) {
            // Partial unique index: another active QR slipped in. Report cleanly, never half-succeed.
            throw new ResourceApiException(ErrorCode.CONFLICT_DUPLICATE, "This resource already has an active QR");
        }
        String url = publicBaseUrl + pathPrefix + token;
        String svg = renderer.svg(url);
        String png = Base64.getEncoder().encodeToString(renderer.png(url));
        return new QrIssueResponse(r.getId(), version, url, svg, png, q.getIssuedAt());
    }

    private static Map<String, String> detail(UUID resourceId, int version) {
        return Map.of("resourceId", resourceId.toString(), "qrVersion", Integer.toString(version));
    }
}
