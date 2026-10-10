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

    @PostMapping("/users/{userId}/password")
    ApiResponse<Void> changePassword(@PathVariable Long userId, @RequestBody Map<String, Object> request);

    @GetMapping("/accounts/by-users")
    ApiResponse<List<Map<String, Object>>> accountsByUsers(@RequestParam("userIds") List<Long> userIds);

    /// Body `{status: ACTIVE|INACTIVE}`; INACTIVE thu hồi refresh token. Không có tài khoản ⇒ 200, `accountExists=false`.
    @PutMapping("/users/{userId}/status")
    ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long userId, @RequestBody Map<String, Object> request);

    /// Body `{email?, phoneNumber?}` — field rỗng = giữ nguyên. Trùng tài khoản khác ⇒ 409 AUTH_EMAIL_TAKEN/AUTH_PHONE_TAKEN.
    @PutMapping("/users/{userId}/identifiers")
    ApiResponse<Map<String, Object>> updateIdentifiers(@PathVariable Long userId, @RequestBody Map<String, Object> request);
}
