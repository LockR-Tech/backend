package com.huynqb.laundrylocker.iot.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.huynqb.laundrylocker.iot.settings.IotSettingsCatalog.*;

/// Đọc có kiểu các quy tắc nghiệp vụ của iot-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Component
@RequiredArgsConstructor
public class IotRules {

    private final BusinessSettings settings;

    public int lockoutMaxAttempts() {
        return settings.getInt(LOCKOUT_MAX_ATTEMPTS);
    }

    public int lockoutMinutes() {
        return settings.getInt(LOCKOUT_MINUTES);
    }

    public int unlockWaitSeconds() {
        return settings.getInt(UNLOCK_WAIT_SECONDS);
    }

    public int doorOpenTimeoutSeconds() {
        return settings.getInt(DOOR_OPEN_TIMEOUT_SECONDS);
    }

    public int kioskConfirmWindowMinutes() {
        return settings.getInt(KIOSK_CONFIRM_WINDOW_MINUTES);
    }

    public boolean testOpenEnabled() {
        return settings.getBoolean(TEST_OPEN_ENABLED);
    }

    public boolean testOpenAllowedUser(Long userId) {
        return userId != null && userId > 0 && settings.getIntList(TEST_OPEN_USER_IDS).contains(userId.intValue());
    }

    public boolean testOpenAllowedLocker(Long lockerId) {
        return lockerId != null && lockerId > 0 && settings.getIntList(TEST_OPEN_LOCKER_IDS).contains(lockerId.intValue());
    }

    public long bleTokenTtlSeconds() {
        return settings.getLong(BLE_TOKEN_TTL_SECONDS);
    }
}
