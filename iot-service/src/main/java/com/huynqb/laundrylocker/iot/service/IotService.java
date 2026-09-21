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
import com.huynqb.laundrylocker.iot.settings.IotRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    /// Chống dò mã và thời gian chờ tủ phản hồi do admin cấu hình (ADR-0005).
    private final IotRules rules;

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
            return denied(request.boxId(), verification.message(), verification.reasonCode());
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = lockerMqttService.sendUnlockCommandAsync(request.lockerId(), request.boxId())
                    .get(rules.unlockWaitSeconds(), java.util.concurrent.TimeUnit.SECONDS);

            if (node.has("status") && "FAILED".equals(node.get("status").asText())) {
                logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", "FAILED", "Hardware failed to open");
                return Map.of("accepted", false, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", "Hardware failed to open");
            }
            lockerClient.openBox(request.boxId());
            logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", "SUCCESS", null);
            afterOpen(verification.orderId(), verification.orderStatus(), verification.orderType(), verification.orderUserId());
            return opened(
                    request.lockerId(), request.boxId(), verification.orderId(),
                    verification.orderType(), verification.orderStatus());
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
                    .get(rules.unlockWaitSeconds(), java.util.concurrent.TimeUnit.SECONDS);
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
            return denied(boxId, verification.message(), verification.reasonCode());
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    lockerMqttService.sendUnlockCommandAsync(request.lockerId(), boxId)
                            .get(rules.unlockWaitSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            if (node.has("status") && "FAILED".equals(node.get("status").asText())) {
                logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", "FAILED", "Hardware failed to open");
                return Map.of("accepted", false, "boxId", boxId, "message", "Hardware failed to open");
            }
            lockerClient.openBox(boxId);
            logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", "SUCCESS", null);
            afterOpen(order.id(), order.status(), order.type(), order.userId());
            return opened(request.lockerId(), boxId, order.id(), order.type(), order.status());
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
            return new VerifyPinResponse(false, null, boxId, null, "Box temporarily locked after repeated failed attempts. Try again later.", null);
        }
        try {
            OrderLookupResponse order = orderClient.getByAccess(code).data();
            if (!isCredentialActive(order.status())) {
                return new VerifyPinResponse(false, order.id(), boxId, order.status(), "Access code is no longer active", order.userId());
            }
            boolean validBox =
                    boxId.equals(order.sendBoxId()) || boxId.equals(order.receiveBoxId());
            if (!validBox) {
                recordFailedAttempt(boxId);
                return new VerifyPinResponse(false, order.id(), boxId, order.status(), "Access code does not match this box", order.userId());
            }
            // Mã đúng nhưng đơn tạm chưa được mở (chưa thanh toán, hết hạn thuê…): từ chối
            // mà KHÔNG tính là nhập sai, để khách không bị khoá ô vì một lỗi không phải dò mã.
            if (order.accessBlockReason() != null) {
                return new VerifyPinResponse(
                        false, order.id(), boxId, order.status(), blockMessage(order.accessBlockReason()),
                        order.userId(), order.type(), order.accessBlockReason());
            }
            resetAttempts(boxId);
            return new VerifyPinResponse(
                    true, order.id(), boxId, order.status(), "Access verified", order.userId(), order.type(), null);
        } catch (Exception ex) {
            recordFailedAttempt(boxId);
            return new VerifyPinResponse(false, null, boxId, null, "Invalid access code", null);
        }
    }

    /// Kiosk: người gửi/người thuê đã mở ô bằng mã và bỏ đồ xong, bấm "Đã bỏ hàng xong".
    /// Kiosk không đăng nhập, nên quyền đến từ chính mã + việc ô của đơn vừa được mở
    /// thành công gần đây; khi đó xác nhận thay mặt chủ đơn.
    public Map<String, Object> confirmDropWithCode(UnlockWithCodeRequest request) {
        OrderLookupResponse order = findOrderAtLocker(request);
        if (order == null) {
            return denied(null, INVALID_CODE_MESSAGE, null);
        }
        if (!"INITIALIZED".equalsIgnoreCase(order.status())) {
            return denied(null, "Đơn này không ở bước bỏ hàng.", "ORDER_STATUS_INVALID");
        }
        if (order.accessBlockReason() != null) {
            return denied(null, blockMessage(order.accessBlockReason()), order.accessBlockReason());
        }
        if (!openedRecently(order.id())) {
            return denied(null, "Vui lòng mở ô bằng mã trước khi xác nhận.", "NOT_OPENED_RECENTLY");
        }
        try {
            OrderLookupResponse updated = orderClient.confirm(order.id(), order.userId()).data();
            return kioskActionDone(order, updated, "Đã xác nhận bỏ hàng. Cảm ơn bạn!");
        } catch (Exception ex) {
            return remoteDenied(ex, "Không xác nhận được, vui lòng thử lại.");
        }
    }

    /// Kiosk: người thuê lấy hết đồ và kết thúc lượt thuê ngay tại tủ.
    public Map<String, Object> endRentalWithCode(UnlockWithCodeRequest request) {
        OrderLookupResponse order = findOrderAtLocker(request);
        if (order == null) {
            return denied(null, INVALID_CODE_MESSAGE, null);
        }
        if (!"RENTAL".equalsIgnoreCase(order.type())) {
            return denied(null, "Mã này không phải của đơn thuê ô.", "ORDER_TYPE_INVALID");
        }
        if (!"STORING".equalsIgnoreCase(order.status()) && !"RETURNED".equalsIgnoreCase(order.status())) {
            return denied(null, "Đơn thuê này không còn hoạt động.", "ORDER_STATUS_INVALID");
        }
        if (!openedRecently(order.id())) {
            return denied(null, "Vui lòng mở ô bằng mã và lấy hết đồ trước khi kết thúc thuê.", "NOT_OPENED_RECENTLY");
        }
        try {
            OrderLookupResponse updated = orderClient.pickupStorage(order.id(), order.userId()).data();
            return kioskActionDone(order, updated, "Đã kết thúc thuê. Ô đã được trả lại.");
        } catch (Exception ex) {
            return remoteDenied(ex, "Không kết thúc thuê được, vui lòng thử lại.");
        }
    }

    private static final String INVALID_CODE_MESSAGE = "Mã không hợp lệ hoặc đã hết hạn";

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private OrderLookupResponse findOrderAtLocker(UnlockWithCodeRequest request) {
        try {
            OrderLookupResponse order = orderClient.getByAccess(request.code()).data();
            if (order == null || order.lockerId() == null || !order.lockerId().equals(request.lockerId())) {
                return null;
            }
            return order;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean openedRecently(Long orderId) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(rules.kioskConfirmWindowMinutes());
        return accessLogRepository.existsByOrderIdAndResultAndCreatedAtAfter(orderId, "SUCCESS", since);
    }

    /// Sau khi mở ô thành công: mở để bỏ hàng thì ghi mốc cho order-service, mở để lấy
    /// hàng thì hoàn tất đơn (trừ đơn thuê).
    private void afterOpen(Long orderId, String orderStatus, String orderType, Long orderUserId) {
        if ("INITIALIZED".equalsIgnoreCase(orderStatus)) {
            if (orderId == null) {
                return;
            }
            try {
                orderClient.dropOpened(orderId);
            } catch (Exception ex) {
                log.warn("Could not record drop-off opening for order {}: {}", orderId, ex.getMessage());
            }
            return;
        }
        completeIfPickup(orderId, orderStatus, orderType, orderUserId);
    }

    /// Bước tiếp theo trên kiosk/app sau khi ô mở: xác nhận bỏ hàng, dùng ô thuê, hay chỉ lấy hàng.
    static String nextStep(String orderType, String orderStatus) {
        if ("INITIALIZED".equalsIgnoreCase(orderStatus)) {
            return "CONFIRM_DROP";
        }
        if ("RENTAL".equalsIgnoreCase(orderType)) {
            return "RENTAL_ACCESS";
        }
        return "PICKUP";
    }

    private static Map<String, Object> opened(
            Long lockerId, Long boxId, Long orderId, String orderType, String orderStatus) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("lockerId", lockerId);
        result.put("boxId", boxId);
        result.put("orderId", orderId);
        result.put("orderType", orderType);
        result.put("orderStatus", orderStatus);
        result.put("nextStep", nextStep(orderType, orderStatus));
        result.put("message", "Unlock command accepted");
        return result;
    }

    private static Map<String, Object> denied(Long boxId, String message, String reasonCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", false);
        if (boxId != null) {
            result.put("boxId", boxId);
        }
        result.put("message", message);
        if (reasonCode != null) {
            result.put("reasonCode", reasonCode);
        }
        return result;
    }

    // Không trả PIN/QR của đơn về kiosk.
    private static Map<String, Object> kioskActionDone(
            OrderLookupResponse before, OrderLookupResponse after, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("orderId", before.id());
        result.put("orderType", before.type());
        result.put("orderStatus", after != null ? after.status() : null);
        result.put("message", message);
        return result;
    }

    /// Lỗi nghiệp vụ từ order-service (VD ORDER_UNPAID) mang theo mã + câu tiếng Việt
    /// trong body JSON — chuyển nguyên cho kiosk thay vì một câu lỗi chung chung.
    private static Map<String, Object> remoteDenied(Exception ex, String fallbackMessage) {
        if (ex instanceof feign.FeignException feignError) {
            try {
                com.fasterxml.jackson.databind.JsonNode body = JSON.readTree(feignError.contentUTF8());
                String code = body.path("code").asText(null);
                String message = body.path("message").asText(null);
                if (message != null && !message.isBlank()) {
                    return denied(null, message, code);
                }
            } catch (Exception ignored) {
                // Body không phải JSON — dùng câu mặc định.
            }
        }
        log.warn("Kiosk order action failed: {}", ex.getMessage());
        return denied(null, fallbackMessage, null);
    }

    static String blockMessage(String reasonCode) {
        return switch (reasonCode) {
            case "ORDER_UNPAID" -> "Đơn chưa thanh toán. Vui lòng thanh toán trên ứng dụng trước khi mở ô.";
            case "RENTAL_EXPIRED" -> "Đã hết thời gian thuê. Vui lòng gia hạn trên ứng dụng để mở ô.";
            case "RENTAL_UNPAID" -> "Còn phí gia hạn chưa thanh toán. Vui lòng thanh toán trên ứng dụng để mở ô.";
            default -> "Mã này tạm thời chưa mở được ô.";
        };
    }

    /// F2-G01: mở khoá để LẤY đồ (đơn đang STORING/RETURNED) trước đây không hoàn tất
    /// đơn — đơn cứ nằm mãi ở STORING, PIN/QR còn dùng lại được tới khi hết hạn thay vì
    /// bị thu hồi ngay. Việc PIN/QR vừa xác thực đúng (verifyAccess) đã chứng minh người
    /// mở có quyền — không cần đợi khách tự bấm "hoàn tất" trên app (họ có thể đang dùng
    /// kiosk, không đăng nhập). Chỉ kích hoạt khi đơn đang STORING/RETURNED — mở để BỎ đồ
    /// (đơn còn INITIALIZED) không được đụng vào.
    ///
    /// Best-effort, có chủ đích: cửa đã mở thật rồi, một lỗi hoàn tất (VD đơn chưa thanh
    /// toán) không được biến phản hồi mở khoá thành "thất bại" — khách vẫn đang đứng
    /// trước tủ đã mở. Nút "Tôi đã lấy đồ — hoàn tất" trên app vẫn còn để tự làm lại.
    ///
    /// Đơn RENTAL dùng PIN nhiều lần tới hạn — mở lại để lấy/cất đồ không được kết thúc
    /// lượt thuê; thuê chỉ kết thúc qua pickup-storage.
    private void completeIfPickup(Long orderId, String orderStatus, String orderType, Long orderUserId) {
        if (orderId == null || orderUserId == null) {
            return;
        }
        if ("RENTAL".equalsIgnoreCase(orderType)) {
            return;
        }
        if (!"STORING".equalsIgnoreCase(orderStatus) && !"RETURNED".equalsIgnoreCase(orderStatus)) {
            return;
        }
        try {
            orderClient.complete(orderId, orderUserId);
        } catch (Exception ex) {
            log.warn("Auto-complete order {} after pickup unlock failed: {}", orderId, ex.getMessage());
        }
    }

    private void recordFailedAttempt(Long boxId) {
        AccessAttempt attempt = accessAttemptRepository.findById(boxId).orElseGet(AccessAttempt::new);
        attempt.setBoxId(boxId);
        attempt.setFailedCount(attempt.getFailedCount() + 1);
        if (attempt.getFailedCount() >= rules.lockoutMaxAttempts()) {
            attempt.setLockedUntil(LocalDateTime.now().plusMinutes(rules.lockoutMinutes()));
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
