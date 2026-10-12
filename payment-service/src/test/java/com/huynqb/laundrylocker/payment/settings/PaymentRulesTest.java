package com.huynqb.laundrylocker.payment.settings;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.common.settings.SettingView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.huynqb.laundrylocker.payment.settings.PaymentSettingsCatalog.ENABLED_METHODS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Danh sách phương thức thanh toán đang bật sau khi ngừng hỗ trợ VNPay/MoMo.
class PaymentRulesTest {

    @Test
    void defaultEnabledMethodsIsSepayOnly() {
        BusinessSettings settings = TestPaymentRules.settings(Map.of());

        assertEquals(List.of("SEPAY"), List.copyOf(new PaymentRules(settings).enabledMethods()));
        assertEquals("SEPAY", settings.publicValues().get(ENABLED_METHODS));

        SettingView view = settings.list().stream().filter(v -> v.key().equals(ENABLED_METHODS)).findFirst().orElseThrow();
        assertEquals(List.of("CASH", "WALLET", "SEPAY"), view.allowedItems());
        // allowedValues rỗng ⇒ web admin vẫn hiện ô nhập tự do, không thành dropdown chọn một.
        assertEquals(List.of(), view.allowedValues());
    }

    @Test
    void adminCanNoLongerEnableRetiredOrUnknownMethods() {
        for (String value : new String[]{"CASH,VNPAY", "momo", "WALLET,BITCOIN"}) {
            BusinessException error = assertThrows(
                    BusinessException.class, () -> TestPaymentRules.of(Map.of(ENABLED_METHODS, value)), value);
            assertEquals("SETTING_INVALID", error.getCode(), value);
            assertTrue(error.getMessage().contains("không được hỗ trợ"), error.getMessage());
        }

        PaymentRules rules = TestPaymentRules.of(Map.of(ENABLED_METHODS, " sepay ,wallet,,SEPAY"));
        assertEquals(List.of("SEPAY", "WALLET"), List.copyOf(rules.enabledMethods()));
        assertTrue(rules.isMethodEnabled("sepay"));
        assertFalse(rules.isMethodEnabled("CASH"));
    }

    /// Giá trị admin lưu trước khi ngừng hỗ trợ vẫn đọc được: chỉ bỏ VNPAY/MOMO, giữ lựa chọn còn lại
    /// (CASH vẫn tắt nếu admin đã tắt), và giá trị công khai cho app cũng đã được lọc.
    @Test
    void storedLegacyValueIsFilteredOnRead() {
        BusinessSettings settings = TestPaymentRules.preexisting(
                Map.of(ENABLED_METHODS, "WALLET,VNPAY,MOMO,SEPAY"), Map.of());
        PaymentRules rules = new PaymentRules(settings);

        assertEquals(List.of("WALLET", "SEPAY"), List.copyOf(rules.enabledMethods()));
        assertFalse(rules.isMethodEnabled("VNPAY"));
        assertFalse(rules.isMethodEnabled("MOMO"));
        assertFalse(rules.isMethodEnabled("CASH"));
        assertEquals("WALLET,SEPAY", settings.publicValues().get(ENABLED_METHODS));
    }

    @Test
    void legacyValueWithOnlyRetiredMethodsFallsBackToDefault() {
        PaymentRules rules = new PaymentRules(
                TestPaymentRules.preexisting(Map.of(ENABLED_METHODS, "VNPAY,MOMO"), Map.of()));

        assertEquals(List.of("SEPAY"), List.copyOf(rules.enabledMethods()));
    }

    @Test
    void legacyEnvironmentValueIsFilteredOnRead() {
        PaymentRules rules = new PaymentRules(
                TestPaymentRules.preexisting(Map.of(), Map.of(ENABLED_METHODS, "CASH,VNPAY,MOMO,SEPAY")));

        assertEquals(List.of("CASH", "SEPAY"), List.copyOf(rules.enabledMethods()));
    }

    @Test
    void retiredMethodsAreRecognisedWithTopupSuffix() {
        assertTrue(PaymentRules.isRetiredMethod("VNPAY"));
        assertTrue(PaymentRules.isRetiredMethod(" momo "));
        assertTrue(PaymentRules.isRetiredMethod("VNPAY_TOPUP"));
        assertFalse(PaymentRules.isRetiredMethod("SEPAY"));
        assertFalse(PaymentRules.isRetiredMethod("SEPAY_TOPUP"));
        assertFalse(PaymentRules.isRetiredMethod(null));
    }
}
