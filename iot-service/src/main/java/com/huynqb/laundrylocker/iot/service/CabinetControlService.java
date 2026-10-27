package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.dto.CabinetControlLogResponse;
import com.huynqb.laundrylocker.iot.dto.LockerView;
import com.huynqb.laundrylocker.iot.settings.IotRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/// Điều khiển tủ từ admin web: mở ô, điều khiển nắp/thanh trượt (mở/đóng/về gốc/dừng, chạy N vòng,
/// chỉnh tốc độ), xem trạng thái. Gửi lệnh MQTT tới Pi rồi chờ kết quả (cùng cơ chế restart).
///
/// Phân quyền: gateway cho ADMIN và LOCKER_TECHNICIAN tới `/api/locker-technician/cabinet/**`.
/// KTV tủ chỉ điều khiển được tủ mình phụ trách; ADMIN điều khiển mọi tủ.
@Slf4j
@Service
@RequiredArgsConstructor
public class CabinetControlService {

    private static final List<String> LID_ACTIONS = List.of("open", "close", "home", "stop");

    private final LockerMqttService lockerMqttService;
    private final LockerClient lockerClient;
    private final IotRules rules;
    private final CabinetControlLogService controlLogs;
    private final CabinetLayoutLookup layoutLookup;

    public Map<String, Object> openBox(Long lockerId, Long boxId, Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        if (boxId == null) {
            throw new BusinessException("BOX_REQUIRED", "Thiếu mã ô cần mở");
        }
        return logged(lockerId, userId, "BOX_OPEN", null, boxId, null, null,
                await("mở ô", () -> lockerMqttService.sendUnlockCommandAsync(lockerId, boxId)));
    }

    /**
     * Mở thử một ô bằng QR, không cần đơn — để thử tủ thật bằng app khách. Chỉ chạy khi admin bật
     * chế độ thử và cả tài khoản lẫn tủ nằm trong danh sách cho phép (Cài đặt › iot). Không mở ô
     * đang giữ hàng của đơn thật; không tra được sơ đồ tủ thì từ chối.
     */
    public Map<String, Object> testOpenBox(Long lockerId, Long boxId, Long userId) {
        if (!rules.testOpenEnabled()) {
            throw new BusinessException("TEST_OPEN_DISABLED",
                    "Chế độ mở thử ô bằng QR đang tắt", HttpStatus.FORBIDDEN);
        }
        if (!rules.testOpenAllowedUser(userId)) {
            throw new BusinessException("TEST_OPEN_USER_NOT_ALLOWED",
                    "Tài khoản này không nằm trong danh sách được mở thử ô", HttpStatus.FORBIDDEN);
        }
        if (!rules.testOpenAllowedLocker(lockerId)) {
            throw new BusinessException("TEST_OPEN_LOCKER_NOT_ALLOWED",
                    "Tủ này không nằm trong danh sách được mở thử", HttpStatus.FORBIDDEN);
        }
        if (boxId == null) {
            throw new BusinessException("BOX_REQUIRED", "Thiếu mã ô cần mở");
        }
        var layout = layoutLookup.layout(lockerId).orElseThrow(() -> new BusinessException(
                "LOCKER_LOOKUP_FAILED", "Không tra được sơ đồ tủ, chưa mở thử được", HttpStatus.SERVICE_UNAVAILABLE));
        var cell = layout.cells().stream().filter(c -> boxId.equals(c.id())).findFirst()
                .orElseThrow(() -> new BusinessException(
                        "BOX_NOT_IN_LOCKER", "Ô này không thuộc tủ " + lockerId, HttpStatus.NOT_FOUND));
        if ("OCCUPIED".equalsIgnoreCase(cell.status())) {
            throw new BusinessException("BOX_IN_USE",
                    "Ô " + cell.boxNumber() + " đang giữ hàng của một đơn — không mở thử", HttpStatus.CONFLICT);
        }
        return logged(lockerId, userId, "BOX_OPEN_TEST", null, boxId, null, "Mở thử bằng QR (không cần đơn)",
                await("mở ô", () -> lockerMqttService.sendUnlockCommandAsync(lockerId, boxId)));
    }

    public Map<String, Object> lid(Long lockerId, int axis, String action, Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        String act = action == null ? "" : action.trim().toLowerCase();
        if (!LID_ACTIONS.contains(act)) {
            throw new BusinessException("LID_ACTION_INVALID", "Lệnh nắp phải là open, close, home hoặc stop");
        }
        return logged(lockerId, userId, "LID_" + act.toUpperCase(java.util.Locale.ROOT), axis, null, null, null,
                await("nắp " + act, () -> lockerMqttService.sendLidCommandAsync(lockerId, axis, act)));
    }

