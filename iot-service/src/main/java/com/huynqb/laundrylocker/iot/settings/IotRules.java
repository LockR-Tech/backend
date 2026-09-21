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
}
