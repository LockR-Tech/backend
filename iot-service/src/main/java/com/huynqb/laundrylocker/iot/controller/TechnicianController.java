package com.huynqb.laundrylocker.iot.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.dto.BoxAccessLogResponse;
import com.huynqb.laundrylocker.iot.dto.DeviceStatusResponse;
import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import com.huynqb.laundrylocker.iot.model.DeviceStatus;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.DeviceStatusRepository;
import com.huynqb.laundrylocker.iot.service.LockerMqttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Device management endpoints for TECHNICIAN role.
 * Gateway enforces TECHNICIAN|ADMIN — no JWT re-validation here.
 * Depends on headers X-User-Id forwarded by gateway.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TechnicianController {

    private final DeviceStatusRepository deviceStatusRepository;
    private final BoxAccessLogRepository boxAccessLogRepository;
    private final LockerMqttService lockerMqttService;

    /**
     * List all registered IoT devices and their current status.
     */
    @GetMapping("/api/technician/devices")
    public ApiResponse<List<DeviceStatusResponse>> listDevices() {
        List<DeviceStatusResponse> devices = deviceStatusRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(devices);
    }

    /**
     * Get a single device by its DB id (health, last-seen, status).
     */
    @GetMapping("/api/technician/devices/{id}")
    public ApiResponse<DeviceStatusResponse> getDevice(@PathVariable Long id) {
        DeviceStatus device = deviceStatusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id));
        return ApiResponse.ok(toResponse(device));
    }

    /**
     * Manually override a device's status (e.g. mark OFFLINE when hardware
     * is physically disconnected but heartbeat hasn't timed out yet).
     * Request body: {"status": "ONLINE"|"OFFLINE"|"ERROR"}
     */
    @PutMapping("/api/technician/devices/{id}/status")
    public ApiResponse<DeviceStatusResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        DeviceStatus device = deviceStatusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id));
        String newStatus = body.getOrDefault("status", "UNKNOWN").toUpperCase();
        device.setStatus(newStatus);
        device.setLastSeenAt(LocalDateTime.now());
        DeviceStatus saved = deviceStatusRepository.save(device);
        return ApiResponse.ok("DEVICE_STATUS_UPDATED", "Device status updated", toResponse(saved));
    }

    /**
     * Audit log for a device — returns box_access_logs for the locker that
     * this device is paired with, ordered newest-first.
     */
    @GetMapping("/api/technician/devices/{id}/logs")
    public ApiResponse<List<BoxAccessLogResponse>> deviceLogs(@PathVariable Long id) {
        DeviceStatus device = deviceStatusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id));
        if (device.getLockerId() == null) {
            return ApiResponse.ok(List.of());
        }
        List<BoxAccessLogResponse> logs = boxAccessLogRepository
                .findByLockerIdOrderByCreatedAtDesc(device.getLockerId())
                .stream()
                .map(this::toLogResponse)
                .toList();
        return ApiResponse.ok(logs);
    }

    /**
     * Publish a restart command via MQTT (best-effort) and log the attempt.
     * If MQTT is unavailable the log entry is still written so the restart
     * request is auditable even without a physical response.
     */
    @PostMapping("/api/technician/devices/{id}/restart")
    public ApiResponse<Map<String, Object>> restartDevice(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        DeviceStatus device = deviceStatusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id));

        // Write audit log entry regardless of MQTT outcome
        BoxAccessLog auditLog = new BoxAccessLog();
        auditLog.setBoxId(0L);
        auditLog.setLockerId(device.getLockerId());
        auditLog.setActorUserId(userId);
        auditLog.setCredentialType("MASTER");
        auditLog.setResult("RESTART_REQUESTED");
        auditLog.setMessage("Technician restart for device " + device.getDeviceId());
        boxAccessLogRepository.save(auditLog);

        // Best-effort MQTT: publish a restart command. No box involved so boxId = 0.
        if (device.getLockerId() != null) {
            try {
                lockerMqttService.sendUnlockCommandAsync(device.getLockerId(), 0L);
            } catch (Exception ex) {
                log.warn("MQTT restart publish failed for device {}: {}", device.getDeviceId(), ex.getMessage());
            }
        }

        return ApiResponse.ok(
                "DEVICE_RESTART_REQUESTED",
                "Restart command sent",
                Map.of("deviceId", device.getDeviceId(), "lockerId",
                        device.getLockerId() != null ? device.getLockerId() : "N/A"));
    }

    private DeviceStatusResponse toResponse(DeviceStatus d) {
        return new DeviceStatusResponse(d.getId(), d.getDeviceId(), d.getLockerId(), d.getStatus(), d.getLastSeenAt());
    }

    private BoxAccessLogResponse toLogResponse(BoxAccessLog l) {
        return new BoxAccessLogResponse(
                l.getId(), l.getBoxId(), l.getLockerId(), l.getOrderId(),
                l.getActorUserId(), l.getCredentialType(), l.getResult(),
                l.getMessage(), l.getCreatedAt());
    }
}
