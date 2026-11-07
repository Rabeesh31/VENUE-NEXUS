package com.venuenexus.security.authz;

import java.util.Optional;
import java.util.UUID;

/**
 * Service-level enforcer for hierarchical operational scopes (Organisation / Location).
 *
 * <p><strong>CRITICAL USAGE RULE:</strong>
 * Every tenant-owned entity query MUST first be tenant-filtered in the database repository.
 * {@code ScopeEnforcer} is subsequently called to verify whether the authenticated staff member
 * has clearance to read or mutate the specific organisation and physical location of that record.
 *
 * <p>Out-of-scope access attempts must appear as non-existent (yielding 404) to avoid leaking
 * metadata about other locations or departments.
 */
public interface ScopeEnforcer {

    /**
     * Asserts that the current caller has permission to access a record belonging to the
     * specified organisation and location.
     *
     * @param recordOrganisationId the record's organisation ID (may be null if unassigned)
     * @param recordLocationId the record's location ID (may be null if unassigned)
     * @throws com.venuenexus.common.error.ApiException (e.g. RESOURCE_NOT_FOUND) if access is denied.
     */
    void assertCanAccess(UUID recordOrganisationId, UUID recordLocationId);

    /**
     * Non-throwing check to see if the current caller can access a record in the specified scope.
     *
     * @param recordOrganisationId the record's organisation ID
     * @param recordLocationId the record's location ID
     * @return true if access is permitted, false otherwise.
     */
    boolean canAccess(UUID recordOrganisationId, UUID recordLocationId);

    /**
     * Returns the location ID that the caller is restricted to, or empty if the caller is
     * permitted across all locations in their tenant/organisation.
     *
     * @return optional restricted location ID.
     */
    Optional<UUID> restrictedLocationId();

    /**
     * Returns the organisation ID that the caller is restricted to, or empty if the caller
     * has tenant-wide access.
     *
     * @return optional restricted organisation ID.
     */
    Optional<UUID> restrictedOrganisationId();
}

