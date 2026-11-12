package com.venuenexus.identity.service;

import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for verifying staff passwords using BCrypt with timing equalisation.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * To prevent user enumeration attacks via response timing differences, this verifier
 * always executes a BCrypt hash comparison against a pre-computed dummy hash of identical
 * strength when a user email is not found in the database.
 */
@Component
public class PasswordVerifier {

    private final PasswordEncoder passwordEncoder;
    private String dummyHash;
    private final AtomicBoolean dummyHashInvoked = new AtomicBoolean(false);

    public PasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    void initDummyHash() {
        // Pre-computed dummy hash of identical bcrypt strength
        this.dummyHash = passwordEncoder.encode("dummy-timing-equalisation-secret-string");
    }

    /**
     * Verifies the presented raw password against the stored password hash.
     *
     * @param rawPassword the plaintext password from the user
     * @param storedHash the BCrypt hash from the database
     * @return true if the password matches, false otherwise
     */
    public boolean verify(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            verifyDummy(rawPassword);
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }

    /**
     * Executes timing equalisation by hashing against the pre-computed dummy hash.
     * Always returns false.
     *
     * @param rawPassword the plaintext password
     * @return false always
     */
    public boolean verifyDummy(String rawPassword) {
        dummyHashInvoked.set(true);
        passwordEncoder.matches(rawPassword != null ? rawPassword : "", dummyHash);
        return false;
    }

    /**
     * Test helper to verify if the dummy hash path was exercised.
     */
    public boolean wasDummyHashInvoked() {
        return dummyHashInvoked.get();
    }

    /**
     * Test helper to reset the invocation flag.
     */
    public void resetDummyHashInvoked() {
        dummyHashInvoked.set(false);
    }
}

