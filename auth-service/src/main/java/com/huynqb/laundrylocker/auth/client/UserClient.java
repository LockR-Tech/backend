package com.huynqb.laundrylocker.auth.client;

import com.huynqb.laundrylocker.auth.dto.UserProvisionRequest;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {

    @GetMapping("/{id}")
    ApiResponse<UserSummary> getUser(@PathVariable Long id);

    @PostMapping
    ApiResponse<UserSummary> provisionUser(@RequestBody UserProvisionRequest request);
}
