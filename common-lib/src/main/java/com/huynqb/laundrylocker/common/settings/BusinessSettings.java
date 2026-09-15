package com.huynqb.laundrylocker.common.settings;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/// Quy tắc nghiệp vụ admin chỉnh được (ADR-0005).
///
/// Thứ tự ưu tiên khi đọc: giá trị admin lưu trong DB → property/biến môi trường cùng tên key
/// → mặc định trong catalog. Giá trị DB được cache và làm mới sau mỗi lần ghi hoặc sau 30 giây,
/// nên đọc trên luồng nghiệp vụ không chạm DB. DB lỗi ⇒ dùng cache cũ/mặc định, không làm hỏng nghiệp vụ.
@Slf4j
@Component
@ConditionalOnProperty(name = "app.settings.scope")
public class BusinessSettings {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final int MAX_STRING_LENGTH = 2000;

    private final String scope;
    private final Map<String, SettingDefinition> definitions;
    private final SettingsStore store;
    private final Environment environment;
    private final Clock clock;

    private volatile Map<String, SettingsStore.StoredSetting> cache = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    @Autowired
    public BusinessSettings(
            @Value("${app.settings.scope}") String scope,
            List<SettingsCatalog> catalogs,
            SettingsStore store,
            Environment environment) {
        this(scope, catalogs, store, environment, Clock.systemUTC());
    }

    BusinessSettings(
            String scope, List<SettingsCatalog> catalogs, SettingsStore store, Environment environment, Clock clock) {
        this.scope = scope;
        this.store = store;
        this.environment = environment;
        this.clock = clock;
        Map<String, SettingDefinition> byKey = new LinkedHashMap<>();
        for (SettingsCatalog catalog : catalogs) {
            for (SettingDefinition definition : catalog.definitions()) {
                if (byKey.put(definition.key(), definition) != null) {
                    throw new IllegalStateException("Duplicate business setting key: " + definition.key());
                }
                // Mặc định sai kiểu là lỗi lập trình — phát hiện ngay khi khởi động.
                normalize(definition, definition.defaultValue());
            }
        }
        this.definitions = Collections.unmodifiableMap(byKey);
    }

    /// Bản dùng cho unit test: store trong bộ nhớ, không đọc biến môi trường; `overrides` như admin đã sửa.
    public static BusinessSettings inMemory(String scope, SettingsCatalog catalog, Map<String, ?> overrides) {
        BusinessSettings settings = new BusinessSettings(
                scope, List.of(catalog), new InMemorySettingsStore(),
                new org.springframework.core.env.AbstractEnvironment() { }, Clock.systemUTC());
        if (overrides != null && !overrides.isEmpty()) {
            settings.update(overrides, null);
        }
        return settings;
    }

    public String scope() {
        return scope;
    }

    // ---- Đọc trên luồng nghiệp vụ ----

    public int getInt(String key) {
        return Integer.parseInt(resolve(key, SettingType.INTEGER));
    }

    public long getLong(String key) {
        return Long.parseLong(resolve(key, SettingType.INTEGER));
    }

    public BigDecimal getDecimal(String key) {
        return new BigDecimal(resolve(key, SettingType.DECIMAL));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(resolve(key, SettingType.BOOLEAN));
    }

    public String getString(String key) {
        return resolve(key, SettingType.STRING);
    }

    public List<Integer> getIntList(String key) {
        String raw = resolve(key, SettingType.INTEGER_LIST);
        return Arrays.stream(raw.split(",")).map(String::trim).map(Integer::valueOf).toList();
    }

    // ---- Admin ----

