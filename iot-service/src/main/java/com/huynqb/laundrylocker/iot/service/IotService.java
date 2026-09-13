package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.client.OrderClient;
import com.huynqb.laundrylocker.iot.dto.*;
import com.huynqb.laundrylocker.iot.model.AccessAttempt;
import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import com.huynqb.laundrylocker.iot.model.BoxHardwareStatus;
import com.huynqb.laundrylocker.iot.model.DeviceStatus;
import com.huynqb.laundrylocker.iot.repository.AccessAttemptRepository;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.BoxHardwareStatusRepository;
import com.huynqb.laundrylocker.iot.repository.DeviceStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IotService {

    private final DeviceStatusRepository repository;
    private final BoxAccessLogRepository accessLogRepository;
    private final BoxHardwareStatusRepository boxHardwareStatusRepository;
    private final AccessAttemptRepository accessAttemptRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OrderClient orderClient;
    private final LockerClient lockerClient;
    private final LockerMqttService lockerMqttService;

    @Value("${app.iot.lockout.max-attempts:5}")
    private int lockoutMaxAttempts;

    @Value("${app.iot.lockout.minutes:15}")
    private int lockoutMinutes;


    @Transactional
    public DeviceStatusResponse updateStatus(DeviceStatusRequest request) {
        DeviceStatus device = repository.findByDeviceId(request.deviceId()).orElseGet(DeviceStatus::new);
        device.setDeviceId(request.deviceId());
        device.setLockerId(request.lockerId());
        device.setStatus(request.status().toUpperCase());
        device.setLastSeenAt(LocalDateTime.now());
        DeviceStatus saved = repository.save(device);
        publishDeviceStatus(saved);
        return toResponse(saved);
    }

    public Map<String, Object> unlock(UnlockRequest request, Long actorUserId) {
        VerifyPinResponse verification = verifyPin(new VerifyPinRequest(request.boxId(), request.pinCode()));
        if (!Boolean.TRUE.equals(verification.valid())) {
            logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", "DENIED", verification.message());
            return Map.of("accepted", false, "boxId", request.boxId(), "message", verification.message());
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = lockerMqttService.sendUnlockCommandAsync(request.lockerId(), request.boxId())
                    .get(20, java.util.concurrent.TimeUnit.SECONDS);

            if (node.has("status") && "FAILED".equals(node.get("status").asText())) {
                logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", "FAILED", "Hardware failed to open");
                return Map.of("accepted", false, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", "Hardware failed to open");
            }
            lockerClient.openBox(request.boxId());
            logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", "SUCCESS", null);
            return Map.of("accepted", true, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", "Unlock command accepted");
        } catch (Exception e) {
            log.error("Timeout or error waiting for IoT device", e);
            logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", "TIMEOUT", e.getMessage());
            return Map.of("accepted", false, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", "IoT device timeout");
        }
    }

    /// Booking → IoT sync (GAP 1): mirror a box lifecycle change down to the cabinet.
    /// Best-effort and non-blocking — a down broker just returns published=false and
    /// never breaks the caller's order/booking flow.
    public Map<String, Object> syncBoxState(BoxStateSyncRequest request) {
        boolean published = lockerMqttService.publishBoxStateSync(
                request.lockerId(), request.boxId(), request.state().toUpperCase(), request.orderId());
        return Map.of(
                "published", published,
                "lockerId", request.lockerId(),
                "boxId", request.boxId(),
                "state", request.state().toUpperCase());
    }

    /// Maintenance/admin override: open a box without a customer PIN/QR. Always
    /// audited as credential type MASTER so it's distinguishable from normal opens.
    public Map<String, Object> forceUnlock(ForceUnlockRequest request) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = lockerMqttService.sendUnlockCommandAsync(request.lockerId(), request.boxId())
                    .get(20, java.util.concurrent.TimeUnit.SECONDS);
            if (node.has("status") && "FAILED".equals(node.get("status").asText())) {
                logAccess(request.boxId(), request.lockerId(), null, request.actorUserId(), "MASTER", "FAILED", "Hardware failed to open");
                return Map.of("accepted", false, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", "Hardware failed to open");
            }
            lockerClient.openBox(request.boxId());
            logAccess(request.boxId(), request.lockerId(), null, request.actorUserId(), "MASTER", "SUCCESS", null);
            return Map.of("accepted", true, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", "Force unlock accepted");
        } catch (Exception e) {
            log.error("Timeout or error waiting for IoT device on force-unlock", e);
            logAccess(request.boxId(), request.lockerId(), null, request.actorUserId(), "MASTER", "TIMEOUT", e.getMessage());
            return Map.of("accepted", false, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", "IoT device timeout");
        }
    }

    public VerifyPinResponse verifyPin(VerifyPinRequest request) {
        return verifyAccess(request.boxId(), request.pinCode());
    }

    /// Kiosk tủ mở khóa chỉ bằng mã (PIN/QR/mã ủy quyền) — tra đơn theo mã, suy
    /// ra ô rồi đi cùng đường mở khóa MQTT như unlock thường. Audit riêng bằng
    /// credential ACCESS_CODE để phân biệt với luồng mobile.
    public Map<String, Object> unlockWithCode(UnlockWithCodeRequest request) {
        OrderLookupResponse order;
        try {
            order = orderClient.getByAccess(request.code()).data();
        } catch (Exception ex) {
            return Map.of("accepted", false, "message", "Mã không hợp lệ hoặc đã hết hạn");
        }
        Long boxId = order.receiveBoxId() != null ? order.receiveBoxId() : order.sendBoxId();
        if (boxId == null) {
            return Map.of("accepted", false, "message", "Đơn này không gắn với ô tủ nào");
        }
        if (order.lockerId() == null || !order.lockerId().equals(request.lockerId())) {
            return Map.of("accepted", false, "message", "Mã không hợp lệ hoặc đã hết hạn");
        }
        VerifyPinResponse verification = verifyAccess(boxId, request.code());
        if (!Boolean.TRUE.equals(verification.valid())) {
            logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", "DENIED", verification.message());
            return Map.of("accepted", false, "boxId", boxId, "message", verification.message());
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    lockerMqttService.sendUnlockCommandAsync(request.lockerId(), boxId)
                            .get(20, java.util.concurrent.TimeUnit.SECONDS);
            if (node.has("status") && "FAILED".equals(node.get("status").asText())) {
                logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", "FAILED", "Hardware failed to open");
                return Map.of("accepted", false, "boxId", boxId, "message", "Hardware failed to open");
            }
            lockerClient.openBox(boxId);
            logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", "SUCCESS", null);
            return Map.of(
                    "accepted", true,
                    "lockerId", request.lockerId(),
                    "boxId", boxId,
                    "orderId", order.id(),
                    "orderStatus", String.valueOf(order.status()),
                    "message", "Unlock command accepted");
        } catch (Exception e) {
            log.error("Timeout or error waiting for IoT device on unlock-with-code", e);
            logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", "TIMEOUT", e.getMessage());
            return Map.of("accepted", false, "boxId", boxId, "message", "IoT device timeout");
        }
    }

    /**
     * Accepts either a 6-digit PIN or a signed QR token (LLQR.*) as credential.
     */
    @Transactional
    public VerifyPinResponse verifyAccess(Long boxId, String code) {
        LocalDateTime lockedUntil = accessAttemptRepository.findById(boxId).map(AccessAttempt::getLockedUntil).orElse(null);
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            return new VerifyPinResponse(false, null, boxId, null, "Box temporarily locked after repeated failed attempts. Try again later.");
        }
        try {
            OrderLookupResponse order = orderClient.getByAccess(code).data();
            if (!isCredentialActive(order.status())) {
                return new VerifyPinResponse(false, order.id(), boxId, order.status(), "Access code is no longer active");
            }
            boolean validBox =
                    boxId.equals(order.sendBoxId()) || boxId.equals(order.receiveBoxId());
            if (!validBox) {
                recordFailedAttempt(boxId);
                return new VerifyPinResponse(false, order.id(), boxId, order.status(), "Access code does not match this box");
            }
            resetAttempts(boxId);
            return new VerifyPinResponse(true, order.id(), boxId, order.status(), "Access verified");
        } catch (Exception ex) {
            recordFailedAttempt(boxId);
            return new VerifyPinResponse(false, null, boxId, null, "Invalid access code");
        }
    }

    private void recordFailedAttempt(Long boxId) {
        AccessAttempt attempt = accessAttemptRepository.findById(boxId).orElseGet(AccessAttempt::new);
        attempt.setBoxId(boxId);
        attempt.setFailedCount(attempt.getFailedCount() + 1);
        if (attempt.getFailedCount() >= lockoutMaxAttempts) {
            attempt.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        }
        attempt.setUpdatedAt(LocalDateTime.now());
        accessAttemptRepository.save(attempt);
    }

    private void resetAttempts(Long boxId) {
        accessAttemptRepository.findById(boxId).ifPresent(accessAttemptRepository::delete);
    }

    private boolean isCredentialActive(String status) {
        return "INITIALIZED".equalsIgnoreCase(status)
                || "STORING".equalsIgnoreCase(status)
                || "RETURNED".equalsIgnoreCase(status);
    }

    private void logAccess(
            Long boxId, Long lockerId, Long orderId, Long actorUserId, String credentialType, String result, String message) {
        try {
            BoxAccessLog entry = new BoxAccessLog();
            entry.setBoxId(boxId);
            entry.setLockerId(lockerId);
            entry.setOrderId(orderId);
            entry.setActorUserId(actorUserId);
            entry.setCredentialType(credentialType);
            entry.setResult(result);
            entry.setMessage(message);
            accessLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Could not write box access log for box {}: {}", boxId, ex.getMessage());
        }
    }

    public PickupResponse pickup(PickupRequest request, Long userId) {
        OrderLookupResponse response = orderClient.complete(request.orderId(), userId).data();
        return new PickupResponse(response.id(), response.status(), response.completedAt(), "Pickup confirmed");
    }

    @Transactional(readOnly = true)
    public List<DeviceStatusResponse> listDeviceStatuses() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }


    @Transactional
    public void updateBoxStatus(BoxStatusUpdateRequest request) {
        // GAP 2: persist the cabinet-reported hardware truth, kept separate from the
        // order-driven LockerBox.status (owned by locker-service) — never overwrites it.
        BoxHardwareStatus hw =
                boxHardwareStatusRepository.findById(request.boxId()).orElseGet(BoxHardwareStatus::new);
        hw.setBoxId(request.boxId());
        if (request.lockerId() != null) {
            hw.setLockerId(request.lockerId());
        }
        hw.setHwState(request.status().toUpperCase());
        hw.setLastReportedAt(LocalDateTime.now());
        boxHardwareStatusRepository.save(hw);
        publishRawDeviceStatus(
                "box-" + request.boxId(), request.lockerId(), request.status().toUpperCase(), Map.of("boxId", request.boxId()));
    }

    /// GAP 2: read-only view of cabinet-reported hardware box state for ops
    /// (Manager/Admin) to compare against the order-driven logical status.
    @Transactional(readOnly = true)
    public List<BoxHardwareStatusResponse> listBoxHardwareStatuses(Long lockerId) {
        List<BoxHardwareStatus> rows =
                lockerId == null
                        ? boxHardwareStatusRepository.findAllByOrderByLastReportedAtDesc()
                        : boxHardwareStatusRepository.findByLockerIdOrderByLastReportedAtDesc(lockerId);
        return rows.stream()
                .map(hw -> new BoxHardwareStatusResponse(hw.getBoxId(), hw.getLockerId(), hw.getHwState(), hw.getLastReportedAt()))
                .toList();
    }

    private DeviceStatusResponse toResponse(DeviceStatus device) {
        return new DeviceStatusResponse(device.getId(), device.getDeviceId(), device.getLockerId(), device.getStatus(), device.getLastSeenAt());
    }

    private void publishDeviceStatus(DeviceStatus device) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.IOT_DEVICE_STATUS_CHANGED,
                    DomainEvent.of(
                            DomainEventNames.IOT_DEVICE_STATUS_CHANGED,
                            "iot-service",
                            eventPayload(device.getDeviceId(), device.getLockerId(), device.getStatus(), Map.of())));
        } catch (AmqpException ex) {
            log.warn("Could not publish iot.device.status.changed: {}", ex.getMessage());
        }
    }

    private void publishRawDeviceStatus(String deviceId, Long lockerId, String status, Map<String, Object> extra) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.IOT_DEVICE_STATUS_CHANGED,
                    DomainEvent.of(DomainEventNames.IOT_DEVICE_STATUS_CHANGED, "iot-service", eventPayload(deviceId, lockerId, status, extra)));
        } catch (AmqpException ex) {
            log.warn("Could not publish iot.device.status.changed: {}", ex.getMessage());
        }
    }

    private Map<String, Object> eventPayload(String deviceId, Long lockerId, String status, Map<String, Object> extra) {
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("deviceId", deviceId);
        if (lockerId != null) {
            payload.put("lockerId", lockerId);
        }
        payload.put("status", status);
        payload.putAll(extra);
        return payload;
    }
}
