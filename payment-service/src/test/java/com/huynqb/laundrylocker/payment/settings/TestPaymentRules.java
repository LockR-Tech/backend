package com.huynqb.laundrylocker.payment.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;

import java.util.Map;

/// Quy tắc payment-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestPaymentRules {

    private TestPaymentRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("payment", new PaymentSettingsCatalog(), overrides);
    }

    public static PaymentRules of(Map<String, ?> overrides) {
        return new PaymentRules(settings(overrides));
    }

    public static PaymentRules defaults() {
        return of(Map.of());
    }
}
