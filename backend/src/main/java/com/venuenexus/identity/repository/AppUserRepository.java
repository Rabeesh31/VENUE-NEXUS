package com.venuenexus.identity.repository;

import com.venuenexus.identity.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AppUser management.
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query("SELECT u FROM AppUser u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<AppUser> findByEmailIgnoreCase(@Param("email") String email);

    Optional<AppUser> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<AppUser> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<AppUser> findByTenantIdAndRole(UUID tenantId, String role, Pageable pageable);

    Page<AppUser> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);
}
