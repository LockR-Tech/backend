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

import java.util.Map;

/**
 * Marks an order PAID when payment-service confirms payment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private final LockerOrderRepository orderRepository;
    private final DroneLateRefundScheduler droneLateRefunds;
    /// Báo "đã thanh toán" + mở lượt lấy hàng drone — dùng chung với đơn 0đ (OrderBenefitService).
    private final PaidOrderEffects paidEffects;

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
                            boolean canceledDrone = "DRONE_DELIVERY".equals(order.getType())
                                    && "CANCELED".equals(order.getStatus());
                            if (canceledDrone) {
                                droneLateRefunds.schedule(orderId);
                            }
                            if ("DRONE_DELIVERY".equals(order.getType())
                                    && !canceledDrone
                                    && applyDronePayment(order, event.payload())) {
                                return;
                            }
                            // Sự kiện thanh toán tới trễ không được lật đơn đang chờ hoàn / đã hoàn về PAID.
                            if (!"PAID".equals(order.getPaymentStatus())
                                    && !"REFUND_PENDING".equals(order.getPaymentStatus())
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
                                // Đơn drone đã huỷ: khoản này sắp được hoàn, không báo "thanh toán thành công".
                                if (!canceledDrone) {
                                    paidEffects.afterPaid(saved);
                                }
                            }
                        });
    }

    /**
     * Đơn drone đang chạy: cộng ĐÚNG số tiền của lần thanh toán này vào `paidAmount` và chỉ
     * lật `PAID` khi đã đủ tổng đơn. Trước đây mọi sự kiện đều gán `paidAmount = totalPrice`,
     * nên sự kiện lặp lại của lần trả đầu (cổng thanh toán gửi lại callback) tới sau khi đã cộng phụ thu cân
     * lệch lật đơn về `PAID` dù chưa thu phần chênh.
     *
     * @return false khi sự kiện không mang số tiền/mã thanh toán (nguồn phát cũ) — nơi gọi
     *     dùng cách ghi nhận cũ.
     */
    private boolean applyDronePayment(LockerOrder order, Map<String, Object> payload) {
        java.math.BigDecimal amount = decimal(payload.get("amount"));
        Long paymentId = payload.get("paymentId") == null ? null : longValue(payload.get("paymentId"));
        if (amount == null || paymentId == null) {
            return false;
        }
        if (paymentId.equals(order.getLastPaymentId()) || !"UNPAID".equals(order.getPaymentStatus())) {
            return true; // lần thanh toán này đã được cộng, hoặc đơn không còn chờ tiền
        }
        java.math.BigDecimal total =
                order.getTotalPrice() == null ? java.math.BigDecimal.ZERO : order.getTotalPrice();
        java.math.BigDecimal paid =
                (order.getPaidAmount() == null ? java.math.BigDecimal.ZERO : order.getPaidAmount()).add(amount);
        order.setLastPaymentId(paymentId);
        order.setPaidAmount(paid);
        if (paid.compareTo(total) < 0) {
            orderRepository.save(order);
            log.info("Drone order {} received {} but still owes {}", order.getId(), amount, total.subtract(paid));
            return true;
        }
        order.setPaymentStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        LockerOrder saved = orderRepository.save(order);
        log.info("Drone order {} marked PAID via payment {}", order.getId(), paymentId);
        paidEffects.afterPaid(saved);
        return true;
    }

    private static java.math.BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long longValue(Object value) {
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
