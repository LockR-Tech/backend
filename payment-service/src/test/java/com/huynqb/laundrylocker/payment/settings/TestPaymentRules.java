package com.huynqb.laundrylocker.payment.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.common.settings.InMemorySettingsStore;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

/// Quy tắc payment-service cho unit test: mặc định catalog + giá trị ghi đè như admin đã sửa.
public final class TestPaymentRules {

    private TestPaymentRules() {
    }

    public static BusinessSettings settings(Map<String, ?> overrides) {
        return BusinessSettings.inMemory("payment", new PaymentSettingsCatalog(), overrides);
    }

    /// Giá trị đã nằm sẵn trong DB / biến môi trường từ trước (không qua kiểm tra khi admin lưu),
    /// ví dụ danh sách phương thức còn VNPAY/MOMO từ trước khi ngừng hỗ trợ.
    public static BusinessSettings preexisting(Map<String, String> stored, Map<String, String> environment) {
        InMemorySettingsStore store = new InMemorySettingsStore();
        stored.forEach((key, value) -> store.upsert(key, value, 1L));
        MockEnvironment env = new MockEnvironment();
        environment.forEach(env::setProperty);
        return new BusinessSettings("payment", List.of(new PaymentSettingsCatalog()), store, env);
    }

    public static PaymentRules of(Map<String, ?> overrides) {
        return new PaymentRules(settings(overrides));
    }

    public static PaymentRules defaults() {
        return of(Map.of());
    }
}
