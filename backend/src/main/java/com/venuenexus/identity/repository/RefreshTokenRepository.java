package com.venuenexus.identity.repository;

import com.venuenexus.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken rotation and family tracking.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByTokenFamily(UUID tokenFamily);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.tokenFamily = :tokenFamily AND r.revokedAt IS NULL")
    int revokeEntireFamily(@Param("tokenFamily") UUID tokenFamily, @Param("revokedAt") OffsetDateTime revokedAt);
}
