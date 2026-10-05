package com.huynqb.laundrylocker.iot.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.dto.AssignGatewayRequest;
import com.huynqb.laundrylocker.iot.dto.GatewayDeviceResponse;
import com.huynqb.laundrylocker.iot.service.GatewayProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/// Bộ điều khiển tủ (Pi) và việc gán vào tủ — ADR-0008. `/api/admin/**` chỉ ADMIN (gateway).
@RestController
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayProvisioningService provisioning;

    @GetMapping("/api/admin/iot/gateways")
    public ApiResponse<List<GatewayDeviceResponse>> list() {
        return ApiResponse.ok(provisioning.list());
    }

    /// Gửi lệnh setup kèm sơ đồ ô của tủ; kết quả thử từng ô về sau qua MQTT (xem lại bằng GET).
    @PostMapping("/api/admin/iot/gateways/{id}/assign")
    public ApiResponse<GatewayDeviceResponse> assign(
            @PathVariable Long id, @Valid @RequestBody AssignGatewayRequest request) {
        return ApiResponse.ok("GATEWAY_SETUP_SENT", "Setup command sent", provisioning.assign(id, request));
    }

    @PostMapping("/api/admin/iot/gateways/{id}/unassign")
    public ApiResponse<GatewayDeviceResponse> unassign(@PathVariable Long id) {
        return ApiResponse.ok("GATEWAY_CLEAR_SENT", "Clear-setup command sent", provisioning.unassign(id));
    }

    @PostMapping("/api/admin/iot/gateways/{id}/discover")
    public ApiResponse<Void> rediscover(@PathVariable Long id) {
        provisioning.rediscover(id);
        return ApiResponse.ok("GATEWAY_DISCOVERY_SENT", "Discovery request sent");
    }

    @DeleteMapping("/api/admin/iot/gateways/{id}")
    public ApiResponse<Void> forget(@PathVariable Long id) {
        provisioning.forget(id);
        return ApiResponse.ok("GATEWAY_DELETED", "Gateway removed");
    }

    @GetMapping("/internal/iot/gateways/{lockerId}")
    public ApiResponse<GatewayDeviceResponse> getGatewayByLockerId(@PathVariable Long lockerId) {
        return ApiResponse.ok(provisioning.getByLockerId(lockerId));
    }
}
