package com.huynqb.laundrylocker.order.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc order-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestOrderRules {

    private TestOrderRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("order", new OrderSettingsCatalog(), overrides);
    }

    public static OrderRules of(Map<String, ?> overrides) {
        return new OrderRules(settings(overrides));
    }

    public static OrderRules defaults() {
        return of(Map.of());
    }
}
