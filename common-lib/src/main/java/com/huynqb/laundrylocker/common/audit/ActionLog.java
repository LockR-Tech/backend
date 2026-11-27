package com.huynqb.laundrylocker.common.audit;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// Nhật ký thao tác (F3-G16) — "ai, lúc nào, làm gì, với cái gì, từ → thành, lý do". Service gọi
/// {@link #record} ngay chỗ đổi dữ liệu; người thao tác lấy từ {@link RequestActor} (null = hệ thống tự làm).
/// Gửi sự kiện {@link DomainEventNames#ADMIN_ACTION_LOGGED} SAU commit (rollback thì không ghi); user-service lưu
/// và trả `/api/admin/audit-logs`. Mất kết nối RabbitMQ thì chỉ ghi log cảnh báo — không làm hỏng thao tác chính.
///
/// Mã hành động (`action`) là chuỗi in hoa ổn định để web dịch (vd `LOCKER_MAINTENANCE_ON`); `targetLabel` là tên
/// dễ đọc lúc ghi (mã tủ, số ô, mã drone…) để nhật ký vẫn đọc được khi đối tượng đã bị xoá.
@Component
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
public class ActionLog {

    private static final Logger log = LoggerFactory.getLogger(ActionLog.class);
    private static final int MAX_VALUE = 500;
    private static final int MAX_NOTE = 2000;

    private final ObjectProvider<RabbitTemplate> rabbitTemplate;
    private final String source;

    public ActionLog(ObjectProvider<RabbitTemplate> rabbitTemplate,
            @Value("${spring.application.name:unknown}") String source) {
        this.rabbitTemplate = rabbitTemplate;
        this.source = source;
    }

    /// Một dòng nhật ký. `lockerId` để lọc theo tủ (null nếu không gắn tủ nào).
    public record Entry(
            String action,
            String targetType,
            Long targetId,
            String targetLabel,
            Long lockerId,
            String fromValue,
            String toValue,
            String note) {

        public static Entry of(String action, String targetType, Long targetId, String targetLabel) {
            return new Entry(action, targetType, targetId, targetLabel, null, null, null, null);
        }

        public Entry locker(Long id) {
            return new Entry(action, targetType, targetId, targetLabel, id, fromValue, toValue, note);
        }

        public Entry change(Object from, Object to) {
            return new Entry(action, targetType, targetId, targetLabel, lockerId,
                    from == null ? null : String.valueOf(from), to == null ? null : String.valueOf(to), note);
        }

        public Entry note(String text) {
            return new Entry(action, targetType, targetId, targetLabel, lockerId, fromValue, toValue, text);
        }
    }

    public void record(Entry entry) {
        record(entry, RequestActor.current());
    }

    public void record(Entry entry, RequestActor actor) {
        if (entry == null || entry.action() == null) {
            return;
        }
        DomainEvent event = new DomainEvent(
                UUID.randomUUID().toString(), DomainEventNames.ADMIN_ACTION_LOGGED, source, Instant.now(),
                payload(entry, actor));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    static Map<String, Object> payload(Entry entry, RequestActor actor) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", entry.action());
        put(payload, "targetType", entry.targetType());
        put(payload, "targetId", entry.targetId());
        put(payload, "targetLabel", cut(entry.targetLabel(), 255));
        put(payload, "lockerId", entry.lockerId());
        put(payload, "fromValue", cut(entry.fromValue(), MAX_VALUE));
        put(payload, "toValue", cut(entry.toValue(), MAX_VALUE));
        put(payload, "note", cut(entry.note(), MAX_NOTE));
        if (actor != null) {
            put(payload, "actorUserId", actor.userId());
            put(payload, "actorRoles", cut(actor.roles(), 255));
        }
        return payload;
    }

    private void send(DomainEvent event) {
        RabbitTemplate template = rabbitTemplate.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.convertAndSend(DomainEventNames.EXCHANGE, DomainEventNames.ADMIN_ACTION_LOGGED, event);
        } catch (RuntimeException ex) {
            log.warn("Could not publish action log {}: {}", event.payload().get("action"), ex.getMessage());
        }
    }

    private static void put(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }
}
