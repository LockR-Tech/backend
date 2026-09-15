package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/// Tra cứu người dùng theo lô cho trang admin (tách contextId khỏi UserClient).
@FeignClient(name = "user-service", contextId = "userLookupClient", path = "/internal/users")
public interface UserLookupClient {

    @GetMapping("/batch")
    ApiResponse<List<UserSummary>> getUsers(@RequestParam("ids") Collection<Long> ids);

    @GetMapping("/by-phone")
    ApiResponse<UserSummary> getUserByPhone(@RequestParam("phone") String phone);
}
