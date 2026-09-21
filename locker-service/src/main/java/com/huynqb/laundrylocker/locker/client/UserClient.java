package com.huynqb.laundrylocker.locker.client;

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

    /// Người dùng ACTIVE có vai trò `role` — để báo phiếu cho mọi KTV tủ khi tủ chưa có người phụ trách.
    @GetMapping
    ApiResponse<List<UserSummary>> listByRole(@RequestParam("role") String role);
}
