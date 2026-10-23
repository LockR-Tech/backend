package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.iot.client.LockerClient;
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

    public Map<String, Object> openBox(Long lockerId, Long boxId, Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        if (boxId == null) {
            throw new BusinessException("BOX_REQUIRED", "Thiếu mã ô cần mở");
        }
        return await("mở ô", () -> lockerMqttService.sendUnlockCommandAsync(lockerId, boxId));
    }

    public Map<String, Object> lid(Long lockerId, int axis, String action, Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        String act = action == null ? "" : action.trim().toLowerCase();
        if (!LID_ACTIONS.contains(act)) {
            throw new BusinessException("LID_ACTION_INVALID", "Lệnh nắp phải là open, close, home hoặc stop");
        }
        return await("nắp " + act, () -> lockerMqttService.sendLidCommandAsync(lockerId, axis, act));
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
        return await("chạy nắp", () -> lockerMqttService.sendLidJogAsync(lockerId, axis, revolutions, dir));
    }

    public Map<String, Object> lidTune(Long lockerId, int axis, Map<String, Object> changes,
                                       Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        return await("chỉnh nắp", () -> lockerMqttService.sendLidTuneAsync(lockerId, axis, changes));
    }

    public Map<String, Object> panel(Long lockerId, Long userId, String roles) {
        requireLockerInScope(lockerId, userId, roles);
        return await("trạng thái tủ", () -> lockerMqttService.sendPanelQueryAsync(lockerId));
    }

    /// ADMIN: mọi tủ. LOCKER_TECHNICIAN: chỉ tủ mình phụ trách. Tủ không tồn tại ⇒ 404.
    private void requireLockerInScope(Long lockerId, Long userId, String roles) {
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
    private Map<String, Object> await(String label, Supplier<CompletableFuture<JsonNode>> send) {
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
