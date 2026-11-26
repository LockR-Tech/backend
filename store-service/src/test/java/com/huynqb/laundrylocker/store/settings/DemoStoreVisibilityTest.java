package com.huynqb.laundrylocker.store.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Cụm "Giả lập & demo" (F3-G14): tắt "Hiện điểm gửi demo cho khách" thì điểm gửi demo biến khỏi khách thường,
/// admin và tài khoản demo được phép vẫn thấy; điểm gửi thật không bị ảnh hưởng.
class DemoStoreVisibilityTest {

    private static StoreRules rules(Map<String, ?> overrides) {
        return new StoreRules(BusinessSettings.inMemory("store", new StoreSettingsCatalog(), overrides));
    }

    @Test
    void defaultKeepsDemoStoresVisible() {
        assertFalse(rules(Map.of()).demoStoreHiddenFor(800001L, 900123L, "CUSTOMER"));
    }

    @Test
    void hiddenOnlyForRegularCustomersAndAnonymous() {
        StoreRules r = rules(Map.of(StoreSettingsCatalog.DEMO_VISIBLE, false));
        assertTrue(r.demoStoreHiddenFor(800001L, 900123L, "CUSTOMER"));
        assertTrue(r.demoStoreHiddenFor(800007L, null, null));
        assertFalse(r.demoStoreHiddenFor(800001L, 800101L, "CUSTOMER"));
        assertFalse(r.demoStoreHiddenFor(800001L, 1L, "ADMIN"));
        assertFalse(r.demoStoreHiddenFor(1L, 900123L, "CUSTOMER"));
    }
}
