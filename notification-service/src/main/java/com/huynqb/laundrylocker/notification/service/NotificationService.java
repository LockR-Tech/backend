package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final RabbitTemplate rabbitTemplate;
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

        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(saved.getId()));
        data.put("type", saved.getType());
        data.put("referenceType", saved.getReferenceType() == null ? "" : saved.getReferenceType());
        data.put("referenceId", saved.getReferenceId() == null ? "" : String.valueOf(saved.getReferenceId()));
        if (extraData != null) {
            data.putAll(extraData);
        }

        fcmPushNotificationService.sendToUser(saved.getUserId(), saved.getTitle(), saved.getMessage(), data);
        webSocketNotificationService.sendToUser(saved.getUserId(), toResponse(saved));
        publishNotificationRequested(saved);
        return toResponse(saved);
    }

    @Transactional
    public List<NotificationResponse> broadcast(NotificationRequest request) {
        List<Long> userIds = fcmTokenRepository.findAll().stream().map(FcmToken::getUserId).distinct().toList();
        List<NotificationResponse> responses =
                userIds.stream()
                        .map(userId -> create(new NotificationRequest(userId, request.title(), request.message(), request.type(), request.referenceId(), request.referenceType())))
                        .toList();
        fcmPushNotificationService.broadcast(request.title(), request.message(), Map.of("type", request.type() == null ? "SYSTEM" : request.type()));
        responses.forEach(webSocketNotificationService::broadcast);
        return responses;
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

    @Transactional
    public NotificationResponse markRead(Long id) {
        NotificationMessage notification =
                notificationRepository.findById(id).orElseThrow(() -> new NotFoundException("Notification", id));
        notification.setIsRead(true);
        notification.setStatus("READ");
        notification.setReadAt(java.time.LocalDateTime.now());
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public List<NotificationResponse> markBatchRead(List<Long> ids) {
        return ids.stream().map(this::markRead).toList();
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void deleteAll(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }

    @Transactional
    public void delete(Long id) {
        if (!notificationRepository.existsById(id)) {
            throw new NotFoundException("Notification", id);
        }
        notificationRepository.deleteById(id);
    }

    @Transactional
    public void consumeDomainEvent(DomainEvent event) {
        Map<String, Object> payload = event.payload();
        Long userId = asLong(payload.get("userId"));
        if (userId == null) {
            log.debug("Ignoring {} without userId", event.type());
            return;
        }
        String title = switch (event.type()) {
            case DomainEventNames.ORDER_STATUS_CHANGED -> "Order status changed";
            case DomainEventNames.PAYMENT_COMPLETED -> "Payment completed";
            case DomainEventNames.PAYMENT_FAILED -> "Payment failed";
            case DomainEventNames.LOCKER_REPORT_CLAIMED -> "Báo cáo đang được xử lý";
            case DomainEventNames.LOCKER_REPORT_RESOLVED -> "Báo cáo đã được xử lý xong";
            default -> "Notification";
        };
        Object message = payload.getOrDefault("message", event.type() + " event received");
        Long referenceId = asLong(payload.containsKey("referenceId") ? payload.get("referenceId") : payload.get("orderId"));
        String referenceType = String.valueOf(payload.getOrDefault("referenceType", "ORDER"));
        create(new NotificationRequest(userId, title, String.valueOf(message), event.type(), referenceId, referenceType));
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
