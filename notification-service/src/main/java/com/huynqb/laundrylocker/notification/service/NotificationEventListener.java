package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.notification.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final DeliveryNotificationService deliveryNotificationService;

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void onEvent(DomainEvent event) {
        // Event giao hàng (drone) có data payload riêng (orderId/status/eta) -> xử lý
        // bằng DeliveryNotificationService; các event khác theo luồng chung.
        if (DomainEventNames.DELIVERY_STATUS_CHANGED.equals(event.type())) {
            deliveryNotificationService.notifyFromEvent(event);
        } else {
            notificationService.consumeDomainEvent(event);
        }
    }
}
