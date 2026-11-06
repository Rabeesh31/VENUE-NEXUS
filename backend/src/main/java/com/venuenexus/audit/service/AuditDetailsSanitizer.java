package com.venuenexus.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Sanitizer ensuring no secrets, credentials, tokens, or PII leak into audit records.
 * Matches docs/security.md §7.
 */
@Component
public class AuditDetailsSanitizer {

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i).*(password|token|secret|authorization|auth_header|bearer|qr|otp|api_?key|credential|credit_?card|cvv).*"
    );

    private final ObjectMapper objectMapper;

    public AuditDetailsSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuditDetailsSanitizer() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sanitizes a map of details by stripping all sensitive keys recursively.
     */
    public Map<String, Object> sanitize(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> sanitized = new TreeMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey();
            if (key == null || isSensitiveKey(key)) {
                continue; // Strip key
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) nestedMap;
                sanitized.put(key, sanitize(casted));
            } else if (value instanceof Collection<?> collection) {
                sanitized.put(key, sanitizeCollection(collection));
            } else {
                sanitized.put(key, value);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    public String toJsonString(Map<String, Object> details) {
        Map<String, Object> sanitized = sanitize(details);
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public static String hashIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private boolean isSensitiveKey(String key) {
        return SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }

    private List<Object> sanitizeCollection(Collection<?> collection) {
        List<Object> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) nested;
                result.add(sanitize(casted));
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
