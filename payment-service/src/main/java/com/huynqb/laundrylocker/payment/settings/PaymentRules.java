package com.huynqb.laundrylocker.payment.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.huynqb.laundrylocker.payment.settings.PaymentSettingsCatalog.*;

/// Đọc có kiểu các quy tắc nghiệp vụ của payment-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Component
@RequiredArgsConstructor
public class PaymentRules {

    /// Các phương thức thanh toán đơn mà hệ thống hỗ trợ.
    public static final Set<String> SUPPORTED_METHODS = Set.copyOf(PAYMENT_METHODS);

    /// Phương thức đã ngừng hỗ trợ: không tạo giao dịch mới được (400 PAYMENT_METHOD_UNSUPPORTED) nhưng
    /// vẫn còn trong dữ liệu cũ (payments.method, kể cả dạng `*_TOPUP`) — giữ tên để đọc/hiển thị lịch sử.
    public static final Set<String> RETIRED_METHODS = Set.of("VNPAY", "MOMO");

    private static final String TOPUP_SUFFIX = "_TOPUP";

    private final BusinessSettings settings;

    public BigDecimal topupMinAmount() {
        return BigDecimal.valueOf(settings.getLong(TOPUP_MIN_AMOUNT));
    }

    public BigDecimal topupMaxAmount() {
        return BigDecimal.valueOf(Math.max(settings.getLong(TOPUP_MIN_AMOUNT), settings.getLong(TOPUP_MAX_AMOUNT)));
    }

    /// `true` nếu là phương thức đã ngừng hỗ trợ (VNPAY, MOMO, VNPAY_TOPUP…), không phân biệt hoa thường.
    public static boolean isRetiredMethod(String method) {
        if (method == null) {
            return false;
        }
        String normalized = method.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith(TOPUP_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - TOPUP_SUFFIX.length());
        }
        return RETIRED_METHODS.contains(normalized);
    }

    /// BusinessSettings đã chuẩn hoá giá trị theo {@link PaymentSettingsCatalog#PAYMENT_METHODS} (viết hoa,
    /// bỏ trùng). Giá trị admin lưu từ trước còn VNPAY/MOMO thì khi đọc chỉ bỏ hai phần tử đó.
    public Set<String> enabledMethods() {
        Set<String> enabled = new LinkedHashSet<>();
        for (String token : settings.getString(ENABLED_METHODS).split(",")) {
            String method = token.trim().toUpperCase(Locale.ROOT);
            if (SUPPORTED_METHODS.contains(method)) {
                enabled.add(method);
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

    /// Admin bật nút giả lập thanh toán và `userId` nằm trong danh sách được phép (trống = mọi người).
    public boolean simulationAllowedFor(Long userId) {
        if (userId == null || !settings.getBoolean(SIMULATION_ENABLED)) {
            return false;
        }
        String allowed = settings.getString(SIMULATION_ALLOWED_USER_IDS);
        if (allowed == null || allowed.isBlank()) {
            return true;
        }
        String expected = String.valueOf(userId);
        return java.util.Arrays.stream(allowed.split(",")).map(String::trim).anyMatch(expected::equals);
    }
}
