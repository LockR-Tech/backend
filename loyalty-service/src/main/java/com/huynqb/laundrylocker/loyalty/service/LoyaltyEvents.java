package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/// Báo `loyalty.points.changed` sau commit để app của chủ tài khoản tải lại điểm/tem/hạng — điểm đổi ngoài
/// tay khách (cộng khi đơn hoàn tất, thu hồi khi hoàn tiền, admin điều chỉnh) trước đây chỉ thấy khi tự làm
/// mới. Có `message` thì notification-service tạo thêm thông báo; không thì chỉ đẩy tin đồng bộ.
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyEvents {

    private final RabbitTemplate rabbitTemplate;

    public void changed(Long userId) {
        notify(userId, null, null);
    }

    public void notify(Long userId, String title, String message) {
        if (userId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("referenceType", "LOYALTY");
        if (title != null) {
            payload.put("title", title);
        }
        if (message != null) {
            payload.put("message", message);
        }
        AfterCommit.run(() -> publish(payload));
    }

    private void publish(Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.LOYALTY_POINTS_CHANGED,
                    DomainEvent.of(DomainEventNames.LOYALTY_POINTS_CHANGED, "loyalty-service", payload));
        } catch (AmqpException ex) {
            log.warn("Could not publish loyalty.points.changed for user {}: {}", payload.get("userId"), ex.getMessage());
        }
    }
}
