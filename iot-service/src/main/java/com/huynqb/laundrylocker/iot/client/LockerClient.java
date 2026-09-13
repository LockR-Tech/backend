package com.huynqb.laundrylocker.iot.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "locker-service")
public interface LockerClient {

    @PostMapping("/api/boxes/{id}/open")
    ApiResponse<LockerBoxSummary> openBox(@PathVariable Long id);
}
