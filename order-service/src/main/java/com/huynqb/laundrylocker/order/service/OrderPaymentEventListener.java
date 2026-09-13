package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.order.config.RabbitConfig;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Marks an order PAID when payment-service confirms payment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private final LockerOrderRepository orderRepository;

    @RabbitListener(queues = RabbitConfig.ORDER_PAYMENT_QUEUE)
    @Transactional
    public void onPaymentEvent(DomainEvent event) {
        Object orderIdObj = event.payload() == null ? null : event.payload().get("orderId");
        if (orderIdObj == null) {
            return;
        }
        long orderId;
        try {
            orderId = Long.parseLong(orderIdObj.toString());
        } catch (NumberFormatException ex) {
            return;
        }
        if (orderId <= 0L) {
            return; // 0 = wallet top-up sentinel, not a real order
        }
        if (!DomainEventNames.PAYMENT_COMPLETED.equals(event.type())) {
            return; // only completed payments flip the order to PAID
        }
        orderRepository
                .findById(orderId)
                .ifPresent(
                        order -> {
                            if (!"PAID".equals(order.getPaymentStatus())) {
                                order.setPaymentStatus("PAID");
                                order.setPaidAt(LocalDateTime.now());
                                orderRepository.save(order);
                                log.info("Order {} marked PAID via payment event", orderId);
                            }
                        });
    }
}
