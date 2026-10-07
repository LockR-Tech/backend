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
    private final DroneLateRefundScheduler droneLateRefunds;

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
        // Khoá đơn: sự kiện này ghi đè cả bản ghi, không được chạy xen với huỷ/tiếp nhận/nạp hàng.
        orderRepository
                .findByIdForUpdate(orderId)
                .ifPresent(
                        order -> {
                            // Đơn drone đã huỷ mà tiền mới về (chuyển khoản còn chờ lúc huỷ):
                            // lúc huỷ chưa có gì để hoàn, nên phải hoàn khoản này bây giờ.
                            if ("DRONE_DELIVERY".equals(order.getType())
                                    && "CANCELED".equals(order.getStatus())) {
                                droneLateRefunds.schedule(orderId);
                            }
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
                                orderRepository.save(order);
                                log.info("Order {} marked PAID via payment event", orderId);
                            }
                        });
    }
}
