package com.huynqb.laundrylocker.common.settings;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BusinessSettingsTest {

    private InMemoryStore store;
    private MockEnvironment environment;
    private MutableClock clock;
    private BusinessSettings settings;

    private static final SettingsCatalog CATALOG = () -> List.of(
            SettingDefinition.integer("app.order.send-base-fee", "Giá", "Phí gửi", "", 15000, 0, 10_000_000, "VND").asPublic(),
            SettingDefinition.decimal("app.order.overtime-percent", "Phí", "Trần %", "", "50", "0", "100", "%"),
            SettingDefinition.bool("app.order.require-payment-before-drop", "Thanh toán", "Trả trước", "", true),
            SettingDefinition.integerList("app.order.rental-quick-hours", "Thuê", "Giờ nhanh", "", "2,4,8", 1, 720, "giờ").asPublic(),
            SettingDefinition.string("app.payment.default-method", "Thanh toán", "Mặc định", "", "CASH", List.of("CASH", "WALLET")));

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        environment = new MockEnvironment();
        clock = new MutableClock(Instant.parse("2026-09-15T08:00:00Z"));
        settings = new BusinessSettings("order", List.of(CATALOG), store, environment, clock);
    }

    @Test
    void readsCatalogDefaultThenEnvironmentThenAdminValue() {
        assertEquals(15000, settings.getInt("app.order.send-base-fee"));

        environment.setProperty("app.order.send-base-fee", "18000");
        assertEquals(18000, settings.getInt("app.order.send-base-fee"));

        settings.update(Map.of("app.order.send-base-fee", 20000), 1L);
        assertEquals(20000, settings.getInt("app.order.send-base-fee"));

        SettingView view = view("app.order.send-base-fee");
        assertTrue(view.overridden());
        assertEquals("20000", view.value());
        assertEquals("18000", view.defaultValue());
        assertEquals(1L, view.updatedByUserId());
    }

    @Test
    void typedGettersNormalizeValues() {
        settings.update(Map.of(
                "app.order.overtime-percent", "12.50",
                "app.order.require-payment-before-drop", "FALSE",
                "app.order.rental-quick-hours", List.of(1, 3, 24),
                "app.payment.default-method", "WALLET"), 7L);

        assertEquals(new BigDecimal("12.5"), settings.getDecimal("app.order.overtime-percent"));
        assertFalse(settings.getBoolean("app.order.require-payment-before-drop"));
        assertEquals(List.of(1, 3, 24), settings.getIntList("app.order.rental-quick-hours"));
        assertEquals("WALLET", settings.getString("app.payment.default-method"));
    }

    @Test
    void rejectsInvalidBatchWithoutWritingAnything() {
        BusinessException ex = assertThrows(BusinessException.class, () -> settings.update(new LinkedHashMap<>(Map.of(
                "app.order.send-base-fee", 1000,
                "app.order.overtime-percent", "150")), 1L));

        assertEquals("SETTING_INVALID", ex.getCode());
        assertTrue(store.values.isEmpty());
        assertEquals(15000, settings.getInt("app.order.send-base-fee"));
    }

    @Test
    void rejectsUnknownKeyWrongTypeAndDisallowedValue() {
        assertEquals("SETTING_UNKNOWN", assertThrows(BusinessException.class,
                () -> settings.update(Map.of("app.order.nope", 1), 1L)).getCode());
        assertEquals("SETTING_INVALID", assertThrows(BusinessException.class,
                () -> settings.update(Map.of("app.order.send-base-fee", "abc"), 1L)).getCode());
        assertEquals("SETTING_INVALID", assertThrows(BusinessException.class,
                () -> settings.update(Map.of("app.payment.default-method", "BITCOIN"), 1L)).getCode());
        assertEquals("SETTING_INVALID", assertThrows(BusinessException.class,
                () -> settings.update(Map.of("app.order.rental-quick-hours", "0,4"), 1L)).getCode());
    }

    @Test
    void auditsChangesAndResetReturnsToDefault() {
        settings.update(Map.of("app.order.send-base-fee", 20000), 1L);
        settings.update(Map.of("app.order.send-base-fee", 20000), 1L);
        settings.update(Map.of("app.order.send-base-fee", 25000), 2L);
        settings.reset("app.order.send-base-fee", 3L);

        assertEquals(15000, settings.getInt("app.order.send-base-fee"));
        assertEquals(3, store.audits.size());
        assertEquals(List.of("15000→20000", "20000→25000", "25000→null"),
                store.audits.stream().map(a -> a.oldValue() + "→" + a.newValue()).toList());
    }

    @Test
    void cachesStoreForThirtySecondsAndSurvivesStoreFailure() {
        settings.getInt("app.order.send-base-fee");
        int loads = store.loads;

        store.values.put("app.order.send-base-fee",
                new SettingsStore.StoredSetting("app.order.send-base-fee", "30000", 9L, LocalDateTime.now()));
        assertEquals(15000, settings.getInt("app.order.send-base-fee"));
        assertEquals(loads, store.loads);

        clock.advanceSeconds(31);
        assertEquals(30000, settings.getInt("app.order.send-base-fee"));

        store.failing = true;
        clock.advanceSeconds(31);
        assertEquals(30000, settings.getInt("app.order.send-base-fee"));
    }

    @Test
    void ignoresCorruptedStoredValue() {
        store.values.put("app.order.send-base-fee",
                new SettingsStore.StoredSetting("app.order.send-base-fee", "not-a-number", 1L, LocalDateTime.now()));
        clock.advanceSeconds(31);
        assertEquals(15000, settings.getInt("app.order.send-base-fee"));
    }

    @Test
    void publicValuesContainOnlyPublicTypedSettings() {
        Map<String, Object> values = settings.publicValues();
        assertEquals(2, values.size());
        assertEquals(15000L, values.get("app.order.send-base-fee"));
        assertEquals(List.of(2, 4, 8), values.get("app.order.rental-quick-hours"));
    }

    @Test
    void duplicateKeysAndInvalidDefaultsFailAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new BusinessSettings("x", List.of(CATALOG, CATALOG), store, environment, clock));
        SettingsCatalog broken = () -> List.of(
                SettingDefinition.integer("a", "g", "A", "", 5, 10, 20, null));
        assertThrows(BusinessException.class, () -> new BusinessSettings("x", List.of(broken), store, environment, clock));
    }

    private SettingView view(String key) {
        return settings.list().stream().filter(v -> v.key().equals(key)).findFirst().orElseThrow();
    }

    private static final class InMemoryStore implements SettingsStore {
        final Map<String, StoredSetting> values = new LinkedHashMap<>();
        final List<SettingAuditView> audits = new ArrayList<>();
        int loads;
        boolean failing;

        @Override
        public Map<String, StoredSetting> loadAll() {
            loads++;
            if (failing) {
                throw new IllegalStateException("db down");
            }
            return new LinkedHashMap<>(values);
        }

        @Override
        public void upsert(String key, String value, Long actorUserId) {
            values.put(key, new StoredSetting(key, value, actorUserId, LocalDateTime.now()));
        }

        @Override
        public void delete(String key) {
            values.remove(key);
        }

        @Override
        public void audit(String key, String oldValue, String newValue, Long actorUserId) {
            audits.add(new SettingAuditView((long) audits.size() + 1, key, oldValue, newValue, actorUserId, LocalDateTime.now()));
        }

        @Override
        public List<SettingAuditView> audits(String key, int limit) {
            return audits;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
