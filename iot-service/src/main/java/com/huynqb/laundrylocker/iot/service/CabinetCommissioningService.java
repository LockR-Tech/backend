package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.dto.AssignGatewayRequest;
import com.huynqb.laundrylocker.iot.dto.CommissioningStatusResponse;
import com.huynqb.laundrylocker.iot.dto.CommissioningStatusResponse.CommandResult;
import com.huynqb.laundrylocker.iot.dto.GatewayDeviceResponse;
import com.huynqb.laundrylocker.iot.model.CabinetHardwareConfig;
import com.huynqb.laundrylocker.iot.model.GatewayDevice;
import com.huynqb.laundrylocker.iot.repository.CabinetControlLogRepository;
import com.huynqb.laundrylocker.iot.repository.CabinetHardwareConfigRepository;
import com.huynqb.laundrylocker.iot.repository.GatewayDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Lắp đặt & nghiệm thu tủ (F3-G12): KTV lắp đặt gán Pi bằng QR / MAC và locker-service đọc kết quả
/// các lệnh thử để tự xác nhận checklist (không để KTV tự tích các bước máy kiểm được).
@Service
@RequiredArgsConstructor
public class CabinetCommissioningService {

    /// Nội dung QR kiosk hiện khi Pi chưa được gán tủ.
    static final String QR_PREFIX = "LOCKR-PI:";
    static final Set<String> TEST_ACTIONS = Set.of("BOX_OPEN", "BOX_OPEN_TEST", "LID_OPEN", "LID_CLOSE");

    private final GatewayDeviceRepository gateways;
    private final GatewayProvisioningService provisioning;
    private final CabinetHardwareConfigRepository hardwareConfigs;
    private final CabinetControlLogRepository controlLogs;
    private final CabinetControlService control;

    /// API nội bộ cho locker-service: tình trạng bộ điều khiển, cấu hình phần cứng và lệnh thử từ `since`.
    @Transactional(readOnly = true)
    public CommissioningStatusResponse status(Long lockerId, LocalDateTime since) {
        GatewayDeviceResponse controller = gateways.findByLockerId(lockerId).map(provisioning::toResponse).orElse(null);
        CabinetHardwareConfig hardware = hardwareConfigs.findById(lockerId).orElse(null);
        LocalDateTime from = since == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : since;
        List<CommandResult> commands = controlLogs
                .findByLockerIdAndActionInAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(lockerId, TEST_ACTIONS, from)
                .stream()
                .map(log -> new CommandResult(log.getAction(), log.getBoxId(), log.getBoxNumber(), log.getAxis(),
                        log.getStatus(), log.getMessage(), log.getOccurredAt()))
                .toList();
        return new CommissioningStatusResponse(
                lockerId,
                controller,
                hardware == null ? null : hardware.getApplyStatus(),
                hardware == null ? null : hardware.getAppliedAt(),
                hardware == null ? null : hardware.getApplyMessage(),
                commands);
    }

    /// Pi đang online, chưa gán tủ nào (hoặc đã gán đúng tủ này) — để KTV chọn khi không quét được QR.
    @Transactional(readOnly = true)
    public List<GatewayDeviceResponse> candidates(Long lockerId, Long userId, String roles) {
        control.requireLockerInScope(lockerId, userId, roles);
        return gateways.findAllByOrderByIdAsc().stream()
                .map(provisioning::toResponse)
                .filter(GatewayDeviceResponse::online)
                .filter(g -> g.lockerId() == null || lockerId.equals(g.lockerId()))
                .toList();
    }

    /// KTV phụ trách tủ gán Pi theo MAC / QR ⇒ gửi sơ đồ ô, Pi mở thử từng ô và báo kết quả setup.
    @Transactional
    public GatewayDeviceResponse bind(Long lockerId, String rawMac, Long userId, String roles) {
        control.requireLockerInScope(lockerId, userId, roles);
        String mac = GatewayProvisioningService.normalizeMac(stripQrPrefix(rawMac));
        if (mac == null) {
            throw new BusinessException("CONTROLLER_MAC_INVALID",
                    "Mã QR / MAC không hợp lệ — quét QR trên màn kiosk của tủ hoặc nhập MAC dạng AA:BB:CC:DD:EE:FF");
        }
        GatewayDevice device = gateways.findByMacAddress(mac).orElseThrow(() -> new BusinessException(
                "CONTROLLER_NOT_DISCOVERED",
                "Hệ thống chưa thấy bộ điều khiển " + mac + " — bật nguồn Pi, nối mạng rồi chờ khoảng 1 phút",
                HttpStatus.NOT_FOUND));
        if (device.getLockerId() != null && !device.getLockerId().equals(lockerId)) {
            throw new BusinessException("CONTROLLER_ASSIGNED_ELSEWHERE",
                    "Bộ điều khiển " + mac + " đang gắn với tủ khác (#" + device.getLockerId() + ")",
                    HttpStatus.CONFLICT);
        }
        if (!provisioning.toResponse(device).online()) {
            throw new BusinessException("CONTROLLER_OFFLINE",
                    "Bộ điều khiển " + mac + " đang mất kết nối — kiểm tra nguồn và mạng của tủ",
                    HttpStatus.CONFLICT);
        }
        return provisioning.assign(device.getId(), new AssignGatewayRequest(lockerId, true));
    }

    static String stripQrPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.toUpperCase(Locale.ROOT).startsWith(QR_PREFIX) ? value.substring(QR_PREFIX.length()) : value;
    }
}
