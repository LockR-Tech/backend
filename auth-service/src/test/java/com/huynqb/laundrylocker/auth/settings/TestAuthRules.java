package com.huynqb.laundrylocker.auth.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc auth-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestAuthRules {

    private TestAuthRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("auth", new AuthSettingsCatalog(), overrides);
    }

    public static AuthRules of(Map<String, ?> overrides) {
        return new AuthRules(settings(overrides));
    }

    public static AuthRules defaults() {
        return of(Map.of());
    }
}
