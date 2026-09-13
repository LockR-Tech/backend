package com.huynqb.laundrylocker.locker.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.locker.dto.DroneDeliveryResponse;
import com.huynqb.laundrylocker.locker.service.DroneDeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/// Giao hàng bằng drone: khách tạo yêu cầu qua `/api/drone-deliveries`
/// (JWT bất kỳ); đội bay điều phối qua `/api/maintenance/drone-deliveries`
/// (gateway giới hạn MAINTENANCE/ADMIN — TECHNICIAN không vào được).
@RestController
@RequiredArgsConstructor
public class DroneDeliveryController {

    private final DroneDeliveryService droneDeliveryService;

    public record CreateDroneDeliveryRequest(
            @NotNull Long lockerId, Long boxId, String receiverPhone, String description) {
    }

    // ---- Khách hàng ----

    @PostMapping("/api/drone-deliveries")
    public ApiResponse<DroneDeliveryResponse> create(
            @Valid @RequestBody CreateDroneDeliveryRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_DELIVERY_CREATED",
                "Drone delivery request created",
                droneDeliveryService.create(
                        userId,
                        request.lockerId(),
                        request.boxId(),
                        request.receiverPhone(),
                        request.description()));
    }

    @GetMapping("/api/drone-deliveries/my")
    public ApiResponse<List<DroneDeliveryResponse>> my(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(droneDeliveryService.myRequests(userId));
    }

    @PutMapping("/api/drone-deliveries/{id}/cancel")
    public ApiResponse<DroneDeliveryResponse> cancel(
            @PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_DELIVERY_CANCELED",
                "Drone delivery request canceled",
                droneDeliveryService.cancel(id, userId));
    }

    // ---- Đội bay (MAINTENANCE/ADMIN qua gateway) ----

    @GetMapping("/api/maintenance/drone-deliveries")
    public ApiResponse<List<DroneDeliveryResponse>> queue(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(droneDeliveryService.queue(status));
    }

    @PostMapping("/api/maintenance/drone-deliveries/{id}/dispatch")
    public ApiResponse<DroneDeliveryResponse> dispatch(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader("X-User-Id") Long userId) {
        Long droneUnitId = null;
        if (body != null && body.get("droneUnitId") != null) {
            droneUnitId = Long.valueOf(String.valueOf(body.get("droneUnitId")));
        }
        return ApiResponse.ok(
                "DRONE_DELIVERY_DISPATCHED",
                "Drone dispatched",
                droneDeliveryService.dispatch(id, userId, droneUnitId));
    }

    @PostMapping("/api/maintenance/drone-deliveries/{id}/complete")
    public ApiResponse<DroneDeliveryResponse> complete(@PathVariable Long id) {
        return ApiResponse.ok(
                "DRONE_DELIVERY_COMPLETED", "Drone delivery completed", droneDeliveryService.complete(id));
    }
}
