package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {

    @GetMapping("/{id}")
    ApiResponse<UserSummary> getUser(@PathVariable Long id);

    @GetMapping("/by-phone")
    ApiResponse<UserSummary> getUserByPhone(@RequestParam("phone") String phone);

    @GetMapping
    ApiResponse<List<UserSummary>> getUsersByRole(@RequestParam("role") String role);
}