    public List<SettingView> list() {
        Map<String, SettingsStore.StoredSetting> stored = current();
        List<SettingView> views = new ArrayList<>(definitions.size());
        for (SettingDefinition definition : definitions.values()) {
            SettingsStore.StoredSetting override = stored.get(definition.key());
            String effective = effectiveValue(definition, override);
            boolean overridden = override != null && effective.equals(safeNormalize(definition, override.value()));
            views.add(new SettingView(
                    scope,
                    definition.key(),
                    definition.group(),
                    definition.label(),
                    definition.description(),
                    definition.type(),
                    effective,
                    defaultValue(definition),
                    overridden,
                    definition.min(),
                    definition.max(),
                    definition.unit(),
                    definition.allowedValues(),
                    definition.publicValue(),
                    overridden ? override.updatedByUserId() : null,
                    overridden ? override.updatedAt() : null));
        }
        return views;
    }

    /// Kiểm tra toàn bộ trước rồi mới ghi — một giá trị sai thì không ghi giá trị nào.
    public List<SettingView> update(Map<String, ?> values, Long actorUserId) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException("SETTINGS_EMPTY", "No settings to update");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            SettingDefinition definition = require(entry.getKey());
            normalized.put(definition.key(), normalize(definition, toRaw(entry.getValue())));
        }
        Map<String, SettingsStore.StoredSetting> before = loadFresh();
        normalized.forEach((key, value) -> {
            SettingDefinition definition = definitions.get(key);
            String oldValue = effectiveValue(definition, before.get(key));
            if (before.containsKey(key) && value.equals(before.get(key).value())) {
                return;
            }
            store.upsert(key, value, actorUserId);
            store.audit(key, oldValue, value, actorUserId);
        });
        refresh();
        return list();
    }

    /// Xoá giá trị admin ⇒ quay về property/mặc định.
    public List<SettingView> reset(String key, Long actorUserId) {
        SettingDefinition definition = require(key);
        Map<String, SettingsStore.StoredSetting> before = loadFresh();
        if (before.containsKey(key)) {
            store.delete(key);
            store.audit(key, before.get(key).value(), null, actorUserId);
        }
        refresh();
        return list().stream().filter(view -> view.key().equals(definition.key())).toList();
    }

    public List<SettingAuditView> audits(String key, int limit) {
        if (key != null) {
            require(key);
        }
        return store.audits(key, Math.max(1, Math.min(limit, 500)));
    }

    /// Giá trị đã ép kiểu của các quy tắc công khai (giá, giới hạn hiển thị cho khách).
    public Map<String, Object> publicValues() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (SettingDefinition definition : definitions.values()) {
            if (!definition.publicValue()) {
                continue;
            }
            String key = definition.key();
            result.put(key, switch (definition.type()) {
                case INTEGER -> getLong(key);
                case DECIMAL -> getDecimal(key);
                case BOOLEAN -> getBoolean(key);
                case STRING -> getString(key);
                case INTEGER_LIST -> getIntList(key);
            });
        }
        return result;
    }

    // ---- Nội bộ ----

    private String resolve(String key, SettingType expected) {
        SettingDefinition definition = require(key);
        if (definition.type() != expected) {
            throw new IllegalStateException("Setting " + key + " is " + definition.type() + ", not " + expected);
        }
        return effectiveValue(definition, current().get(key));
    }

    private String effectiveValue(SettingDefinition definition, SettingsStore.StoredSetting override) {
        if (override != null) {
            String value = safeNormalize(definition, override.value());
            if (value != null) {
                return value;
            }
            log.warn("Ignoring invalid stored value for setting {}: {}", definition.key(), override.value());
        }
        return defaultValue(definition);
    }

    private String defaultValue(SettingDefinition definition) {
        String fromEnvironment = environment.getProperty(definition.key());
        if (fromEnvironment != null) {
            String value = safeNormalize(definition, fromEnvironment);
            if (value != null) {
                return value;
            }
            log.warn("Ignoring invalid property value for setting {}: {}", definition.key(), fromEnvironment);
        }
        return normalize(definition, definition.defaultValue());
    }

    private Map<String, SettingsStore.StoredSetting> current() {
        if (Duration.between(loadedAt, clock.instant()).compareTo(CACHE_TTL) >= 0) {
            refresh();
        }
        return cache;
    }

    private synchronized void refresh() {
        try {
            cache = Map.copyOf(store.loadAll());
        } catch (RuntimeException ex) {
            log.warn("Could not load business settings for {}, keeping previous values: {}", scope, ex.getMessage());
        }
        loadedAt = clock.instant();
    }

    private Map<String, SettingsStore.StoredSetting> loadFresh() {
        return store.loadAll();
    }

    private SettingDefinition require(String key) {
        SettingDefinition definition = key == null ? null : definitions.get(key);
        if (definition == null) {
            throw new BusinessException(
                    "SETTING_UNKNOWN", "Unknown setting for " + scope + ": " + key, HttpStatus.NOT_FOUND);
        }
        return definition;
    }

    private static String toRaw(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            iterable.forEach(item -> parts.add(String.valueOf(item)));
            return String.join(",", parts);
        }
        return String.valueOf(value);
    }

    private static String safeNormalize(SettingDefinition definition, String raw) {
        try {
            return normalize(definition, raw);
        } catch (BusinessException ex) {
            return null;
        }
    }

    /// Kiểm tra kiểu + giới hạn và trả dạng chuẩn để lưu; sai ⇒ `400 SETTING_INVALID`.
    static String normalize(SettingDefinition definition, String raw) {
        String value = raw == null ? null : raw.trim();
        if (value == null || value.isEmpty()) {
            // Chuỗi tự do được phép rỗng (ví dụ danh sách user id rỗng = cho tất cả).
            if (definition.type() == SettingType.STRING && definition.allowedValues().isEmpty()) {
                return "";
            }
            throw invalid(definition, "value is required");
        }
        return switch (definition.type()) {
            case INTEGER -> String.valueOf(checkRange(definition, parseLong(definition, value)));
            case DECIMAL -> {
                BigDecimal decimal;
                try {
                    decimal = new BigDecimal(value);
                } catch (NumberFormatException ex) {
                    throw invalid(definition, "must be a number");
                }
                if (definition.min() != null && decimal.compareTo(new BigDecimal(definition.min())) < 0) {
                    throw invalid(definition, "must be ≥ " + definition.min());
                }
                if (definition.max() != null && decimal.compareTo(new BigDecimal(definition.max())) > 0) {
                    throw invalid(definition, "must be ≤ " + definition.max());
                }
                yield decimal.stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> {
                String lower = value.toLowerCase(Locale.ROOT);
                if (!lower.equals("true") && !lower.equals("false")) {
                    throw invalid(definition, "must be true or false");
                }
                yield lower;
            }
            case STRING -> {
                if (value.length() > MAX_STRING_LENGTH) {
                    throw invalid(definition, "is too long");
                }
                if (!definition.allowedValues().isEmpty() && !definition.allowedValues().contains(value)) {
                    throw invalid(definition, "must be one of " + definition.allowedValues());
                }
                yield value;
            }
            case INTEGER_LIST -> {
                List<Long> items = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(part -> !part.isEmpty())
                        .map(part -> checkRange(definition, parseLong(definition, part)))
                        .toList();
                if (items.isEmpty()) {
                    throw invalid(definition, "needs at least one number");
                }
                yield items.stream().map(String::valueOf).collect(Collectors.joining(","));
            }
        };
    }

    private static long parseLong(SettingDefinition definition, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE) {
                throw invalid(definition, "is out of range");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw invalid(definition, "must be a whole number");
        }
    }

    private static long checkRange(SettingDefinition definition, long value) {
        if (definition.min() != null && value < Long.parseLong(definition.min())) {
            throw invalid(definition, "must be ≥ " + definition.min());
        }
        if (definition.max() != null && value > Long.parseLong(definition.max())) {
            throw invalid(definition, "must be ≤ " + definition.max());
        }
        return value;
    }

    private static BusinessException invalid(SettingDefinition definition, String reason) {
        return new BusinessException(
                "SETTING_INVALID", definition.label() + " (" + definition.key() + ") " + reason);
    }
}
