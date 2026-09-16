package com.huynqb.laundrylocker.notification.channel;

import org.springframework.util.StringUtils;

/// Chuẩn hoá số điện thoại Việt Nam sang E.164 cho nhà cung cấp SMS.
///
/// Người dùng nhập `0901234567`, `090 123 4567` hay `+84901234567` đều phải ra cùng
/// một chuỗi thì Twilio mới nhận; số đã có `+` thì giữ nguyên để không phá số nước ngoài.
public final class PhoneNumbers {

    private static final String VN_COUNTRY_CODE = "+84";

    private PhoneNumbers() {
    }

    /// `null` khi chuỗi rỗng hoặc không còn chữ số nào sau khi bỏ khoảng trắng, dấu
    /// chấm, gạch ngang và ngoặc.
    public static String toE164(String raw) {
        if (!StringUtils.hasText(raw)) return null;

        String trimmed = raw.trim();
        boolean hadPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;

        if (hadPlus) return "+" + digits;

        // `0084…` và `84…` đều là mã quốc gia Việt Nam viết không dấu cộng.
        if (digits.startsWith("0084")) return "+" + digits.substring(2);
        if (digits.startsWith("84") && digits.length() >= 11) return "+" + digits;

        // Số nội địa bắt đầu bằng 0 ⇒ bỏ số 0 rồi ghép mã quốc gia.
        if (digits.startsWith("0")) return VN_COUNTRY_CODE + digits.substring(1);

        return VN_COUNTRY_CODE + digits;
    }
}
