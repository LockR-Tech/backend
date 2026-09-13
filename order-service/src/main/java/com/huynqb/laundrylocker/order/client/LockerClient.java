package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "locker-service", path = "/internal/boxes")
public interface LockerClient {

    @GetMapping("/{id}")
    ApiResponse<LockerBoxSummary> getBox(@PathVariable Long id);

    // Toàn bộ ô (id/lockerId/status/reservedUntil) cho job đối soát G4.
    @GetMapping
    ApiResponse<List<Map<String, Object>>> listBoxes();

    @PostMapping("/{id}/reserve")
    ApiResponse<LockerBoxSummary> reserveBox(@PathVariable Long id, @RequestParam(required = false) String channel);

    @PostMapping("/{id}/occupy")
    ApiResponse<LockerBoxSummary> occupyBox(@PathVariable Long id);

    @PostMapping("/{id}/release")
    ApiResponse<LockerBoxSummary> releaseBox(@PathVariable Long id);

    @PostMapping("/{id}/fault")
    ApiResponse<Map<String, Object>> reportFault(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") Long userId);
}
