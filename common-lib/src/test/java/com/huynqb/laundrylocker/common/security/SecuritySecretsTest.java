package com.huynqb.laundrylocker.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecuritySecretsTest {

    private static final String STRONG_DEV_SECRET =
            "laundry-locker-dev-secret-change-me-32chars";
    private static final String STRONG_PROD_SECRET =
            "prod-secret-value-with-enough-entropy-2026";

    @Test
    void hmacShaKeyForRejectsBlankSecret() {
        assertThrows(
                IllegalStateException.class,
                () -> SecuritySecrets.hmacShaKeyFor(" ", "app.security.jwt.secret"));
    }

    @Test
    void hmacShaKeyForRejectsShortSecret() {
        assertThrows(
                IllegalStateException.class,
                () -> SecuritySecrets.hmacShaKeyFor("short-secret", "app.security.jwt.secret"));
    }

    @Test
    void hmacShaKeyForAllowsDevelopmentDefaultOutsideProductionProfile() {
        assertDoesNotThrow(
                () -> SecuritySecrets.hmacShaKeyFor(STRONG_DEV_SECRET, "app.security.jwt.secret", "dev"));
    }

    @Test
    void hmacShaKeyForRejectsDevelopmentDefaultInProductionProfile() {
        assertThrows(
                IllegalStateException.class,
                () -> SecuritySecrets.hmacShaKeyFor(STRONG_DEV_SECRET, "app.security.jwt.secret", "prod"));
    }

    @Test
    void requireProductionSafeValueRejectsSandboxUrlInProductionProfile() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        SecuritySecrets.requireProductionSafeValue(
                                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                                "vnpay.pay-url",
                                "production"));
    }

    @Test
    void requireProductionSafeValueAllowsExplicitProductionValue() {
        assertDoesNotThrow(
                () ->
                        SecuritySecrets.requireProductionSafeValue(
                                STRONG_PROD_SECRET, "vnpay.hash-secret", "production"));
    }
}
