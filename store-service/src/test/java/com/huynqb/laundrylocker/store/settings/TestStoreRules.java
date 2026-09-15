package com.huynqb.laundrylocker.store.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc store-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestStoreRules {

    private TestStoreRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("store", new StoreSettingsCatalog(), overrides);
    }

    public static StoreRules of(Map<String, ?> overrides) {
        return new StoreRules(settings(overrides));
    }

    public static StoreRules defaults() {
        return of(Map.of());
    }
}
