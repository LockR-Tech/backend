package com.huynqb.laundrylocker.loyalty.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc loyalty-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestLoyaltyRules {

    private TestLoyaltyRules() {
    }

    /// Tỉ lệ trước khi chuyển sang 1 điểm = 1đ (tích 1 điểm/1.000đ, 1 điểm = 100đ, mốc hạng 500/2000/5000,
    /// tối thiểu 10 điểm mỗi lần dùng) — cho các test kiểm cách tính, không phụ thuộc mặc định hiện hành.
    public static final Map<String, Object> LEGACY_RATES = Map.of(
            "app.loyalty.vnd-per-point", 1000,
            "app.loyalty.redeem-vnd-per-point", 100,
            "app.loyalty.redeem-min-points", 10,
            "app.loyalty.tier-silver-points", 500,
            "app.loyalty.tier-gold-points", 2000,
            "app.loyalty.tier-platinum-points", 5000);

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
