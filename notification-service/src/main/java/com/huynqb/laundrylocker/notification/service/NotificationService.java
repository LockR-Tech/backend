package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.notification.dto.AdminBroadcastRequest;
import com.huynqb.laundrylocker.notification.dto.FcmTokenRequest;
import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import com.huynqb.laundrylocker.notification.model.FcmToken;
import com.huynqb.laundrylocker.notification.model.NotificationMessage;
import com.huynqb.laundrylocker.notification.repository.FcmTokenRepository;
import com.huynqb.laundrylocker.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final RabbitTemplate rabbitTemplate;

    /// Topic công khai (mọi người dùng đã đăng nhập) — xem BrokerDestinationGuardInterceptor.
    public static final String LOCKER_AVAILABILITY_TOPIC = "/topic/public/locker-availability";
    private final FcmPushNotificationService fcmPushNotificationService;
    private final WebSocketNotificationService webSocketNotificationService;

    @Transactional
    public NotificationResponse create(NotificationRequest request) {
        return create(request, Map.of());
    }

    /**
     * Tạo notification + đẩy FCM kèm thêm các field [extraData] vào phần data của
     * message (vd noti giao hàng cần orderId/status/eta/message). Field gốc
     * (notificationId/type/referenceType/referenceId) vẫn được giữ; extraData chỉ
     * bổ sung/ghi đè nên client cũ không bị ảnh hưởng.
     */
    @Transactional
    public NotificationResponse create(NotificationRequest request, Map<String, String> extraData) {
        NotificationMessage notification = new NotificationMessage();
        notification.setUserId(request.userId());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setType(StringUtils.hasText(request.type()) ? request.type() : "SYSTEM");
        notification.setReferenceId(request.referenceId());
        notification.setReferenceType(request.referenceType());
        NotificationMessage saved = notificationRepository.save(notification);

        Map<String, String> data = pushData(saved);
        if (extraData != null) {
            data.putAll(extraData);
        }

        fcmPushNotificationService.sendToUser(saved.getUserId(), saved.getTitle(), saved.getMessage(), data);
        NotificationResponse response = toResponse(saved);
        webSocketNotificationService.sendToUser(saved.getUserId(), response);
        publishNotificationRequested(saved);

        if (isOrderRelated(saved.getType(), saved.getReferenceType())) {
            Map<String, Object> orderPayload = new HashMap<>(data);
            orderPayload.put("orderId", saved.getReferenceId());
            orderPayload.put("userId", saved.getUserId());
            orderPayload.put("title", saved.getTitle());
            orderPayload.put("message", saved.getMessage());
            orderPayload.put("status", saved.getType());
            orderPayload.put("timestamp", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
            webSocketNotificationService.sendOrderUpdate(saved.getUserId(), orderPayload);
        }

        return response;
    }

    /// Gửi mọi user đã đăng ký FCM token. Push FCM đi qua create() — mỗi user một lần, kèm
    /// notificationId riêng — nên mỗi thiết bị nhận đúng một push; không gửi thêm push chung.
    @Transactional
    public List<NotificationResponse> broadcast(NotificationRequest request) {
        List<Long> userIds = fcmTokenRepository.findAll().stream().map(FcmToken::getUserId).distinct().toList();
        List<NotificationResponse> responses =
                userIds.stream()
                        .map(userId -> create(new NotificationRequest(userId, request.title(), request.message(), request.type(), request.referenceId(), request.referenceType())))
                        .toList();
        responses.forEach(webSocketNotificationService::broadcast);
        return responses;
    }

    /// Broadcast từ trang admin: không có `userIds` ⇒ như {@link #broadcast(NotificationRequest)};
    /// có `userIds` ⇒ chỉ gửi cho các user đó.
    @Transactional
    public List<NotificationResponse> adminBroadcast(AdminBroadcastRequest request) {
        if (request.userIds() == null) {
            return broadcast(request.template());
        }
        return sendToUsers(request.userIds(), request.template());
    }

    /// Tạo một notification cho từng user trong danh sách (bỏ trùng, bỏ null) rồi đẩy FCM/WebSocket
    /// riêng cho user đó. Bản ghi luôn được tạo kể cả khi user chưa có FCM token, để tin vẫn hiện
    /// trong hộp thư; không gửi lên kênh chung /topic/notifications.
    @Transactional
    public List<NotificationResponse> sendToUsers(List<Long> userIds, NotificationRequest template) {
        return userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(userId -> create(new NotificationRequest(
                        userId, template.title(), template.message(), template.type(),
                        template.referenceId(), template.referenceType())))
                .toList();
    }

    @Transactional
    public void upsertFcmToken(FcmTokenRequest request) {
        FcmToken token =
                fcmTokenRepository
                        .findByToken(request.token())
                        .orElseGet(FcmToken::new);
        token.setUserId(request.userId());
        token.setToken(request.token());
        token.setDeviceType(request.deviceType());
        fcmTokenRepository.save(token);
    }

    @Transactional
    public void deleteFcmToken(Long userId, String token) {
        if (StringUtils.hasText(token)) {
            fcmTokenRepository.deleteByUserIdAndToken(userId, token);
            return;
        }
        fcmTokenRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getByUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> all() {
        return notificationRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadByUser(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /// Đánh dấu đã đọc thay người dùng (trang admin) — không kiểm chủ sở hữu.
    @Transactional
    public NotificationResponse markRead(Long id) {
        return toResponse(markRead(find(id)));
    }

    /// Người dùng chỉ đánh dấu được notification của chính mình; notification của người khác
    /// trả 404 giống như không tồn tại.
    @Transactional
    public NotificationResponse markRead(Long id, Long userId) {
        return toResponse(markRead(findOwned(id, userId)));
    }

    @Transactional
    public List<NotificationResponse> markBatchRead(List<Long> ids, Long userId) {
        return ids.stream().map(id -> markRead(id, userId)).toList();
    }

    /// Gửi lại push FCM của một notification cho đúng người nhận của nó (dùng lại data gốc).
    /// Không tạo bản ghi mới, không đổi trạng thái đã đọc.
    @Transactional(readOnly = true)
    public NotificationResponse resend(Long id) {
        NotificationMessage notification = find(id);
        fcmPushNotificationService.sendToUser(
                notification.getUserId(), notification.getTitle(), notification.getMessage(), pushData(notification));
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void deleteAll(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }

    /// Xoá bất kỳ notification nào (trang admin).
    @Transactional
    public void delete(Long id) {
        if (!notificationRepository.existsById(id)) {
            throw new NotFoundException("Notification", id);
        }
        notificationRepository.deleteById(id);
    }

    /// Người dùng chỉ xoá được notification của chính mình; của người khác trả 404.
    @Transactional
    public void delete(Long id, Long userId) {
        notificationRepository.delete(findOwned(id, userId));
    }

    public Map<String, Object> broadcastTechnicianPresence(Map<String, Object> payload) {
        Map<String, Object> wsMsg = new HashMap<>(payload);
        wsMsg.putIfAbsent("type", DomainEventNames.TECHNICIAN_PRESENCE_UPDATED);
        wsMsg.putIfAbsent("timestamp", java.time.LocalDateTime.now().toString());
        webSocketNotificationService.sendToDestination("/topic/technicians/presence", wsMsg);
        webSocketNotificationService.sendToDestination("/topic/notifications", wsMsg);
        Long techId = asLong(payload.get("userId"));
        if (techId != null) {
            webSocketNotificationService.sendToDestination("/topic/technicians/" + techId + "/presence", wsMsg);
        }
        return wsMsg;
    }

    @Transactional
    public void consumeDomainEvent(DomainEvent event) {
        Map<String, Object> payload = event.payload();

        if (DomainEventNames.LOCKER_LAYOUT_UPDATED.equals(event.type())
                || DomainEventNames.LOCKER_BOX_FAULT.equals(event.type())) {
            Map<String, Object> wsMsg = new HashMap<>(payload);
            wsMsg.put("type", event.type());
            wsMsg.put("timestamp", java.time.LocalDateTime.now().toString());
            webSocketNotificationService.sendToDestination("/topic/notifications", wsMsg);
            webSocketNotificationService.sendToDestination("/topic/lockers", wsMsg);
        }

        if (DomainEventNames.TECHNICIAN_PRESENCE_UPDATED.equals(event.type())) {
            broadcastTechnicianPresence(payload);
        }

        // Số ô trống đổi: chỉ đẩy mã tủ (không có đơn, PIN hay người dùng) cho màn đặt đơn tải lại.
        if (DomainEventNames.LOCKER_AVAILABILITY_CHANGED.equals(event.type())) {
            Map<String, Object> wsMsg = new HashMap<>();
            wsMsg.put("lockerId", payload.get("lockerId"));
            wsMsg.put("timestamp", java.time.LocalDateTime.now().toString());
            webSocketNotificationService.sendToDestination(LOCKER_AVAILABILITY_TOPIC, wsMsg);
            return;
        }

        Long userId = asLong(payload.get("userId"));
        Long referenceId = asLong(payload.containsKey("referenceId") ? payload.get("referenceId") : payload.get("orderId"));

        // Broadcast order events immediately to /topic/orders and user order queue
        if (isOrderDomainEvent(event.type())) {
            Map<String, Object> wsOrderMsg = new HashMap<>(payload);
            wsOrderMsg.put("type", event.type());
            if (referenceId != null) {
                wsOrderMsg.put("orderId", referenceId);
            }
            wsOrderMsg.put("timestamp", java.time.LocalDateTime.now().toString());
            webSocketNotificationService.sendOrderUpdate(userId, wsOrderMsg);
        }

        if (userId == null) {
            log.debug("No userId in {} event, skipping direct user notification", event.type());
            return;
        }
        // Sự kiện không kèm `message` (order.*, payment.*) chỉ để đồng bộ realtime ở trên: service phát
        // sự kiện tự gửi thông báo tiếng Việt chi tiết riêng, tạo thêm ở đây thành ra mỗi việc báo hai lần,
        // một lần là câu tiếng Anh "<type> event received". Riêng nạp ví và thanh toán lỗi chưa nơi nào
        // báo nên soạn nội dung ở đây.
        Object rawMessage = payload.get("message");
        String message = rawMessage != null && StringUtils.hasText(String.valueOf(rawMessage))
                ? String.valueOf(rawMessage)
                : paymentMessage(event.type(), payload);
        if (message == null) {
            log.debug("{} event has no message, realtime update only", event.type());
            return;
        }
        boolean topUp = isTopUp(event.type(), payload);
        if (topUp) {
            referenceId = asLong(payload.get("paymentId"));
        }
        String title = switch (event.type()) {
            case DomainEventNames.ORDER_CREATED -> "Đơn hàng mới";
            case DomainEventNames.ORDER_STATUS_CHANGED -> "Trạng thái đơn hàng thay đổi";
            case DomainEventNames.ORDER_BOX_RELOCATED -> "Đơn hàng được chuyển sang ô mới";
            case DomainEventNames.DELIVERY_STATUS_CHANGED -> "Trạng thái giao hàng thay đổi";
            case DomainEventNames.PAYMENT_COMPLETED -> topUp ? "Nạp ví thành công" : "Thanh toán thành công";
            case DomainEventNames.PAYMENT_FAILED -> topUp ? "Nạp ví không thành công" : "Thanh toán thất bại";
            case DomainEventNames.LOCKER_REPORT_CLAIMED -> "Báo cáo đang được xử lý";
            case DomainEventNames.LOCKER_REPORT_RESOLVED -> "Báo cáo đã được xử lý xong";
            case DomainEventNames.LOCKER_REPORT_ROUTED -> "Phiếu sự cố mới cần xử lý";
            case DomainEventNames.LOCKER_REPORT_ASSIGNED -> "MAINTENANCE_SCHEDULE".equals(payload.get("referenceType"))
                    ? "Lịch kiểm tra mới"
                    : "LOCKER_REPORT_RATING".equals(payload.get("referenceType"))
                            ? "Đánh giá dịch vụ mới"
                            : "LOCKER".equals(payload.get("referenceType"))
                                    ? "Phân công phụ trách tủ"
                                    : "Bạn được giao việc bảo trì";
            case DomainEventNames.LOCKER_SCHEDULE_DUE -> "Lịch kiểm tra định kỳ tới hạn";
            // locker-service gửi sự kiện của KTV drone trên cùng routing key locker.*, type riêng drone.*
            case "drone.report.routed" -> "Phiếu sự cố drone mới cần xử lý";
            case "drone.report.assigned" -> "MAINTENANCE_SCHEDULE".equals(payload.get("referenceType"))
                    ? "Lịch kiểm tra drone mới"
                    : "LOCKER_REPORT_RATING".equals(payload.get("referenceType"))
                            ? "Đánh giá dịch vụ drone"
                            : "Bạn được giao phiếu sự cố drone";
            case "drone.schedule.due" -> "Lịch kiểm tra drone tới hạn";
            default -> "Thông báo";
        };
        String referenceType = String.valueOf(payload.getOrDefault("referenceType", topUp ? "WALLET" : "ORDER"));
        create(new NotificationRequest(userId, title, message, event.type(), referenceId, referenceType));
    }

    /// Nạp ví = giao dịch không gắn đơn (orderId 0 hoặc thiếu).
    private boolean isTopUp(String type, Map<String, Object> payload) {
        if (!DomainEventNames.PAYMENT_COMPLETED.equals(type) && !DomainEventNames.PAYMENT_FAILED.equals(type)) {
            return false;
        }
        Long orderId = asLong(payload.get("orderId"));
        return orderId == null || orderId <= 0;
    }

    /// Nội dung cho các sự kiện thanh toán mà không service nào tự báo: nạp ví (thành công/lỗi) và
    /// thanh toán đơn lỗi. Thanh toán đơn thành công do order-service báo kèm mã đơn ⇒ null.
    private String paymentMessage(String type, Map<String, Object> payload) {
        boolean completed = DomainEventNames.PAYMENT_COMPLETED.equals(type);
        if (!completed && !DomainEventNames.PAYMENT_FAILED.equals(type)) {
            return null;
        }
        String amount = formatVnd(payload.get("amount"));
        if (isTopUp(type, payload)) {
            return completed
                    ? "Đã cộng " + amount + " vào ví LockR."
                    : "Giao dịch nạp " + amount + " vào ví chưa thành công. Vui lòng thử lại.";
        }
        if (completed) {
            return null;
        }
        return "Thanh toán " + amount + " cho đơn #" + asLong(payload.get("orderId"))
                + " chưa thành công. Vui lòng thử lại hoặc chọn phương thức khác.";
    }

    private static String formatVnd(Object amount) {
        try {
            long value = new java.math.BigDecimal(String.valueOf(amount)).setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValueExact();
            return String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,dđ", value);
        } catch (RuntimeException ex) {
            return "khoản tiền";
        }
    }

    private boolean isOrderRelated(String type, String referenceType) {
        if ("ORDER".equalsIgnoreCase(referenceType) || "DELIVERY".equalsIgnoreCase(referenceType)) {
            return true;
        }
        if (type != null) {
            String upper = type.toUpperCase();
            return upper.startsWith("ORDER") || upper.startsWith("DELIVERY") || upper.startsWith("PAYMENT");
        }
        return false;
    }

    private boolean isOrderDomainEvent(String eventType) {
        return DomainEventNames.ORDER_CREATED.equals(eventType)
                || DomainEventNames.ORDER_STATUS_CHANGED.equals(eventType)
                || DomainEventNames.ORDER_BOX_RELOCATED.equals(eventType)
                || DomainEventNames.DELIVERY_STATUS_CHANGED.equals(eventType)
                || DomainEventNames.PAYMENT_COMPLETED.equals(eventType)
                || DomainEventNames.PAYMENT_FAILED.equals(eventType);
    }

    private NotificationMessage find(Long id) {
        return notificationRepository.findById(id).orElseThrow(() -> new NotFoundException("Notification", id));
    }

    private NotificationMessage findOwned(Long id, Long userId) {
        return notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification", id));
    }

    private NotificationMessage markRead(NotificationMessage notification) {
        notification.setIsRead(true);
        notification.setStatus("READ");
        notification.setReadAt(java.time.LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    /// Phần data của message FCM; client mở đúng màn hình theo các field này.
    private Map<String, String> pushData(NotificationMessage notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", notification.getType());
        data.put("referenceType", notification.getReferenceType() == null ? "" : notification.getReferenceType());
        data.put("referenceId", notification.getReferenceId() == null ? "" : String.valueOf(notification.getReferenceId()));
        data.put("title", notification.getTitle() == null ? "" : notification.getTitle());
        data.put("body", notification.getMessage() == null ? "" : notification.getMessage());
        data.put("content", notification.getMessage() == null ? "" : notification.getMessage());
        return data;
    }

    private NotificationResponse toResponse(NotificationMessage notification) {
        return new NotificationResponse(
                notification.getId(), notification.getUserId(), notification.getTitle(), notification.getMessage(),
                notification.getType(), notification.getIsRead(), notification.getReferenceId(), notification.getReferenceType(),
                notification.getStatus(), notification.getReadAt(), notification.getCreatedAt());
    }

    private void publishNotificationRequested(NotificationMessage notification) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.NOTIFICATION_REQUESTED,
                    DomainEvent.of(
                            DomainEventNames.NOTIFICATION_REQUESTED,
                            "notification-service",
                            Map.of("notificationId", notification.getId(), "userId", notification.getUserId())));
        } catch (AmqpException ex) {
            log.warn("Could not publish notification.requested: {}", ex.getMessage());
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text);
        }
        return null;
    }
}
