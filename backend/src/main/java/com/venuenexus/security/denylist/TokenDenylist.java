package com.venuenexus.security.denylist;

import java.time.Duration;

/**
 * Service managing access token denylisting for logout and immediate revocation.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * The denylist is backed by Redis with keys of the form {@code auth:denylist:jti:<jti>}.
 * If Redis becomes unreachable, operations MUST fail closed to prevent revoked tokens
 * from being accepted during an infrastructure outage.
 */
public interface TokenDenylist {

    /**
     * Adds a token's JTI to the denylist with the specified TTL (remaining token lifetime).
     *
     * @param jti the unique JWT ID
     * @param ttl the remaining lifetime of the access token
     */
    void denylist(String jti, Duration ttl);

    /**
     * Checks if a token's JTI is in the denylist.
     *
     * @param jti the unique JWT ID
     * @return true if the token is revoked, false otherwise
     */
    boolean isDenylisted(String jti);
}

