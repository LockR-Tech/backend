package com.huynqb.laundrylocker.order.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;

import static com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog.*;

/// Đọc có kiểu các quy tắc nghiệp vụ của order-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Component
@RequiredArgsConstructor
public class OrderRules {

    private final BusinessSettings settings;

    public BigDecimal sendBaseFee() {
        return BigDecimal.valueOf(settings.getLong(SEND_BASE_FEE));
    }

    public BigDecimal droneDeliveryFee() {
        return BigDecimal.valueOf(settings.getLong(DRONE_DELIVERY_FEE));
    }

    public BigDecimal rentalRate(String cellType) {
        return BigDecimal.valueOf(settings.getLong("XL".equalsIgnoreCase(cellType) ? RENTAL_RATE_XL : RENTAL_RATE_STANDARD));
    }

    public BigDecimal storageItemFee() {
        return BigDecimal.valueOf(settings.getLong(STORAGE_ITEM_FEE));
    }

    public long overtimeFeePerHour() {
        return settings.getLong(OVERTIME_FEE_PER_HOUR);
    }

    public BigDecimal maxOvertimeFee() {
        return BigDecimal.valueOf(settings.getLong(MAX_OVERTIME_FEE));
    }

    public int maxOvertimePercent() {
        return settings.getInt(MAX_OVERTIME_PERCENT);
    }

    public int sendPickupHours() {
        return settings.getInt(SEND_PICKUP_HOURS);
    }

    public int dronePickupHours() {
        return settings.getInt(DRONE_PICKUP_HOURS);
    }

    public int autoCancelHours() {
        return settings.getInt(AUTO_CANCEL_HOURS);
    }

    public int overdueReleaseHours() {
        return settings.getInt(OVERDUE_RELEASE_HOURS);
    }

    public int reminderCooldownMinutes() {
        return settings.getInt(REMINDER_COOLDOWN_MINUTES);
    }

    public int rentalMinHours() {
        return settings.getInt(RENTAL_MIN_HOURS);
    }

    public int rentalMaxHours() {
        return Math.max(rentalMinHours(), settings.getInt(RENTAL_MAX_HOURS));
    }

    public int extendMaxHours() {
        return settings.getInt(EXTEND_MAX_HOURS);
    }

    public boolean requirePaymentBeforeDrop() {
        return settings.getBoolean(REQUIRE_PAYMENT_BEFORE_DROP);
    }

    public boolean sendConfirmRequiresOpen() {
        return settings.getBoolean(SEND_CONFIRM_REQUIRES_OPEN);
    }

    public boolean blockUnpaidRentalAccess() {
        return settings.getBoolean(BLOCK_UNPAID_RENTAL_ACCESS);
    }

    /// Gửi mã mở tủ cho người nhận qua SMS. Bật mà máy chủ chưa nạp khoá nhà cung cấp
    /// thì vẫn không gửi được — notification-service báo lại `smsChannelAvailable=false`.
    public boolean receiverNotifySms() {
        return settings.getBoolean(RECEIVER_NOTIFY_SMS);
    }

    public boolean receiverNotifyEmail() {
        return settings.getBoolean(RECEIVER_NOTIFY_EMAIL);
    }

    public boolean droneDemoEnabled() {
        return settings.getBoolean(DRONE_DEMO_ENABLED);
    }

    /// Danh sách rỗng = mọi người được dùng DEMO.
    public boolean droneDemoAllowedFor(Long userId) {
        String allowed = settings.getString(DRONE_DEMO_ALLOWED_USER_IDS);
        if (allowed.isBlank()) {
            return true;
        }
        String expected = String.valueOf(userId);
        return Arrays.stream(allowed.split(",")).map(String::trim).anyMatch(expected::equals);
    }

    public long droneDemoStageDelayMs() {
        return settings.getLong(DRONE_DEMO_STAGE_DELAY_MS);
    }

    public int droneMinPreflightBatteryPercent() {
        return settings.getInt(DRONE_MIN_PREFLIGHT_BATTERY);
    }
}
