package com.huynqb.laundrylocker.iot.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "locker-service")
public interface LockerClient {

    @PostMapping("/api/boxes/{id}/open")
    ApiResponse<LockerBoxSummary> openBox(@PathVariable Long id);

    /// Toàn bộ ô của tủ kèm `boxNumber` — nguồn của `slotIndex` trong lệnh MQTT (ADR-0008).
    @GetMapping("/internal/lockers/{id}/layout")
    ApiResponse<LockerLayoutView> layout(@PathVariable Long id);
}
