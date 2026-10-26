package com.huynqb.laundrylocker.common.event;

import com.huynqb.laundrylocker.common.util.AfterCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

/// Phát {@link DomainEventNames#CATALOG_CHANGED} sau commit khi admin sửa dữ liệu công khai, để app
/// khách tải lại danh sách ưu đãi/cửa hàng/tủ ngay. Payload chỉ có loại + mã — không mang dữ liệu riêng.
public final class CatalogChangeEvents {

    public static final String PROMOTION = "PROMOTION";
    public static final String STORE = "STORE";
    public static final String LOCKER = "LOCKER";

    private static final Logger log = LoggerFactory.getLogger(CatalogChangeEvents.class);

    private CatalogChangeEvents() {
    }

    public static void publish(RabbitTemplate rabbitTemplate, String source, String entity, Long id) {
        if (rabbitTemplate == null || id == null) {
            return;
        }
        AfterCommit.run(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        DomainEventNames.EXCHANGE,
                        DomainEventNames.CATALOG_CHANGED,
                        DomainEvent.of(DomainEventNames.CATALOG_CHANGED, source, Map.of("entity", entity, "id", id)));
            } catch (AmqpException ex) {
                log.warn("Could not publish catalog.changed for {} {}: {}", entity, id, ex.getMessage());
            }
        });
    }
}
