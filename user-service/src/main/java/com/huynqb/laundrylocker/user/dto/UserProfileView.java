package com.huynqb.laundrylocker.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/// `GET /api/user/profile`: các field của UserSummary + firstName/lastName/birthday để app sửa từng phần,
/// và `hasPassword` (false = tài khoản Google/OTP chưa tự đặt mật khẩu ⇒ đổi mật khẩu không cần mật khẩu hiện tại).
/// `termsAccepted` false ⇒ app bắt bấm "Đồng ý" Điều khoản dịch vụ trước khi dùng tiếp.
public record UserProfileView(
        Long id,
        String email,
        String phoneNumber,
        String fullName,
        String firstName,
        String lastName,
        LocalDate birthday,
        String status,
        Set<String> roles,
        String imageUrl,
        boolean hasPassword,
        boolean termsAccepted,
        LocalDateTime termsAcceptedAt,
        String termsVersion) {
}
