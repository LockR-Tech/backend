package com.huynqb.laundrylocker.locker.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc locker-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestLockerRules {

    private TestLockerRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("locker", new LockerSettingsCatalog(), overrides);
    }

    public static LockerRules of(Map<String, ?> overrides) {
        return new LockerRules(settings(overrides));
    }

    public static LockerRules defaults() {
        return of(Map.of());
    }
}
