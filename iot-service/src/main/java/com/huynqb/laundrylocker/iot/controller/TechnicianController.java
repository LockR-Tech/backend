package com.huynqb.laundrylocker.iot.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.dto.BoxAccessLogResponse;
import com.huynqb.laundrylocker.iot.dto.DeviceStatusResponse;
import com.huynqb.laundrylocker.iot.service.DeviceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Device management endpoints for the LOCKER_TECHNICIAN role.
 * Gateway enforces LOCKER_TECHNICIAN|ADMIN and forwards X-User-Id / X-User-Roles;
 * KTV chỉ thấy thiết bị của tủ mình phụ trách (kiểm trong DeviceManagementService).
 */
@RestController
@RequiredArgsConstructor
public class TechnicianController {

    private final DeviceManagementService deviceService;

    /**
     * IoT devices with their effective status (ONLINE only within the online window).
     */
    @GetMapping("/api/locker-technician/devices")
    public ApiResponse<List<DeviceStatusResponse>> listDevices(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(deviceService.listDevices(userId, roles));
    }

    @GetMapping("/api/locker-technician/devices/{id}")
    public ApiResponse<DeviceStatusResponse> getDevice(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(deviceService.getDevice(id, userId, roles));
    }

    /**
     * Manually mark a device OFFLINE or ERROR (e.g. hardware physically disconnected
     * before the online window expires). ONLINE cannot be set by hand.
     * Request body: {"status": "OFFLINE"|"ERROR"}
     */
    @PutMapping("/api/locker-technician/devices/{id}/status")
    public ApiResponse<DeviceStatusResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        String status = body == null ? null : body.get("status");
        return ApiResponse.ok(
                "DEVICE_STATUS_UPDATED", "Device status updated", deviceService.updateStatus(id, status, userId, roles));
    }

    /**
     * Audit log for a device — box_access_logs of the locker the device is paired with, newest first.
     */
    @GetMapping("/api/locker-technician/devices/{id}/logs")
    public ApiResponse<List<BoxAccessLogResponse>> deviceLogs(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(deviceService.deviceLogs(id, userId, roles));
    }

    /**
     * Restart the cabinet controller process over MQTT and wait for its reply.
     * Always 200 so the client reads `data.accepted` / `data.status` / `data.message`.
     */
    @PostMapping("/api/locker-technician/devices/{id}/restart")
    public ApiResponse<Map<String, Object>> restartDevice(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        Map<String, Object> result = deviceService.restart(id, userId, roles);
        return Boolean.TRUE.equals(result.get("accepted"))
                ? ApiResponse.ok("DEVICE_RESTART_ACCEPTED", String.valueOf(result.get("message")), result)
                : ApiResponse.ok("DEVICE_RESTART_FAILED", String.valueOf(result.get("message")), result);
    }
}
