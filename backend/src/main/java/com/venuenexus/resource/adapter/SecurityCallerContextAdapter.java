package com.venuenexus.resource.adapter;

import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.resource.port.Ports.CallerContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Production adapter linking Phase 5 CallerContext port to Phase 3 CurrentStaff security context.
 */
@Component
public class SecurityCallerContextAdapter implements CallerContext {

    @Override
    public UUID tenantId() {
        return CurrentStaff.current().map(CurrentStaff::tenantId).orElse(null);
    }

    @Override
    public UUID userId() {
        return CurrentStaff.current().map(CurrentStaff::userId).orElse(null);
    }

    @Override
    public Set<UUID> allowedLocationIds() {
        return CurrentStaff.current()
                .map(CurrentStaff::locationId)
                .map(Set::of)
                .orElse(null); // null means full tenant access (e.g., TENANT_ADMIN)
    }

    @Override
    public boolean canAccessLocation(UUID locationId) {
        if (locationId == null) {
            return false;
        }
        Optional<CurrentStaff> staffOpt = CurrentStaff.current();
        if (staffOpt.isEmpty()) {
            return false;
        }
        CurrentStaff staff = staffOpt.get();
        // If staff is tenant-wide (no specific location assigned, like TENANT_ADMIN)
        if (staff.locationId() == null) {
            return true;
        }
        return staff.locationId().equals(locationId);
    }
}
