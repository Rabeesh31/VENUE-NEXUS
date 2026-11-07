package com.venuenexus.platform.repository;

import com.venuenexus.platform.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for IdempotencyRecord entities.
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndScope(String idempotencyKey, String scope);

    void deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
