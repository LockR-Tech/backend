package com.huynqb.laundrylocker.payment.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static com.huynqb.laundrylocker.payment.settings.PaymentSettingsCatalog.*;

/// Đọc có kiểu các quy tắc nghiệp vụ của payment-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRules {

    /// Các phương thức thanh toán đơn mà hệ thống hỗ trợ.
    public static final Set<String> SUPPORTED_METHODS = Set.of("CASH", "WALLET", "VNPAY", "MOMO", "SEPAY");

    private final BusinessSettings settings;

    public BigDecimal topupMinAmount() {
        return BigDecimal.valueOf(settings.getLong(TOPUP_MIN_AMOUNT));
    }

    public BigDecimal topupMaxAmount() {
        return BigDecimal.valueOf(Math.max(settings.getLong(TOPUP_MIN_AMOUNT), settings.getLong(TOPUP_MAX_AMOUNT)));
    }

    /// Chuỗi admin nhập được chuẩn hoá (trim, viết hoa); token không thuộc {@link #SUPPORTED_METHODS} bị bỏ qua.
    public Set<String> enabledMethods() {
        Set<String> enabled = new LinkedHashSet<>();
        for (String token : settings.getString(ENABLED_METHODS).split(",")) {
            String method = token.trim().toUpperCase(Locale.ROOT);
            if (method.isEmpty()) {
                continue;
            }
            if (SUPPORTED_METHODS.contains(method)) {
                enabled.add(method);
            } else {
                log.warn("Ignoring unknown payment method in {}: {}", ENABLED_METHODS, token.trim());
            }
        }
        return Collections.unmodifiableSet(enabled);
    }

    public boolean isMethodEnabled(String method) {
        return method != null && enabledMethods().contains(method.trim().toUpperCase(Locale.ROOT));
    }

    public boolean cashAutoComplete() {
        return settings.getBoolean(CASH_AUTO_COMPLETE);
    }

    public boolean isRefundEnabled() {
        return settings.getBoolean(REFUND_ENABLED);
    }

    public BigDecimal refundMinAmount() {
        return BigDecimal.valueOf(settings.getLong(REFUND_MIN_AMOUNT));
    }

    public int refundMaxDays() {
        return settings.getInt(REFUND_MAX_DAYS);
    }

    public List<String> allowedRefundReasons() {
        String raw = settings.getString(REFUND_ALLOWED_REASONS);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean isRequireBankTransferRef() {
        return settings.getBoolean(REFUND_REQUIRE_TRANSFER_REF);
    }
}
