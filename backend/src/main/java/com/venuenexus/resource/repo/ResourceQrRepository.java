package com.venuenexus.resource.repo;

import com.venuenexus.resource.domain.QrStatus;
import com.venuenexus.resource.domain.ResourceQr;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceQrRepository extends JpaRepository<ResourceQr, UUID> {

    @Override
    @Deprecated
    default Optional<ResourceQr> findById(UUID id) {
        throw new UnsupportedOperationException("Look up QR records via resource identity");
    }

    @Query("SELECT q FROM ResourceQr q WHERE q.resourceId = :resourceId AND q.status = :status")
    Optional<ResourceQr> findByResourceIdAndStatus(@Param("resourceId") UUID resourceId, @Param("status") QrStatus status);

    @Query("SELECT q FROM ResourceQr q WHERE q.resourceId = :resourceId AND q.status = com.venuenexus.resource.domain.QrStatus.ACTIVE")
    Optional<ResourceQr> findActiveByResourceId(@Param("resourceId") UUID resourceId);

    @Query("SELECT q FROM ResourceQr q WHERE q.resourceId IN :resourceIds AND q.status = :status")
    List<ResourceQr> findByResourceIdInAndStatus(@Param("resourceIds") Collection<UUID> resourceIds, @Param("status") QrStatus status);

    @Query("SELECT coalesce(max(q.qrVersion), 0) FROM ResourceQr q WHERE q.resourceId = :resourceId")
    int maxVersion(@Param("resourceId") UUID resourceId);

    /** Lookup used by validate(): hash only, ACTIVE only. */
    @Query("SELECT q FROM ResourceQr q LEFT JOIN FETCH q.resource WHERE q.tokenHash = :hash AND q.status = com.venuenexus.resource.domain.QrStatus.ACTIVE")
    Optional<ResourceQr> findActiveByTokenHash(@Param("hash") String hash);

    Optional<ResourceQr> findByTokenHash(String tokenHash);

    List<ResourceQr> findByResourceIdOrderByCreatedAtDesc(UUID resourceId);
}
