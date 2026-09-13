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
}
