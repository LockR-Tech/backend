package com.huynqb.laundrylocker.iot.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.iot.dto.*;
import com.huynqb.laundrylocker.iot.service.DeviceManagementService;
import com.huynqb.laundrylocker.iot.service.IotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class IotController {

    private final IotService iotService;
    private final DeviceManagementService deviceManagementService;

    // Pi báo trạng thái qua MQTT; đường HTTP này chỉ cho vận hành (KTV tủ/ADMIN) — khách mà gọi
    // được thì giả thiết bị online/offline tuỳ ý (SEC-06). Gateway cũng chặn theo vai trò.
    @PostMapping("/api/iot/device-status")
    public ApiResponse<DeviceStatusResponse> updateStatus(
            @Valid @RequestBody DeviceStatusRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireOperator(roles);
        return ApiResponse.ok("IOT_STATUS_UPDATED", "Device status updated", iotService.updateStatus(request));
    }

    private static void requireOperator(String roles) {
        if (!UserRoles.hasAny(roles, "ADMIN", "LOCKER_TECHNICIAN")) {
            throw new BusinessException(
                    "IOT_OPERATOR_REQUIRED", "Chỉ kỹ thuật viên tủ hoặc quản trị viên được cập nhật trạng thái thiết bị",
                    HttpStatus.FORBIDDEN);
        }
    }

    // Operations visibility for Admin (web console): mọi thiết bị, kèm tên tủ và
    // trạng thái đang áp dụng (ONLINE chỉ khi còn tín hiệu trong cửa sổ online).
    @GetMapping("/api/admin/iot/device-status")
    public ApiResponse<List<DeviceStatusResponse>> listDeviceStatuses() {
        return ApiResponse.ok(deviceManagementService.listAllDevices());
    }

    // GAP 2: cabinet-reported hardware box state (door/sensor), kept separate from
    // the order-driven status so ops can spot mismatches. Optional ?lockerId= filter.
    @GetMapping("/api/admin/iot/box-status")
    public ApiResponse<List<BoxHardwareStatusResponse>> listBoxHardwareStatuses(
            @RequestParam(value = "lockerId", required = false) Long lockerId) {
        return ApiResponse.ok(iotService.listBoxHardwareStatuses(lockerId));
    }

    // Access and hardware audit logs for a locker
    @GetMapping("/api/admin/iot/lockers/{lockerId}/logs")
    public ApiResponse<List<BoxAccessLogResponse>> getLockerLogs(@PathVariable Long lockerId) {
        return ApiResponse.ok(iotService.getLockerLogs(lockerId));
    }

    // Service-to-service (blocked at gateway): locker-service joins this with its
    // own logical box status to build the maintenance box-health view.
    @GetMapping("/internal/iot/box-status")
    public ApiResponse<List<BoxHardwareStatusResponse>> listBoxHardwareStatusesInternal(
            @RequestParam(value = "lockerId", required = false) Long lockerId) {
        return ApiResponse.ok(iotService.listBoxHardwareStatuses(lockerId));
    }

    @PostMapping("/api/iot/unlock")
    public ApiResponse<Map<String, Object>> unlock(
            @Valid @RequestBody UnlockRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("IOT_UNLOCK_ACCEPTED", "Unlock command accepted", iotService.unlock(request, userId));
    }

    // Service-to-service only (blocked at gateway): maintenance force-open,
    // called from locker-service's /api/locker-technician/boxes/{id}/force-open.
    @PostMapping("/internal/iot/force-unlock")
    public ApiResponse<Map<String, Object>> forceUnlock(@Valid @RequestBody ForceUnlockRequest request) {
        return ApiResponse.ok("IOT_FORCE_UNLOCK_ACCEPTED", "Force unlock accepted", iotService.forceUnlock(request));
    }

    // Service-to-service only (blocked at gateway): booking → IoT sync (GAP 1),
    // called from locker-service when a box is reserved/occupied/released/faulted.
    // Mirrors the box lifecycle state down to the cabinet over MQTT.
    @PostMapping("/internal/iot/box-sync")
    public ApiResponse<Map<String, Object>> boxSync(@Valid @RequestBody BoxStateSyncRequest request) {
        return ApiResponse.ok("IOT_BOX_SYNC_PUBLISHED", "Box state sync published", iotService.syncBoxState(request));
    }


    @PostMapping("/api/iot/verify-pin")
    public ApiResponse<VerifyPinResponse> verifyPin(@Valid @RequestBody VerifyPinRequest request) {
        return ApiResponse.ok(iotService.verifyPin(request));
    }

    // PIN or signed QR token — what the cabinet touchscreen scans/keys in.
    @PostMapping("/api/iot/verify-access")
    public ApiResponse<VerifyPinResponse> verifyAccess(@Valid @RequestBody VerifyPinRequest request) {
        return ApiResponse.ok(iotService.verifyAccess(request.boxId(), request.pinCode()));
    }

    // Kiosk unlock by code only (PIN / QR token / delegation code) — the kiosk
    // does not know the boxId up front; iot-service resolves it from the order.
    @PostMapping("/api/iot/unlock-with-code")
    public ApiResponse<Map<String, Object>> unlockWithCode(
            @Valid @RequestBody UnlockWithCodeRequest request) {
        return ApiResponse.ok(
                "IOT_UNLOCK_ACCEPTED", "Unlock command accepted", iotService.unlockWithCode(request));
    }

    // Kiosk sau khi mở ô bằng mã: người gửi xác nhận đã bỏ hàng.
    @PostMapping("/api/iot/confirm-drop-with-code")
    public ApiResponse<Map<String, Object>> confirmDropWithCode(
            @Valid @RequestBody UnlockWithCodeRequest request) {
        return ApiResponse.ok(
                "IOT_DROP_CONFIRM_RESULT", "Drop-off confirmation processed", iotService.confirmDropWithCode(request));
    }

    // Kiosk sau khi mở ô bằng mã: người thuê lấy hết đồ và kết thúc thuê.
    @PostMapping("/api/iot/end-rental-with-code")
    public ApiResponse<Map<String, Object>> endRentalWithCode(
            @Valid @RequestBody UnlockWithCodeRequest request) {
        return ApiResponse.ok(
                "IOT_END_RENTAL_RESULT", "End rental processed", iotService.endRentalWithCode(request));
    }

    @PostMapping("/api/iot/pickup")
    public ApiResponse<PickupResponse> pickup(@Valid @RequestBody PickupRequest request, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("PICKUP_CONFIRMED", "Pickup confirmed", iotService.pickup(request, userId));
    }

    @PostMapping("/api/iot/box-status")
    public ApiResponse<Void> boxStatus(
            @Valid @RequestBody BoxStatusUpdateRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireOperator(roles);
        iotService.updateBoxStatus(request);
        return ApiResponse.ok("BOX_STATUS_UPDATED", "Box status event published");
    }

    @PostMapping("/internal/iot/device-status")
    public ApiResponse<DeviceStatusResponse> updateStatusInternal(@Valid @RequestBody DeviceStatusRequest request) {
        return ApiResponse.ok("IOT_STATUS_UPDATED", "Device status updated", iotService.updateStatus(request));
    }
}
