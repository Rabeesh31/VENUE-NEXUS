package com.venuenexus.resource.qr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QrTokenServiceTest {
    private final QrTokenService s = new QrTokenService();

    @Test void tokenIs22CharBase64UrlWithoutPadding() {
        String t = s.generateToken();
        assertEquals(22, t.length());
        assertTrue(s.isWellFormed(t));
        assertFalse(t.contains("=") || t.contains("+") || t.contains("/"));
    }

    @Test void tenThousandTokensAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) seen.add(s.generateToken());
        assertEquals(10_000, seen.size());
    }

    @Test void hashMatchesKnownSha256Vector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", s.hash("abc"));
    }

    @Test void hashDoesNotContainToken() {
        String t = s.generateToken();
        assertEquals(64, s.hash(t).length());
        assertFalse(s.hash(t).contains(t));
    }

    @Test void malformedTokensAreRejected() {
        String t = s.generateToken();
        assertFalse(s.isWellFormed(null));
        assertFalse(s.isWellFormed(""));
        assertFalse(s.isWellFormed(t + "x"));
        assertFalse(s.isWellFormed(t.substring(0, 21) + "+"));
    }
}
