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

import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Marks an order PAID when payment-service confirms payment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private final LockerOrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationClient notificationClient;

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
                            // REFUNDED: sự kiện thanh toán tới trễ không được lật đơn đã hoàn tiền về PAID.
                            if (!"PAID".equals(order.getPaymentStatus())
                                    && !"REFUNDED".equals(order.getPaymentStatus())) {
                                order.setPaymentStatus("PAID");
                                order.setPaidAt(LocalDateTime.now());
                                // Ghi nhận khách đã trả tới mức tổng hiện tại; lần gia hạn
                                // hoặc tính phí quá hạn sau đó chỉ thu phần chênh lệch.
                                order.setPaidAmount(
                                        order.getTotalPrice() == null
                                                ? java.math.BigDecimal.ZERO
                                                : order.getTotalPrice());
                                LockerOrder saved = orderRepository.save(order);
                                log.info("Order {} marked PAID via payment event", orderId);
                                publishPaymentCompleted(saved);
                            }
                        });
    }

    private void publishPaymentCompleted(LockerOrder order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderCode", order.getOrderCode());
        payload.put("userId", order.getUserId());
        payload.put("status", order.getStatus());
        payload.put("paymentStatus", "PAID");
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.ORDER_STATUS_CHANGED,
                    DomainEvent.of(DomainEventNames.ORDER_STATUS_CHANGED, "order-service", payload));
        } catch (Exception ex) {
            log.warn("Could not publish order status changed for paid order {}: {}", order.getId(), ex.getMessage());
        }

        try {
            notificationClient.requestNotification(new com.huynqb.laundrylocker.common.dto.NotificationRequest(
                    order.getUserId(),
                    "Thanh toán thành công",
                    "Đơn hàng " + order.getOrderCode() + " đã được thanh toán thành công",
                    "PAYMENT_COMPLETED",
                    order.getId(),
                    "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not notify user {} for paid order {}: {}", order.getUserId(), order.getId(), ex.getMessage());
        }
    }
}
