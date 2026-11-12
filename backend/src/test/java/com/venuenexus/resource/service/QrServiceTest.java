package com.venuenexus.resource.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.venuenexus.resource.domain.*;
import com.venuenexus.resource.dto.Dtos.QrIssueResponse;
import com.venuenexus.resource.port.Ports.*;
import com.venuenexus.resource.qr.QrRenderer;
import com.venuenexus.resource.qr.QrTokenService;
import com.venuenexus.resource.repo.ResourceQrRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class QrServiceTest {
    UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), resId = UUID.randomUUID();
    ResourceQrRepository qrs = mock(ResourceQrRepository.class);
    ResourceService resourceService = mock(ResourceService.class);
    QrRenderer renderer = mock(QrRenderer.class);
    CallerContext caller = mock(CallerContext.class);
    AuditPort audit = mock(AuditPort.class);
    QrTokenService tokens = new QrTokenService();
    Resource resource = new Resource(resId, tenant, UUID.randomUUID(), "T12", "Table 12", ResourceType.TABLE);
    QrService service;

    @BeforeEach void setUp() {
        when(caller.userId()).thenReturn(user);
        when(resourceService.requireAccessibleForUpdate(resId)).thenReturn(resource);
        when(renderer.svg(anyString())).thenReturn("<svg/>");
        when(renderer.png(anyString())).thenReturn(new byte[]{1});
        when(qrs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        service = new QrService(qrs, resourceService, tokens, renderer, caller, audit, "https://venue.test", "/q/");
    }

    @Test void issueReturnsTokenOnceAndStoresOnlyHash() {
        when(qrs.findActiveByResourceId(resId)).thenReturn(Optional.empty());
        QrIssueResponse out = service.issue(resId);

        String token = out.qrUrl().substring("https://venue.test/q/".length());
        assertEquals(22, token.length());
        ArgumentCaptor<ResourceQr> saved = ArgumentCaptor.forClass(ResourceQr.class);
        verify(qrs).saveAndFlush(saved.capture());
        assertEquals(1, saved.getValue().getQrVersion());
        assertFalse(out.toString().contains(token), "toString must not leak the token");
    }

    @Test void issueWhenActiveExistsIsConflict() {
        when(qrs.findActiveByResourceId(resId))
                .thenReturn(Optional.of(new ResourceQr(UUID.randomUUID(), resId, "h", 1, user)));
        var ex = assertThrows(ResourceApiException.class, () -> service.issue(resId));
        assertEquals("CONFLICT_DUPLICATE", ex.code());
        verify(qrs, never()).saveAndFlush(any());
    }

    @Test void reissueRevokesAndFlushesBeforeInsertingNewOne() {
        ResourceQr old = new ResourceQr(UUID.randomUUID(), resId, "oldhash", 1, user);
        when(qrs.findActiveByResourceId(resId)).thenReturn(Optional.of(old));
        when(qrs.maxVersion(resId)).thenReturn(1);

        QrIssueResponse out = service.reissue(resId);

        assertEquals(QrStatus.REVOKED, old.getStatus());
        assertEquals(2, out.qrVersion());
        InOrder order = inOrder(qrs);
        order.verify(qrs).saveAndFlush(old);             // revoke flushed first
        order.verify(qrs).saveAndFlush(argThat(q -> q != old)); // then the new row

        verify(audit).record(eq("QR_REISSUED"), eq(tenant), eq(user), any());
    }

    @Test void revokeWithNoActiveQrIsConflict() {
        when(qrs.findActiveByResourceId(resId)).thenReturn(Optional.empty());
        assertEquals("CONFLICT_DUPLICATE", assertThrows(ResourceApiException.class, () -> service.revoke(resId)).code());
    }

    @Test void revokeWritesAuditWithoutTokenOrHash() {
        ResourceQr active = new ResourceQr(UUID.randomUUID(), resId, "secrethash", 3, user);
        when(qrs.findActiveByResourceId(resId)).thenReturn(Optional.of(active));
        service.revoke(resId);

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> detail = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq("QR_REVOKED"), eq(tenant), eq(user), detail.capture());
        assertEquals(Set.of("resourceId", "qrVersion"), detail.getValue().keySet());
        assertFalse(detail.getValue().values().toString().contains("secrethash"));
    }

    @Test void validateAcceptsOnlyActiveTokens() {
        String token = tokens.generateToken();
        ResourceQr active = new ResourceQr(UUID.randomUUID(), resId, tokens.hash(token), 1, user);
        when(qrs.findActiveByTokenHash(tokens.hash(token))).thenReturn(Optional.of(active));

        assertTrue(service.validate(token).isPresent());
        assertTrue(service.validate(tokens.generateToken()).isEmpty()); // unknown / revoked
        assertTrue(service.validate("not-a-token").isEmpty());          // malformed: no DB lookup
        verify(qrs, times(2)).findActiveByTokenHash(anyString());
    }
}
