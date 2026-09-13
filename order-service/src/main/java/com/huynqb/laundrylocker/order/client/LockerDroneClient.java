package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.DroneStatusUpdateRequest;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.dto.LockerLayoutDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "locker-service", contextId = "lockerDroneClient")
public interface LockerDroneClient {

    @GetMapping("/internal/drones/{id}")
    ApiResponse<DroneUnitDto> getDroneUnit(@PathVariable Long id);

    @GetMapping("/internal/lockers/{id}/layout")
    ApiResponse<LockerLayoutDto> getLockerLayout(@PathVariable Long id);

    @PostMapping("/internal/drones/{id}/status")
    ApiResponse<DroneUnitDto> updateDroneStatus(
            @PathVariable Long id, @RequestBody DroneStatusUpdateRequest request);
}