    public Map<String, Object> lidJog(Long lockerId, int axis, double revolutions, String direction,
                                      Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        String dir = direction == null ? "" : direction.trim().toLowerCase();
        if (!dir.equals("open") && !dir.equals("close")) {
            throw new BusinessException("JOG_DIRECTION_INVALID", "Chiều chạy phải là open hoặc close");
        }
        if (revolutions <= 0) {
            throw new BusinessException("JOG_REVS_INVALID", "Số vòng phải lớn hơn 0");
        }
        return logged(lockerId, userId, "LID_JOG", axis, null, revolutions,
                dir.equals("open") ? "Về phía mở" : "Về phía đóng",
                await("chạy nắp", () -> lockerMqttService.sendLidJogAsync(lockerId, axis, revolutions, dir)));
    }

    public Map<String, Object> lidTune(Long lockerId, int axis, Map<String, Object> changes,
                                       Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        return logged(lockerId, userId, "LID_TUNE", axis, null, null, "Thông số " + changes,
                await("chỉnh nắp", () -> lockerMqttService.sendLidTuneAsync(lockerId, axis, changes)));
    }

    /// Không ghi nhật ký: trang web hỏi trạng thái mỗi 4 giây.
    public Map<String, Object> panel(Long lockerId, Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        return await("trạng thái tủ", () -> lockerMqttService.sendPanelQueryAsync(lockerId));
    }

    /// Nhật ký điều khiển của tủ, mới nhất trước — đọc từ database nên vẫn xem được khi tủ mất mạng.
    public List<CabinetControlLogResponse> logs(Long lockerId, Integer limit, Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        return controlLogs.recent(lockerId, limit);
    }

    private Map<String, Object> logged(Long lockerId, Long userId, String action, Integer axis, Long boxId,
                                       Double revolutions, String detail, Map<String, Object> response) {
        String message = String.valueOf(response.get("message"));
        controlLogs.recordWeb(lockerId, userId, action, axis, boxId, revolutions,
                String.valueOf(response.get("status")), detail == null ? message : detail + " — " + message);
        return response;
    }

    /// ADMIN: mọi tủ. LOCKER_TECHNICIAN: chỉ tủ mình phụ trách. Tủ không tồn tại ⇒ 404.
    void requireLockerInScope(Long lockerId, Long userId, String roles) {
        if (lockerId == null) {
            throw new BusinessException("LOCKER_REQUIRED", "Thiếu mã tủ");
        }
        LockerView locker = null;
        try {
            ApiResponse<List<LockerView>> response = lockerClient.lockers(List.of(lockerId));
            if (response != null && response.data() != null) {
                locker = response.data().stream().filter(l -> l != null && lockerId.equals(l.id())).findFirst().orElse(null);
            }
        } catch (Exception ex) {
            throw new BusinessException("LOCKER_LOOKUP_FAILED", "Không tra được thông tin tủ: " + ex.getMessage());
        }
        if (locker == null) {
            throw new BusinessException("LOCKER_NOT_FOUND", "Không tìm thấy tủ " + lockerId, HttpStatus.NOT_FOUND);
        }
        if (UserRoles.isAdmin(roles)) {
            return;
        }
        if (userId == null || !userId.equals(locker.assignedTechnicianId())) {
            throw new BusinessException("CABINET_FORBIDDEN",
                    "Bạn không phụ trách tủ này nên không điều khiển được", HttpStatus.FORBIDDEN);
        }
    }

    /// Gửi lệnh + chờ kết quả tối đa `unlockWaitSeconds`. Trả {accepted, status, message, result}.
    Map<String, Object> await(String label, Supplier<CompletableFuture<JsonNode>> send) {
        int waitSeconds = rules.unlockWaitSeconds();
        String status;
        String message;
        JsonNode result = null;
        try {
            result = send.get().get(waitSeconds, TimeUnit.SECONDS);
            String reported = result != null && result.hasNonNull("status") ? result.get("status").asText() : null;
            boolean ok = "ACCEPTED".equalsIgnoreCase(reported) || "SUCCESS".equalsIgnoreCase(reported)
                    || "OPEN".equalsIgnoreCase(reported);
            if (ok) {
                status = reported.toUpperCase();
                message = "Tủ đã nhận lệnh " + label;
            } else {
                status = "FAILED";
                message = result != null && result.hasNonNull("errorMessage")
                        ? result.get("errorMessage").asText()
                        : "Tủ không thực hiện được lệnh " + label;
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean timeout = ex instanceof TimeoutException
                    || (ex instanceof ExecutionException && ex.getCause() instanceof TimeoutException);
            if (timeout) {
                status = "TIMEOUT";
                message = "Tủ không phản hồi sau " + waitSeconds + " giây — kiểm tra kết nối/nguồn của tủ";
            } else {
                log.warn("Cabinet command '{}' could not be sent: {}", label, ex.getMessage());
                status = "FAILED";
                message = "Không gửi được lệnh tới tủ — vui lòng thử lại";
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", !"FAILED".equals(status) && !"TIMEOUT".equals(status));
        response.put("status", status);
        response.put("message", message);
        response.put("result", result);
        return response;
    }
}
