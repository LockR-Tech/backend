package com.huynqb.laundrylocker.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Rich user row for the admin web (profile + auth provider/verification).
 * `provider`/`emailVerified`/`hasPassword`/`lastLoginAt` null khi auth-service không tra được.
 */
public record AdminUserView(
        Long id,
        String email,
        String phoneNumber,
        String fullName,
        String status,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String provider,
        Boolean emailVerified,
        String imageUrl,
        /// Khách đã bấm "Đồng ý" Điều khoản dịch vụ trên app chưa, và lúc nào.
        boolean termsAccepted,
        LocalDateTime termsAcceptedAt,
        String termsVersion,
        LocalDate birthday,
        /// false = tài khoản Google/OTP chưa tự đặt mật khẩu.
        Boolean hasPassword,
        /// Chuỗi ISO từ auth-service, chỉ để hiển thị.
        String lastLoginAt) {

    public AdminUserView withAuth(String provider, Boolean emailVerified, Boolean hasPassword, String lastLoginAt) {
        return new AdminUserView(
                id, email, phoneNumber, fullName, status, roles, createdAt, updatedAt, provider, emailVerified,
                imageUrl, termsAccepted, termsAcceptedAt, termsVersion, birthday, hasPassword, lastLoginAt);
    }
}
