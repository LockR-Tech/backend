package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.dto.BoxAccessLogResponse;
import com.huynqb.laundrylocker.iot.dto.DeviceStatusResponse;
import com.huynqb.laundrylocker.iot.dto.LockerView;
import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import com.huynqb.laundrylocker.iot.model.DeviceStatus;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.DeviceStatusRepository;
import com.huynqb.laundrylocker.iot.settings.IotRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Thiết bị IoT (bản ghi heartbeat `device_statuses`) cho KTV tủ và admin.
///
/// - `status` trả về là trạng thái đang áp dụng: ONLINE chỉ khi có tín hiệu trong cửa sổ online
///   của bộ điều khiển tủ ({@link GatewayProvisioningService#isOnline}).
/// - KTV chỉ thấy/thao tác thiết bị của tủ mình phụ trách (`lockers.assigned_technician_id`); ADMIN thấy hết.
/// - Đổi trạng thái tay không chạm `lastSeenAt` và không đặt được ONLINE (chỉ heartbeat mới chứng minh online).
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceManagementService {

    /// Trạng thái KTV được đặt tay (ví dụ đã rút nguồn nhưng chưa hết cửa sổ online).
    static final Set<String> MANUAL_STATUSES = Set.of("OFFLINE", "ERROR");
    static final int AUDIT_MESSAGE_MAX = 255;

    private final DeviceStatusRepository deviceStatusRepository;
    private final BoxAccessLogRepository boxAccessLogRepository;
    private final LockerMqttService lockerMqttService;
    private final LockerClient lockerClient;
    /// Thời gian chờ tủ phản hồi lệnh — dùng chung với lệnh mở ô (`app.iot.unlock-wait-seconds`).
    private final IotRules rules;

    // ─── Đọc ───

    /// Admin: mọi thiết bị (tên tủ best-effort). KTV: chỉ thiết bị của tủ mình phụ trách.
    public List<DeviceStatusResponse> listDevices(Long viewerId, String rolesHeader) {
        List<DeviceStatus> devices = deviceStatusRepository.findAll();
        if (UserRoles.isAdmin(rolesHeader)) {
            Map<Long, LockerView> lockers = lockersQuietly();
            return devices.stream().map(device -> toResponse(device, lockers.get(device.getLockerId()))).toList();
        }
        Map<Long, LockerView> mine = requireLockers().values().stream()
                .filter(locker -> viewerId != null && viewerId.equals(locker.assignedTechnicianId()))
                .collect(Collectors.toMap(LockerView::id, Function.identity(), (a, b) -> a));
        return devices.stream()
                .filter(device -> device.getLockerId() != null && mine.containsKey(device.getLockerId()))
                .map(device -> toResponse(device, mine.get(device.getLockerId())))
                .toList();
    }

    /// Bản admin (`/api/admin/iot/device-status`).
    public List<DeviceStatusResponse> listAllDevices() {
        return listDevices(null, "ADMIN");
    }

    public DeviceStatusResponse getDevice(Long id, Long viewerId, String rolesHeader) {
        ScopedDevice scoped = findInScope(id, viewerId, rolesHeader);
        return toResponse(scoped.device(), scoped.locker());
    }

    public List<BoxAccessLogResponse> deviceLogs(Long id, Long viewerId, String rolesHeader) {
        DeviceStatus device = findInScope(id, viewerId, rolesHeader).device();
        if (device.getLockerId() == null) {
            return List.of();
        }
        return boxAccessLogRepository.findByLockerIdOrderByCreatedAtDesc(device.getLockerId()).stream()
                .map(DeviceManagementService::toLogResponse)
                .toList();
    }

    // ─── Thao tác ───

    /// Đánh dấu tay OFFLINE/ERROR. Không cập nhật `lastSeenAt`: "thấy lần cuối" chỉ đến từ thiết bị.
    public DeviceStatusResponse updateStatus(Long id, String status, Long viewerId, String rolesHeader) {
        String target = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("ONLINE".equals(target)) {
            throw new BusinessException(
                    "DEVICE_STATUS_ONLINE_NOT_ALLOWED",
                    "Không đặt tay trạng thái ONLINE — thiết bị tự chuyển ONLINE khi gửi tín hiệu về");
        }
        if (!MANUAL_STATUSES.contains(target)) {
            throw new BusinessException(
                    "DEVICE_STATUS_INVALID", "Trạng thái thiết bị chỉ được đặt OFFLINE hoặc ERROR");
        }
        ScopedDevice scoped = findInScope(id, viewerId, rolesHeader);
        DeviceStatus device = scoped.device();
        device.setStatus(target);
        DeviceStatus saved = deviceStatusRepository.save(device);
        return toResponse(saved, scoped.locker());
    }

    /// Khởi động lại tiến trình điều khiển của tủ qua MQTT `cabinet/{lockerId}/command/restart`, chờ
    /// `…/restart/result` tối đa `unlockWaitSeconds`. Mỗi lần gọi ghi đúng một dòng nhật ký kèm kết quả.
    ///
    /// Kết quả: `accepted` + `status` ACCEPTED (tủ nhận lệnh, sắp khởi động lại) | TIMEOUT (tủ không phản
    /// hồi — có thể đang mất kết nối) | FAILED (không gửi được lệnh hoặc tủ từ chối).
    public Map<String, Object> restart(Long id, Long actorUserId, String rolesHeader) {
        DeviceStatus device = findInScope(id, actorUserId, rolesHeader).device();
        Long lockerId = device.getLockerId();
        if (lockerId == null) {
            throw new BusinessException(
                    "DEVICE_NOT_PAIRED", "Thiết bị chưa gắn với tủ nào — không gửi được lệnh khởi động lại",
                    HttpStatus.CONFLICT);
        }
        int waitSeconds = rules.unlockWaitSeconds();
        String status;
        String message;
        try {
            JsonNode result = lockerMqttService.sendRestartCommandAsync(lockerId).get(waitSeconds, TimeUnit.SECONDS);
            String reported = result != null && result.hasNonNull("status") ? result.get("status").asText() : null;
            if ("ACCEPTED".equalsIgnoreCase(reported)) {
                status = "ACCEPTED";
                message = "Bộ điều khiển tủ đã nhận lệnh và đang khởi động lại";
            } else {
                status = "FAILED";
                message = result != null && result.hasNonNull("errorMessage")
                        ? result.get("errorMessage").asText()
                        : "Bộ điều khiển tủ không thực hiện được lệnh khởi động lại";
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean timeout = ex instanceof TimeoutException
                    || (ex instanceof ExecutionException && ex.getCause() instanceof TimeoutException);
            if (timeout) {
                status = "TIMEOUT";
                message = "Tủ không phản hồi lệnh khởi động lại sau " + waitSeconds
                        + " giây — kiểm tra kết nối mạng/nguồn của tủ";
            } else {
                log.warn("Restart command for locker {} could not be sent: {}", lockerId, ex.getMessage());
                status = "FAILED";
                message = "Không gửi được lệnh khởi động lại tới tủ — vui lòng thử lại";
            }
        }
        audit(lockerId, actorUserId, "RESTART_" + status,
                "Khởi động lại bộ điều khiển " + device.getDeviceId() + ": " + message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", "ACCEPTED".equals(status));
        response.put("status", status);
        response.put("message", message);
        response.put("deviceId", device.getDeviceId());
        response.put("lockerId", lockerId);
        return response;
    }

    // ─── nội bộ ───

    private record ScopedDevice(DeviceStatus device, LockerView locker) {
    }

    private ScopedDevice findInScope(Long id, Long viewerId, String rolesHeader) {
        DeviceStatus device = deviceStatusRepository.findById(id).orElseThrow(() -> new NotFoundException("Device", id));
        if (UserRoles.isAdmin(rolesHeader)) {
            LockerView locker = device.getLockerId() == null ? null : lockersQuietly().get(device.getLockerId());
            return new ScopedDevice(device, locker);
        }
        LockerView locker = device.getLockerId() == null ? null : requireLockers().get(device.getLockerId());
        if (locker == null || viewerId == null || !viewerId.equals(locker.assignedTechnicianId())) {
            throw new BusinessException(
                    "DEVICE_NOT_IN_SCOPE", "Thiết bị thuộc tủ bạn không phụ trách", HttpStatus.FORBIDDEN);
        }
        return new ScopedDevice(device, locker);
    }

    /// KTV: không tra được tủ thì không biết thiết bị nào thuộc quyền ⇒ báo lỗi thay vì trả rỗng.
    private Map<Long, LockerView> requireLockers() {
        try {
            return lockers();
        } catch (RuntimeException ex) {
            log.warn("Cannot load lockers from locker-service: {}", ex.getMessage());
            throw new BusinessException(
                    "LOCKER_SERVICE_UNAVAILABLE", "Chưa tải được danh sách tủ phụ trách, vui lòng thử lại",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Map<Long, LockerView> lockersQuietly() {
        try {
            return lockers();
        } catch (RuntimeException ex) {
            log.debug("Locker names unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<Long, LockerView> lockers() {
        ApiResponse<List<LockerView>> response = lockerClient.lockers(null);
        List<LockerView> lockers = response == null || response.data() == null ? List.of() : response.data();
        return lockers.stream()
                .filter(locker -> locker != null && locker.id() != null)
                .collect(Collectors.toMap(LockerView::id, Function.identity(), (a, b) -> a));
    }

    private void audit(Long lockerId, Long actorUserId, String result, String message) {
        try {
            BoxAccessLog entry = new BoxAccessLog();
            entry.setBoxId(0L);
            entry.setLockerId(lockerId);
            entry.setActorUserId(actorUserId);
            entry.setCredentialType("MASTER");
            entry.setResult(result);
            entry.setMessage(truncate(message, AUDIT_MESSAGE_MAX));
            boxAccessLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Could not write restart audit log for locker {}: {}", lockerId, ex.getMessage());
        }
    }

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    /// ONLINE chỉ khi còn trong cửa sổ online; các trạng thái khác (OFFLINE/ERROR/UNKNOWN) giữ nguyên.
    static String effectiveStatus(DeviceStatus device) {
        String stored = StringUtils.hasText(device.getStatus())
                ? device.getStatus().trim().toUpperCase(Locale.ROOT)
                : "UNKNOWN";
        if ("ONLINE".equals(stored) && !GatewayProvisioningService.isOnline(device.getLastSeenAt())) {
            return "OFFLINE";
        }
        return stored;
    }

    static DeviceStatusResponse toResponse(DeviceStatus device, LockerView locker) {
        String status = effectiveStatus(device);
        boolean sameLocker = locker != null && Objects.equals(locker.id(), device.getLockerId());
        return new DeviceStatusResponse(
                device.getId(), device.getDeviceId(), device.getLockerId(), status, device.getLastSeenAt(),
                sameLocker ? locker.code() : null,
                sameLocker ? locker.name() : null,
                "ONLINE".equals(status));
    }

    private static BoxAccessLogResponse toLogResponse(BoxAccessLog l) {
        return new BoxAccessLogResponse(
                l.getId(), l.getBoxId(), l.getLockerId(), l.getOrderId(),
                l.getActorUserId(), l.getCredentialType(), l.getResult(),
                l.getMessage(), l.getCreatedAt());
    }
}
