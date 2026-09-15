package com.huynqb.laundrylocker.auth.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.huynqb.laundrylocker.auth.settings.AuthSettingsCatalog.*;

/// Đọc có kiểu các quy tắc nghiệp vụ của auth-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Component
@RequiredArgsConstructor
public class AuthRules {

    private final BusinessSettings settings;

    public int otpExpirySeconds() {
        return settings.getInt(OTP_EXPIRY_SECONDS);
    }

    public int otpLength() {
        return settings.getInt(OTP_LENGTH);
    }

    public int tempTokenTtlSeconds() {
        return settings.getInt(TEMP_TOKEN_TTL_SECONDS);
    }
}
