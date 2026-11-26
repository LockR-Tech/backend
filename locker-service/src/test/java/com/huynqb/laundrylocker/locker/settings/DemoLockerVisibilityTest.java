package com.huynqb.laundrylocker.locker.settings;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Cụm "Giả lập & demo" (F3-G14): tắt "Hiện tủ demo cho khách" thì tủ demo biến khỏi khách thường,
/// admin và tài khoản demo được phép vẫn thấy; tủ thật không bị ảnh hưởng.
class DemoLockerVisibilityTest {

    private static final long DEMO_LOCKER = 800001L;
    private static final long REAL_LOCKER = 7L;
    private static final long CUSTOMER = 900123L;
    private static final long DEMO_CUSTOMER = 800101L;

    @Test
    void defaultKeepsDemoLockersVisible() {
        LockerRules rules = TestLockerRules.defaults();
        assertFalse(rules.demoLockerHiddenFor(DEMO_LOCKER, CUSTOMER, "CUSTOMER"));
        assertFalse(rules.demoLockerHiddenFor(DEMO_LOCKER, null, null));
    }

    @Test
    void hiddenDemoLockerOnlyForRegularCustomersAndAnonymous() {
        LockerRules rules = TestLockerRules.of(Map.of(LockerSettingsCatalog.DEMO_VISIBLE, false));
        assertTrue(rules.demoLockerHiddenFor(DEMO_LOCKER, CUSTOMER, "CUSTOMER"));
        assertTrue(rules.demoLockerHiddenFor(DEMO_LOCKER, null, null));
        assertFalse(rules.demoLockerHiddenFor(DEMO_LOCKER, DEMO_CUSTOMER, "CUSTOMER"));
        assertFalse(rules.demoLockerHiddenFor(DEMO_LOCKER, 1L, "ADMIN"));
        assertFalse(rules.demoLockerHiddenFor(REAL_LOCKER, CUSTOMER, "CUSTOMER"));
    }

    @Test
    void adminCanEditDemoListAndAllowedUsers() {
        LockerRules rules = TestLockerRules.of(Map.of(
                LockerSettingsCatalog.DEMO_VISIBLE, false,
                LockerSettingsCatalog.DEMO_LOCKER_IDS, "4,800001",
                LockerSettingsCatalog.DEMO_ALLOWED_USER_IDS, "0"));
        assertTrue(rules.demoLockerHiddenFor(4L, CUSTOMER, "CUSTOMER"));
        assertTrue(rules.demoLockerHiddenFor(DEMO_LOCKER, DEMO_CUSTOMER, "CUSTOMER"));
        assertFalse(rules.demoLockerHiddenFor(800002L, CUSTOMER, "CUSTOMER"));
        assertFalse(rules.demoLockerHiddenFor(DEMO_LOCKER, 2L, "CUSTOMER,ADMIN"));
    }
}
