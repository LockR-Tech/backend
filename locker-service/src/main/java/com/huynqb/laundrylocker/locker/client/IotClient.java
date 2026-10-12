package com.huynqb.laundrylocker.locker.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@FeignClient(name = "iot-service", path = "/internal/iot")
public interface IotClient {

    @PostMapping("/force-unlock")
    ApiResponse<Map<String, Object>> forceUnlock(@RequestBody ForceUnlockRequest request);

    /// `reason`/`note` của KTV được iot-service ghi vào `box_access_logs.message`.
    record ForceUnlockRequest(Long lockerId, Long boxId, Long actorUserId, String reason, String note) {

        public ForceUnlockRequest(Long lockerId, Long boxId, Long actorUserId) {
            this(lockerId, boxId, actorUserId, null, null);
        }
    }

    /// Booking → IoT sync (GAP 1): tell the cabinet a box's lifecycle state changed
    /// (RESERVED/OCCUPIED/AVAILABLE/FAULT). Best-effort — callers swallow failures.
    @PostMapping("/box-sync")
    ApiResponse<Map<String, Object>> syncBoxState(@RequestBody BoxStateSyncRequest request);

    record BoxStateSyncRequest(Long lockerId, Long boxId, String state, Long orderId) {
    }

    /// Cabinet-reported hardware/door state per box (GAP 2), joined with the logical
    /// order-driven status to build the maintenance box-health view. Best-effort.
    @GetMapping("/box-status")
    ApiResponse<List<BoxHardwareStatus>> boxStatus(@RequestParam(value = "lockerId", required = false) Long lockerId);

    record BoxHardwareStatus(Long boxId, Long lockerId, String hwState, LocalDateTime lastReportedAt) {
    }

    /// Toàn bộ bộ điều khiển tủ (kèm `lockerId`, `online`) — tính cờ `online` cho danh sách tủ bằng một lần gọi.
    @GetMapping("/gateways")
    ApiResponse<List<GatewayDeviceResponse>> listGateways();

    @GetMapping("/gateways/{lockerId}")
    ApiResponse<GatewayDeviceResponse> getGatewayByLockerId(@PathVariable("lockerId") Long lockerId);

    record GatewayDeviceResponse(
            Long id,
            String macAddress,
            String hardware,
            String firmwareVersion,
            Integer slaveId,
            Integer availableSlots,
            Long reportedLockerId,
            Long lockerId,
            boolean online,
            String setupStatus,
            String setupProgress,
            LocalDateTime lastSeenAt) {
    }
}
