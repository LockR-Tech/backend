package com.huynqb.laundrylocker.common.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

/// Phát {@link DomainEventNames#MAINTENANCE_CHANGED} khi việc của KTV đổi (phiếu sự cố, lịch bảo trì, tủ,
/// drone, đơn drone, sự cố kiện drone) để app KTV đang mở tải lại ngay — kể cả khi người đổi là KTV khác,
/// admin hay khách. Gom theo giao dịch, gửi sau commit: {@link EntityChangeEvents}.
public final class MaintenanceChangeEvents {

    public static final String REPORT = "REPORT";
    public static final String SCHEDULE = "SCHEDULE";
    public static final String LOCKER = "LOCKER";
    public static final String DRONE = "DRONE";
    public static final String DRONE_ORDER = "DRONE_ORDER";
    public static final String DRONE_INCIDENT = "DRONE_INCIDENT";

    private MaintenanceChangeEvents() {
    }

    public static void changed(RabbitTemplate rabbitTemplate, String source, String entity, Long id, Long lockerId) {
        EntityChangeEvents.changed(
                rabbitTemplate, DomainEventNames.MAINTENANCE_CHANGED, source, entity, id, lockerId);
    }
}
