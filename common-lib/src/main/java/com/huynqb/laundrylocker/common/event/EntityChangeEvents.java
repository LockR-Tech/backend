package com.huynqb.laundrylocker.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/// Phát sự kiện "thực thể X mã Y vừa đổi" cho màn hình đang mở tải lại — gọi từ entity listener nên bắt
/// được mọi luồng ghi. Chỉ gửi SAU KHI giao dịch commit, mỗi (sự kiện, loại, mã) một lần cho một giao dịch
/// dù giao dịch ghi nhiều lần; rollback thì không gửi. Payload chỉ có `entity`, `id`, `lockerId?` — không
/// mang nội dung bản ghi.
public final class EntityChangeEvents {

    private static final Logger log = LoggerFactory.getLogger(EntityChangeEvents.class);
    private static final String PENDING_KEY = EntityChangeEvents.class.getName() + ".pending";

    private EntityChangeEvents() {
    }

    private record Pending(RabbitTemplate rabbitTemplate, String eventName, String source, Map<String, Object> payload) {
    }

    @SuppressWarnings("unchecked")
    public static void changed(
            RabbitTemplate rabbitTemplate, String eventName, String source, String entity, Long id, Long lockerId) {
        if (rabbitTemplate == null || id == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("entity", entity);
        payload.put("id", id);
        if (lockerId != null) {
            payload.put("lockerId", lockerId);
        }
        Pending event = new Pending(rabbitTemplate, eventName, source, payload);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(event);
            return;
        }
        Map<String, Pending> pending = (Map<String, Pending>) TransactionSynchronizationManager.getResource(PENDING_KEY);
        if (pending == null) {
            Map<String, Pending> batch = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(PENDING_KEY, batch);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    batch.values().forEach(EntityChangeEvents::publish);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_KEY);
                }
            });
            pending = batch;
        }
        pending.putIfAbsent(eventName + ":" + entity + ":" + id, event);
    }

    private static void publish(Pending event) {
        try {
            event.rabbitTemplate().convertAndSend(
                    DomainEventNames.EXCHANGE,
                    event.eventName(),
                    DomainEvent.of(event.eventName(), event.source(), event.payload()));
        } catch (AmqpException ex) {
            log.warn("Could not publish {} for {} {}: {}", event.eventName(),
                    event.payload().get("entity"), event.payload().get("id"), ex.getMessage());
        }
    }
}
