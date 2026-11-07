package com.venuenexus.resource.repository;

import com.venuenexus.resource.entity.ResourceQr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ResourceQr tokens.
 */
@Repository
public interface ResourceQrRepository extends JpaRepository<ResourceQr, UUID> {

    Optional<ResourceQr> findByTokenHash(String tokenHash);

    @Query("SELECT q FROM ResourceQr q WHERE q.resourceId = :resourceId AND q.status = 'ACTIVE'")
    Optional<ResourceQr> findActiveByResourceId(@Param("resourceId") UUID resourceId);

    List<ResourceQr> findByResourceIdOrderByCreatedAtDesc(UUID resourceId);
}
