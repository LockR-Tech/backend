package com.huynqb.laundrylocker.loyalty.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc loyalty-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestLoyaltyRules {

    private TestLoyaltyRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("loyalty", new LoyaltySettingsCatalog(), overrides);
    }

    public static LoyaltyRules of(Map<String, ?> overrides) {
        return new LoyaltyRules(settings(overrides));
    }

    public static LoyaltyRules defaults() {
        return of(Map.of());
    }
}
