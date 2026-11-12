package com.venuenexus.venue.repository;

import com.venuenexus.venue.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for root Tenant management (Platform Admin scope).
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByName(String name);

    Page<Tenant> findAllByStatus(String status, Pageable pageable);
}
