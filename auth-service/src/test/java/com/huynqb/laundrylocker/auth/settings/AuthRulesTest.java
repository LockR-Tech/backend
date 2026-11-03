package com.huynqb.laundrylocker.auth.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthRulesTest {

    @Test
    void catalogDefinesSplashDurationAsPublicInteger() {
        AuthSettingsCatalog catalog = new AuthSettingsCatalog();
        SettingDefinition splashDef = catalog.definitions().stream()
                .filter(d -> AuthSettingsCatalog.SPLASH_DURATION_MS.equals(d.key()))
                .findFirst()
                .orElse(null);

        assertNotNull(splashDef, "SPLASH_DURATION_MS must be defined in catalog");
        assertTrue(splashDef.publicValue(), "SPLASH_DURATION_MS must be public for client consumption");
        assertEquals("1200", splashDef.defaultValue());
        assertEquals("300", splashDef.min());
        assertEquals("10000", splashDef.max());
        assertEquals("ms", splashDef.unit());
    }

    @Test
    void defaultSplashDurationReturns1200() {
        AuthRules rules = TestAuthRules.defaults();
        assertEquals(1200, rules.splashDurationMs());
    }

    @Test
    void overriddenSplashDurationReturnsCustomValue() {
        AuthRules rules = TestAuthRules.of(Map.of(AuthSettingsCatalog.SPLASH_DURATION_MS, 2500));
        assertEquals(2500, rules.splashDurationMs());
    }
}

