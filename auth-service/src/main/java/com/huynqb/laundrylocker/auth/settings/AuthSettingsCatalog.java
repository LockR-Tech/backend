package com.huynqb.laundrylocker.auth.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;

/// Quy tắc nghiệp vụ của auth-service admin chỉnh được (ADR-0005).
/// Không gồm JWT TTL/secret — đó là cấu hình bảo mật hạ tầng, giữ ở biến môi trường.
@Component
public class AuthSettingsCatalog implements SettingsCatalog {

    public static final String OTP_EXPIRY_SECONDS = "app.auth.otp.expiry-seconds";
    public static final String OTP_LENGTH = "app.auth.otp.length";
    public static final String TEMP_TOKEN_TTL_SECONDS = "app.auth.temp-token-ttl-seconds";

    private static final String AUTH = "Xác thực";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(OTP_EXPIRY_SECONDS, AUTH, "Thời hạn mã OTP email",
                        "Mã OTP gửi qua email (đăng nhập, quên mật khẩu, 2FA admin) hết hạn sau khoảng này.",
                        300, 60, 3600, "giây"),
                integer(OTP_LENGTH, AUTH, "Số chữ số của mã OTP",
                        "Áp dụng cho mã gửi sau khi lưu; mã đã gửi vẫn dùng được đến khi hết hạn.", 6, 4, 8, "chữ số"),
                integer(TEMP_TOKEN_TTL_SECONDS, AUTH, "Thời hạn token tạm",
                        "Token tạm cấp sau khi xác thực OTP/số điện thoại để hoàn tất đăng ký hoặc 2FA admin.",
                        600, 60, 3600, "giây"));
    }
}
