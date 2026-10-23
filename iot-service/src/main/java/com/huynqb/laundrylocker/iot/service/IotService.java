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
import com.huynqb.laundrylocker.iot.repository.GatewayDeviceRepository;
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
import java.util.Set;

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
    private final GatewayDeviceRepository gatewayDeviceRepository;

    @Transactional
    public DeviceStatusResponse updateStatus(DeviceStatusRequest request) {
        DeviceStatus device = repository.findByDeviceId(request.deviceId()).orElseGet(DeviceStatus::new);
        device.setDeviceId(request.deviceId());
        device.setLockerId(request.lockerId());
        device.setStatus(request.status().toUpperCase());
        device.setLastSeenAt(LocalDateTime.now());
        DeviceStatus saved = repository.save(device);
        publishDeviceStatus(saved);
        return DeviceManagementService.toResponse(saved, null);
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
                String errCode = node.hasNonNull("errorCode") ? node.get("errorCode").asText() : "HARDWARE_FAULT";
                String errMsg = node.hasNonNull("errorMessage") ? node.get("errorMessage").asText() : "Phần cứng không mở được chốt ô";
                String res = "JAMMED".equalsIgnoreCase(errCode) ? "JAMMED" : "FAILED";
                logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", res, "Lỗi phần cứng [" + errCode + "]: " + errMsg);
                reportHardwareFault(request.boxId(), errCode, errMsg);
                return Map.of("accepted", false, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", errMsg);
            }
            lockerClient.openBox(request.boxId());
            logAccess(request.boxId(), request.lockerId(), verification.orderId(), actorUserId, "PIN_OR_QR", "SUCCESS", null);
            afterOpen(verification.orderId(), verification.orderStatus(), verification.orderType(), verification.orderUserId());
            return opened(
                    request.lockerId(), request.boxId(), verification.orderId(),
                    verification.orderType(), verification.orderStatus());
        } catch (Exception e) {
            String message = logUnlockNotConfirmed(e, request.boxId(), request.lockerId(), verification.orderId(),
                    actorUserId, "PIN_OR_QR");
            return Map.of("accepted", false, "lockerId", request.lockerId(), "boxId", request.boxId(), "message", message);
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
    ///
    /// Kết quả luôn có `accepted` + `status`: OPENED (tủ xác nhận mở), TIMEOUT (tủ không phản hồi
    /// trong `unlockWaitSeconds` — ô có thể chưa mở), FAILED/JAMMED (gửi lệnh lỗi hoặc tủ báo lỗi).
    /// Chỉ OPENED mới báo locker-service "ô đã mở"; trước đây timeout vẫn trả accepted=true.
    /// Lý do/ghi chú KTV nhập (nếu có) đi kèm mọi dòng nhật ký của lần mở này.
    public Map<String, Object> forceUnlock(ForceUnlockRequest request) {
        int waitSeconds = rules.unlockWaitSeconds();
        com.fasterxml.jackson.databind.JsonNode node;
        try {
            node = lockerMqttService.sendUnlockCommandAsync(request.lockerId(), request.boxId())
                    .get(waitSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean timeout = e instanceof java.util.concurrent.TimeoutException
                    || e.getCause() instanceof java.util.concurrent.TimeoutException;
            if (timeout) {
                log.warn("Force-unlock of box {} timed out after {}s", request.boxId(), waitSeconds);
                logAccess(request.boxId(), request.lockerId(), null, request.actorUserId(), "MASTER", "TIMEOUT",
                        forceOpenMessage("Lỗi kết nối bộ điều khiển tủ: Quá thời gian chờ mở khẩn cấp (Timeout "
                                + waitSeconds + "s)", request));
                return forceUnlockResult(request, false, "TIMEOUT",
                        "Tủ không phản hồi lệnh mở khẩn cấp sau " + waitSeconds
                                + " giây — ô có thể chưa mở, vui lòng kiểm tra tại tủ rồi thử lại");
            }
            log.error("Force-unlock command for box {} could not be sent", request.boxId(), e);
            logAccess(request.boxId(), request.lockerId(), null, request.actorUserId(), "MASTER", "FAILED",
                    forceOpenMessage("Lỗi kết nối bộ điều khiển tủ: không gửi được lệnh mở khẩn cấp", request));
            return forceUnlockResult(request, false, "FAILED",
                    "Không gửi được lệnh mở ô tới tủ — ô chưa mở, vui lòng thử lại");
        }
        if (node != null && node.has("status") && "FAILED".equals(node.get("status").asText())) {
            String errCode = node.hasNonNull("errorCode") ? node.get("errorCode").asText() : "HARDWARE_FAULT";
            String errMsg = node.hasNonNull("errorMessage") ? node.get("errorMessage").asText() : "Phần cứng không mở được chốt ô";
            String res = "JAMMED".equalsIgnoreCase(errCode) ? "JAMMED" : "FAILED";
            logAccess(request.boxId(), request.lockerId(), null, request.actorUserId(), "MASTER", res,
                    forceOpenMessage("Lỗi phần cứng khẩn cấp [" + errCode + "]: " + errMsg, request));
            reportHardwareFault(request.boxId(), errCode, errMsg);
            return forceUnlockResult(request, false, res, errMsg);
        }
        logAccess(request.boxId(), request.lockerId(), null, request.actorUserId(), "MASTER", "SUCCESS",
                forceOpenMessage(null, request));
        try {
            lockerClient.openBox(request.boxId());
        } catch (Exception ex) {
            // Cửa đã mở thật — lỗi phát sự kiện bên locker-service không đổi kết quả trả về.
            log.warn("Could not notify locker-service that box {} opened: {}", request.boxId(), ex.getMessage());
        }
        return forceUnlockResult(request, true, "OPENED", "Đã mở ô khẩn cấp");
    }

    static final int ACCESS_LOG_MESSAGE_MAX = 255;

    /// `box_access_logs.message` cho lần mở khẩn cấp: kết quả + "Lý do: …" + "Ghi chú: …" (cắt 255 ký tự).
    /// Web admin cũ không gửi lý do ⇒ giữ nguyên như trước (lần mở thành công không có message).
    static String forceOpenMessage(String outcome, ForceUnlockRequest request) {
        List<String> parts = new java.util.ArrayList<>();
        boolean hasReason = request.reason() != null && !request.reason().isBlank();
        boolean hasNote = request.note() != null && !request.note().isBlank();
        if (outcome != null) {
            parts.add(outcome);
        } else if (hasReason || hasNote) {
            parts.add("Mở khẩn cấp");
        }
        if (hasReason) {
            parts.add("Lý do: " + request.reason().trim());
        }
        if (hasNote) {
            parts.add("Ghi chú: " + request.note().trim());
        }
        return parts.isEmpty() ? null : DeviceManagementService.truncate(String.join(" · ", parts), ACCESS_LOG_MESSAGE_MAX);
    }

    /// Mã lỗi Pi gắn với chính ô (mqtt-contract.md § 2.2). Lỗi cấu hình/kết nối cả tủ (UNKNOWN_SLOT,
    /// INVALID_SLOT, GPIO_NOT_STARTED, TIMEOUT, SERIAL_ERROR…) chỉ ghi nhật ký, không đánh hỏng từng ô.
    static final Set<String> BOX_FAULT_ERROR_CODES = Set.of("JAMMED", "HW_ERROR", "HARDWARE_FAULT");

    /// F3.07: tủ báo mở ô thất bại vì lỗi của ô ⇒ locker-service đưa ô về FAULT và mở (hoặc gộp vào)
    /// phiếu sự cố cho KTV phụ trách tủ. Chạy nền, lỗi chỉ ghi log — không đổi phản hồi đang trả.
    private void reportHardwareFault(Long boxId, String errorCode, String errorMessage) {
        if (boxId == null || errorCode == null || !BOX_FAULT_ERROR_CODES.contains(errorCode.toUpperCase(java.util.Locale.ROOT))) {
            return;
        }
        String reason = DeviceManagementService.truncate(
                "Tủ báo mở ô thất bại [" + errorCode + "]: " + errorMessage, 2000);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                lockerClient.markFault(boxId, Map.of("reason", reason));
            } catch (Exception ex) {
                log.warn("Could not report hardware fault of box {} to locker-service: {}", boxId, ex.getMessage());
            }
        });
    }

    private static Map<String, Object> forceUnlockResult(
            ForceUnlockRequest request, boolean accepted, String status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("status", status);
        result.put("lockerId", request.lockerId());
        result.put("boxId", request.boxId());
        result.put("message", message);
        return result;
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
            if (isOrderLookupUnavailable(ex)) {
                log.warn("Order lookup unavailable on unlock-with-code at locker {}: {}", request.lockerId(), ex.getMessage());
                return denied(null, ORDER_LOOKUP_UNAVAILABLE_MESSAGE, ORDER_LOOKUP_UNAVAILABLE);
            }
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
                String errCode = node.hasNonNull("errorCode") ? node.get("errorCode").asText() : "HARDWARE_FAULT";
                String errMsg = node.hasNonNull("errorMessage") ? node.get("errorMessage").asText() : "Phần cứng không mở được chốt ô";
                String res = "JAMMED".equalsIgnoreCase(errCode) ? "JAMMED" : "FAILED";
                logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", res, "Lỗi phần cứng Kiosk [" + errCode + "]: " + errMsg);
                reportHardwareFault(boxId, errCode, errMsg);
                return Map.of("accepted", false, "boxId", boxId, "message", errMsg);
            }
            lockerClient.openBox(boxId);
            logAccess(boxId, request.lockerId(), order.id(), null, "ACCESS_CODE", "SUCCESS", null);
            afterOpen(order.id(), order.status(), order.type(), order.userId());
            return opened(request.lockerId(), boxId, order.id(), order.type(), order.status());
        } catch (Exception e) {
            String message = logUnlockNotConfirmed(e, boxId, request.lockerId(), order.id(), null, "ACCESS_CODE");
            return Map.of("accepted", false, "boxId", boxId, "message", message);
        }
    }

    /**
     * Accepts either a 6-digit PIN or a signed QR token (LLQR.*) as credential.
     */
    @Transactional
    public VerifyPinResponse verifyAccess(Long boxId, String code) {
        LocalDateTime lockedUntil = accessAttemptRepository.findById(boxId).map(AccessAttempt::getLockedUntil).orElse(null);
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            long remainingMinutes = Math.max(1, java.time.Duration.between(LocalDateTime.now(), lockedUntil).toMinutes() + 1);
            String msg = "Nhập sai quá " + rules.lockoutMaxAttempts() + " lần! Ô tủ bị tạm khóa "
                    + rules.lockoutMinutes() + " phút (còn khoảng " + remainingMinutes + " phút). "
                    + "Vui lòng vào App Mobile để \"Cấp lại mã PIN\" hoặc thử lại sau.";
            return new VerifyPinResponse(false, null, boxId, null, msg, null, null, "E_IOT_PIN_LOCKED");
        }
        try {
            OrderLookupResponse order = orderClient.getByAccess(code).data();
            if (!isCredentialActive(order.status())) {
                return new VerifyPinResponse(false, order.id(), boxId, order.status(), "Access code is no longer active", order.userId());
            }
            boolean validBox =
                    boxId.equals(order.sendBoxId()) || boxId.equals(order.receiveBoxId());
            if (!validBox) {
                boolean locked = recordFailedAttempt(boxId);
                if (locked) {
                    String msg = "Nhập sai quá " + rules.lockoutMaxAttempts() + " lần! Ô tủ bị tạm khóa "
                            + rules.lockoutMinutes() + " phút. Vui lòng vào App Mobile để \"Cấp lại mã PIN\" hoặc thử lại sau.";
                    return new VerifyPinResponse(false, null, boxId, null, msg, null, null, "E_IOT_PIN_LOCKED");
                }
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
            // Không tra được đơn (order-service sập, mất kết nối) không phải lỗi của khách: không tính
            // là nhập sai, nếu không mã đúng cũng làm ô bị khoá suốt lúc order-service không trả lời.
            if (isOrderLookupUnavailable(ex)) {
                log.warn("Order lookup unavailable while verifying box {}: {}", boxId, ex.getMessage());
                return new VerifyPinResponse(
                        false, null, boxId, null, ORDER_LOOKUP_UNAVAILABLE_MESSAGE, null, null, ORDER_LOOKUP_UNAVAILABLE);
            }
            boolean locked = recordFailedAttempt(boxId);
            if (locked) {
                String msg = "Nhập sai quá " + rules.lockoutMaxAttempts() + " lần! Ô tủ bị tạm khóa "
                        + rules.lockoutMinutes() + " phút. Vui lòng vào App Mobile để \"Cấp lại mã PIN\" hoặc thử lại sau.";
                return new VerifyPinResponse(false, null, boxId, null, msg, null, null, "E_IOT_PIN_LOCKED");
            }
            return new VerifyPinResponse(false, null, boxId, null, "Invalid access code", null);
        }
    }

    /// order-service trả 4xx khi mã sai / không còn hiệu lực. Mọi lỗi khác — gateway nội bộ không còn
    /// instance (503), mất kết nối, quá giờ (`RetryableException`, status -1) — là hệ thống không tra
    /// được đơn.
    static boolean isOrderLookupUnavailable(Exception ex) {
        if (ex instanceof feign.FeignException feignError) {
            return feignError.status() < 400 || feignError.status() >= 500;
        }
        return true;
    }

    /// Kiosk: người gửi/người thuê đã mở ô bằng mã và bỏ đồ xong, bấm "Đã bỏ hàng xong".
    /// Kiosk không đăng nhập, nên quyền đến từ chính mã + việc ô của đơn vừa được mở
    /// thành công gần đây; khi đó xác nhận thay mặt chủ đơn.
    public Map<String, Object> confirmDropWithCode(UnlockWithCodeRequest request) {
        OrderLookupResponse order;
        try {
            order = findOrderAtLocker(request);
        } catch (OrderLookupUnavailableException ex) {
            return denied(null, ORDER_LOOKUP_UNAVAILABLE_MESSAGE, ORDER_LOOKUP_UNAVAILABLE);
        }
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
        OrderLookupResponse order;
        try {
            order = findOrderAtLocker(request);
        } catch (OrderLookupUnavailableException ex) {
            return denied(null, ORDER_LOOKUP_UNAVAILABLE_MESSAGE, ORDER_LOOKUP_UNAVAILABLE);
        }
        if (order == null) {
            return denied(null, INVALID_CODE_MESSAGE, null);
        }
        if (!"RENTAL".equalsIgnoreCase(order.type())) {
            return denied(null, "Mã này không phải của đơn thuê ô.", "ORDER_TYPE_INVALID");
        }
        if (!"STORING".equalsIgnoreCase(order.status()) && !"RETURNED".equalsIgnoreCase(order.status())) {
            return denied(null, "Đơn thuê này không còn hoạt động.", "ORDER_STATUS_INVALID");
        }
        // Ô đang chờ KTV: kết thúc thuê sẽ nhả ô trong khi đồ còn kẹt bên trong.
        if ("BOX_INCIDENT_OPEN".equals(order.accessBlockReason())) {
            return denied(null, blockMessage(order.accessBlockReason()), order.accessBlockReason());
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
    static final String ORDER_LOOKUP_UNAVAILABLE = "ORDER_LOOKUP_UNAVAILABLE";
    static final String ORDER_LOOKUP_UNAVAILABLE_MESSAGE =
            "Hệ thống đang bận, chưa kiểm tra được mã. Vui lòng thử lại sau ít phút.";

    /// Không tra được đơn vì order-service không trả lời (khác với mã sai).
    private static final class OrderLookupUnavailableException extends RuntimeException {
        OrderLookupUnavailableException(Throwable cause) {
            super(cause);
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /// null khi mã sai hoặc không thuộc tủ này; ném [OrderLookupUnavailableException] khi order-service
    /// không trả lời.
    private OrderLookupResponse findOrderAtLocker(UnlockWithCodeRequest request) {
        try {
            OrderLookupResponse order = orderClient.getByAccess(request.code()).data();
            if (order == null || order.lockerId() == null || !order.lockerId().equals(request.lockerId())) {
                return null;
            }
            return order;
        } catch (Exception ex) {
            if (isOrderLookupUnavailable(ex)) {
                log.warn("Order lookup unavailable at locker {}: {}", request.lockerId(), ex.getMessage());
                throw new OrderLookupUnavailableException(ex);
            }
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
            case "RENTAL_EXPIRED" -> "Đã hết thời gian thuê. Vui lòng thanh toán phí quá giờ trên ứng dụng để mở ô.";
            case "OVERTIME_FEE_UNPAID" -> "Đơn đã quá hạn lấy hàng. Vui lòng thanh toán phụ phí quá hạn trên ứng dụng để mở ô.";
            case "RENTAL_UNPAID" -> "Còn phí gia hạn chưa thanh toán. Vui lòng thanh toán trên ứng dụng để mở ô.";
            case "BOX_INCIDENT_OPEN" -> "Ô này đang chờ kỹ thuật viên xử lý sự cố. Đồ của bạn vẫn an toàn trong ô và "
                    + "phí quá giờ đang tạm dừng — Lock.R sẽ báo khi lấy được đồ.";
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

    private boolean recordFailedAttempt(Long boxId) {
        AccessAttempt attempt = accessAttemptRepository.findById(boxId).orElseGet(AccessAttempt::new);
        attempt.setBoxId(boxId);
        attempt.setFailedCount(attempt.getFailedCount() + 1);
        boolean newlyLocked = false;
        if (attempt.getFailedCount() >= rules.lockoutMaxAttempts()) {
            attempt.setLockedUntil(LocalDateTime.now().plusMinutes(rules.lockoutMinutes()));
            newlyLocked = true;
        }
        attempt.setUpdatedAt(LocalDateTime.now());
        accessAttemptRepository.save(attempt);
        return newlyLocked;
    }

    private void resetAttempts(Long boxId) {
        accessAttemptRepository.findById(boxId).ifPresent(accessAttemptRepository::delete);
    }

    private boolean isCredentialActive(String status) {
        return "INITIALIZED".equalsIgnoreCase(status)
                || "STORING".equalsIgnoreCase(status)
                || "RETURNED".equalsIgnoreCase(status);
    }

    public void logExternalAccess(
            Long boxId, Long lockerId, Long orderId, Long actorUserId, String credentialType, String result, String message) {
        logAccess(boxId, lockerId, orderId, actorUserId, credentialType, result, message);
    }

    static final String UNLOCK_TIMEOUT_MESSAGE = "Tủ không phản hồi lệnh mở, ô có thể chưa mở. Vui lòng thử lại.";
    static final String UNLOCK_SEND_FAILED_MESSAGE = "Không gửi được lệnh mở tới tủ, ô chưa mở. Vui lòng thử lại.";
    static final String UNLOCK_SERVER_ERROR_MESSAGE = "Có lỗi khi xử lý lệnh mở ô. Vui lòng thử lại hoặc liên hệ hỗ trợ.";

    /// Lệnh mở ô bằng mã không được tủ xác nhận: ghi nhật ký đúng nguyên nhân và trả câu cho khách.
    /// Tách ba trường hợp mà trước đây đều ghi là "timeout": tủ không trả lời trong thời gian chờ,
    /// không gửi được lệnh (broker/MQTT), và lỗi phía máy chủ sau khi gửi lệnh.
    String logUnlockNotConfirmed(
            Exception e, Long boxId, Long lockerId, Long orderId, Long actorUserId, String credentialType) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        int waitSeconds = rules.unlockWaitSeconds();
        if (e instanceof java.util.concurrent.TimeoutException
                || e.getCause() instanceof java.util.concurrent.TimeoutException) {
            log.warn("Unlock of box {} not confirmed by the cabinet after {}s", boxId, waitSeconds);
            logAccess(boxId, lockerId, orderId, actorUserId, credentialType, "TIMEOUT",
                    "Tủ không phản hồi lệnh mở sau " + waitSeconds + " giây (Timeout " + waitSeconds
                            + "s) — bộ điều khiển có thể mất điện hoặc mất mạng; ô có thể chưa mở");
            return UNLOCK_TIMEOUT_MESSAGE;
        }
        if (e instanceof java.util.concurrent.ExecutionException || e instanceof InterruptedException) {
            log.error("Unlock command for box {} could not be sent", boxId, e);
            logAccess(boxId, lockerId, orderId, actorUserId, credentialType, "FAILED",
                    "Không gửi được lệnh mở tới bộ điều khiển tủ (kết nối MQTT lỗi) — ô chưa mở");
            return UNLOCK_SEND_FAILED_MESSAGE;
        }
        log.error("Unlock of box {} failed while processing", boxId, e);
        logAccess(boxId, lockerId, orderId, actorUserId, credentialType, "FAILED",
                DeviceManagementService.truncate("Lỗi máy chủ khi xử lý lệnh mở ô (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")", ACCESS_LOG_MESSAGE_MAX));
        return UNLOCK_SERVER_ERROR_MESSAGE;
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

    /// Chỉ ĐỌC nhật ký: sự kiện kết nối/mất kết nối của bộ điều khiển được ghi lúc nhận heartbeat/
    /// discovery và lúc quét mất kết nối (GatewayProvisioningService), không ghi trong GET này.
    @Transactional(readOnly = true)
    public List<BoxAccessLogResponse> getLockerLogs(Long lockerId) {
        List<BoxAccessLog> dbLogs = accessLogRepository.findByLockerIdOrderByCreatedAtDesc(lockerId);
        List<BoxAccessLogResponse> list = new java.util.ArrayList<>();
        for (BoxAccessLog l : dbLogs) {
            list.add(new BoxAccessLogResponse(
                    l.getId(),
                    l.getBoxId(),
                    l.getLockerId(),
                    l.getOrderId(),
                    l.getActorUserId(),
                    l.getCredentialType(),
                    l.getResult(),
                    l.getMessage(),
                    l.getCreatedAt()));
        }

        // Cảnh báo kiểm tra sơ đồ phần cứng gần nhất (dòng ảo, không lưu DB).
        if (gatewayDeviceRepository != null) {
            gatewayDeviceRepository.findByLockerId(lockerId).ifPresent(gw -> {
                final LocalDateTime seen = (gw.getLastSeenAt() != null) ? gw.getLastSeenAt()
                        : (gw.getUpdatedAt() != null) ? gw.getUpdatedAt()
                        : (gw.getCreatedAt() != null) ? gw.getCreatedAt() : LocalDateTime.now();

                if ("FAILED".equalsIgnoreCase(gw.getSetupStatus()) || "PARTIAL".equalsIgnoreCase(gw.getSetupStatus())) {
                    list.add(new BoxAccessLogResponse(
                            -3L,
                            0L,
                            lockerId,
                            null,
                            null,
                            "HARDWARE",
                            "FAILED",
                            "Cảnh báo phần cứng: Lệnh kiểm tra sơ đồ " + gw.getSetupStatus() + " (" + gw.getSetupProgress() + ")",
                            gw.getSetupFinishedAt() != null ? gw.getSetupFinishedAt() : seen
                    ));
                }
            });
        }

        list.sort((a, b) -> {
            if (a.createdAt() == null || b.createdAt() == null) return 0;
            return b.createdAt().compareTo(a.createdAt());
        });

        return list;
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

        // Ghi nhận cảnh báo nếu cảm biến ô báo lỗi phần cứng kẹt chốt hoặc bất thường
        if (Set.of("JAMMED", "ERROR", "FAULT", "TAMPERED", "SENSOR_ERROR").contains(hw.getHwState())) {
            logAccess(request.boxId(), request.lockerId(), null, null, "HARDWARE", "JAMMED",
                    "Cảm biến báo lỗi phần cứng: Ô kẹt chốt hoặc lỗi hành trình [" + hw.getHwState() + "]");
        }
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
