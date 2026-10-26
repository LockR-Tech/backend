package com.huynqb.laundrylocker.payment.service;

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

/// Báo `wallet.updated` sau commit để app của chủ ví tải lại số dư, lịch sử giao dịch và yêu cầu rút —
/// kể cả khi thay đổi do người khác làm (admin duyệt/từ chối rút tiền, admin điều chỉnh ví, hoàn tiền).
/// Có `message` thì notification-service tạo thêm thông báo; không thì chỉ đẩy tin đồng bộ.
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEvents {

    private final RabbitTemplate rabbitTemplate;

    public void changed(Long userId) {
        notify(userId, null, null, null);
    }

    public void notify(Long userId, Long referenceId, String title, String message) {
        if (userId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("referenceType", "WALLET");
        if (referenceId != null) {
            payload.put("referenceId", referenceId);
        }
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
                    DomainEventNames.WALLET_UPDATED,
                    DomainEvent.of(DomainEventNames.WALLET_UPDATED, "payment-service", payload));
        } catch (AmqpException ex) {
            log.warn("Could not publish wallet.updated for user {}: {}", payload.get("userId"), ex.getMessage());
        }
    }
}
