package com.huynqb.laundrylocker.iot.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc iot-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestIotRules {

    private TestIotRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("iot", new IotSettingsCatalog(), overrides);
    }

    public static IotRules of(Map<String, ?> overrides) {
        return new IotRules(settings(overrides));
    }

    public static IotRules defaults() {
        return of(Map.of());
    }
}
