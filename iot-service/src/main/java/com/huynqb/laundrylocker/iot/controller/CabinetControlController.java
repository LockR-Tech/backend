package com.huynqb.laundrylocker.iot.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.dto.CabinetControlLogResponse;
import com.huynqb.laundrylocker.iot.dto.HardwareConfigRequest;
import com.huynqb.laundrylocker.iot.dto.HardwareConfigView;
import com.huynqb.laundrylocker.iot.service.CabinetControlService;
import com.huynqb.laundrylocker.iot.service.CabinetHardwareConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/// Bảng điều khiển tủ trên admin web: mở ô, điều khiển nắp/thanh trượt, xem trạng thái.
/// Gateway cho ADMIN và LOCKER_TECHNICIAN tới `/api/locker-technician/cabinet/**`; service
/// kiểm KTV chỉ điều khiển tủ mình phụ trách (ADMIN mọi tủ).
@RestController
@RequiredArgsConstructor
public class CabinetControlController {

    private final CabinetControlService control;
    private final CabinetHardwareConfigService hardware;

    /// Cấu hình phần cứng đã lưu (bản đồ chân + sơ đồ đấu dây); chưa lưu thì trả mẫu TU01.
    @GetMapping("/api/locker-technician/cabinet/{lockerId}/hardware")
    public ApiResponse<HardwareConfigView> hardware(
            @PathVariable Long lockerId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(hardware.get(lockerId, userId, roles));
    }

    /// Lưu cấu hình phần cứng (chỉ ADMIN). Chưa gửi xuống tủ — bấm Áp dụng để gửi.
    @PutMapping("/api/locker-technician/cabinet/{lockerId}/hardware")
    public ApiResponse<HardwareConfigView> saveHardware(
            @PathVariable Long lockerId,
            @RequestBody HardwareConfigRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("CABINET_HW_SAVED", "Đã lưu cấu hình phần cứng",
                hardware.save(lockerId, body, userId, roles));
    }

    /// Gửi cấu hình đã lưu xuống tủ (chỉ ADMIN); tủ khởi động lại bộ điều khiển khoảng 10 giây.
    @PostMapping("/api/locker-technician/cabinet/{lockerId}/hardware/apply")
    public ApiResponse<Map<String, Object>> applyHardware(
            @PathVariable Long lockerId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("CABINET_HW_APPLY", "Đã gửi cấu hình xuống tủ",
                hardware.apply(lockerId, userId, roles));
    }

    /// Trạng thái các trục nắp của tủ (để trang điều khiển hiển thị).
    @GetMapping("/api/locker-technician/cabinet/{lockerId}/panel")
    public ApiResponse<Map<String, Object>> panel(
            @PathVariable Long lockerId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(control.panel(lockerId, userId, roles));
    }

    /// Nhật ký điều khiển (mở ô, chạy nắp) của tủ, mới nhất trước; `limit` tối đa 200.
    @GetMapping("/api/locker-technician/cabinet/{lockerId}/logs")
    public ApiResponse<List<CabinetControlLogResponse>> logs(
            @PathVariable Long lockerId,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(control.logs(lockerId, limit, userId, roles));
    }

    /// Mở một ô của tủ.
    @PostMapping("/api/locker-technician/cabinet/{lockerId}/boxes/{boxId}/open")
    public ApiResponse<Map<String, Object>> openBox(
            @PathVariable Long lockerId,
            @PathVariable Long boxId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("CABINET_BOX_OPEN", "Đã gửi lệnh mở ô",
                control.openBox(lockerId, boxId, userId, roles));
    }

    /// Mở thử một ô bằng QR, không cần đơn (app khách quét tờ "QR mở ô"). Gateway cho mọi JWT qua
    /// `/api/iot/**`; service chỉ cho khi admin bật chế độ thử và tài khoản + tủ nằm trong danh sách.
    @PostMapping("/api/iot/test-open/lockers/{lockerId}/boxes/{boxId}")
    public ApiResponse<Map<String, Object>> testOpenBox(
            @PathVariable Long lockerId,
            @PathVariable Long boxId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("CABINET_BOX_TEST_OPEN", "Đã gửi lệnh mở thử ô",
                control.testOpenBox(lockerId, boxId, userId));
    }

    /// Điều khiển nắp/thanh trượt: action = open | close | home | stop.
    @PostMapping("/api/locker-technician/cabinet/{lockerId}/lid/{axis}/{action}")
    public ApiResponse<Map<String, Object>> lid(
            @PathVariable Long lockerId,
            @PathVariable int axis,
            @PathVariable String action,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("CABINET_LID", "Đã gửi lệnh nắp",
                control.lid(lockerId, axis, action, userId, roles));
    }

    /// Chạy nắp N vòng về phía mở/đóng. Body: {"revolutions": 2, "direction": "open"|"close"}.
    @PostMapping("/api/locker-technician/cabinet/{lockerId}/lid/{axis}/jog")
    public ApiResponse<Map<String, Object>> jog(
            @PathVariable Long lockerId,
            @PathVariable int axis,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        double revolutions = body != null && body.get("revolutions") instanceof Number n ? n.doubleValue() : 0;
        String direction = body == null ? null : String.valueOf(body.get("direction"));
        return ApiResponse.ok("CABINET_LID_JOG", "Đã gửi lệnh chạy nắp",
                control.lidJog(lockerId, axis, revolutions, direction, userId, roles));
    }

    /// Chỉnh thông số nắp. Body: {"rps": 1.5, "pulse_us": 1000, "steps_per_rev": 400, ...}.
    @PutMapping("/api/locker-technician/cabinet/{lockerId}/lid/{axis}/tuning")
    public ApiResponse<Map<String, Object>> tune(
            @PathVariable Long lockerId,
            @PathVariable int axis,
            @RequestBody Map<String, Object> changes,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("CABINET_LID_TUNE", "Đã gửi lệnh chỉnh nắp",
                control.lidTune(lockerId, axis, changes, userId, roles));
    }
}
