package com.huynqb.laundrylocker.common.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class SecuritySecrets {

    private static final int MIN_HMAC_SECRET_BYTES = 32;
    private static final String[] UNSAFE_PRODUCTION_MARKERS = {
            "change-me", "demo", "dev-secret", "default", "please", "localhost", "127.0.0.1", "sandbox"
    };

    private SecuritySecrets() {
    }

    public static SecretKey hmacShaKeyFor(
            String value, String propertyName, String... activeProfiles) {
        String secret = requireConfigured(value, propertyName);
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalStateException(
                    propertyName + " must be at least " + MIN_HMAC_SECRET_BYTES + " UTF-8 bytes");
        }
        rejectUnsafeProductionValue(secret, propertyName, activeProfiles);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public static String requireProductionSafeValue(
            String value, String propertyName, String... activeProfiles) {
        String configured = requireConfigured(value, propertyName);
        rejectUnsafeProductionValue(configured, propertyName, activeProfiles);
        return configured;
    }

    private static String requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        return value.trim();
    }

    private static void rejectUnsafeProductionValue(
            String value, String propertyName, String... activeProfiles) {
        if (!isProductionProfile(activeProfiles)) {
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String marker : UNSAFE_PRODUCTION_MARKERS) {
            if (normalized.contains(marker)) {
                throw new IllegalStateException(
                        propertyName + " uses an unsafe development/default value for a production profile");
            }
        }
    }

    private static boolean isProductionProfile(String... activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (profile == null) {
                continue;
            }
            String normalized = profile.toLowerCase(Locale.ROOT);
            if ("prod".equals(normalized) || "production".equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
