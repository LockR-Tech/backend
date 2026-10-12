package com.huynqb.laundrylocker.user.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service", path = "/internal/auth")
public interface AuthClient {

    @PostMapping("/accounts")
    ApiResponse<Map<String, Object>> createAccount(@RequestBody Map<String, Object> request);

    /// Body `{currentPassword?, newPassword}`. Trả cặp token mới cho máy đang đổi (AuthResponse dạng map);
    /// sai/thiếu mật khẩu hiện tại ⇒ 400 AUTH_INVALID_PASSWORD / AUTH_CURRENT_PASSWORD_REQUIRED.
    @PostMapping("/users/{userId}/password")
    ApiResponse<Map<String, Object>> changePassword(@PathVariable Long userId, @RequestBody Map<String, Object> request);

    /// Mỗi phần tử `{userId, provider, emailVerified, status, hasPassword}`.
    @GetMapping("/accounts/by-users")
    ApiResponse<List<Map<String, Object>>> accountsByUsers(@RequestParam("userIds") List<Long> userIds);

    /// Body `{status: ACTIVE|INACTIVE}`; INACTIVE thu hồi refresh token. Không có tài khoản ⇒ 200, `accountExists=false`.
    @PutMapping("/users/{userId}/status")
    ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long userId, @RequestBody Map<String, Object> request);

    /// Body `{email?, phoneNumber?}` — field rỗng = giữ nguyên. Trùng tài khoản khác ⇒ 409 AUTH_EMAIL_TAKEN/AUTH_PHONE_TAKEN.
    @PutMapping("/users/{userId}/identifiers")
    ApiResponse<Map<String, Object>> updateIdentifiers(@PathVariable Long userId, @RequestBody Map<String, Object> request);

    /// Xoá người dùng: khoá hẳn tài khoản đăng nhập, thu hồi refresh token, nhả email/số điện thoại và liên kết
    /// Google/Facebook cho tài khoản mới. Gọi lại vẫn an toàn. Không có tài khoản ⇒ 200, `accountExists=false`.
    @DeleteMapping("/users/{userId}")
    ApiResponse<Map<String, Object>> deleteAccount(@PathVariable Long userId);
}
