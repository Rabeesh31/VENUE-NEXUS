package com.venuenexus.common.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Base repository contract for tenant-owned entities.
 * Enforces multi-tenant isolation by requiring tenantId on all access methods.
 * Unscoped findById is intentionally omitted to avoid cross-tenant data leakage.
 */
@NoRepositoryBean
public interface TenantScopedRepository<T, ID> extends Repository<T, ID> {

    Optional<T> findByTenantIdAndId(UUID tenantId, ID id);

    boolean existsByTenantIdAndId(UUID tenantId, ID id);

    Page<T> findAllByTenantId(UUID tenantId, Pageable pageable);

    <S extends T> S save(S entity);

    void delete(T entity);
}
