package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.loyalty.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/// Cộng điểm từ sự kiện `order.completed` (order-service phát một lần khi đơn vừa chuyển sang COMPLETED,
/// sau commit). Tính theo `paidAmount` của đơn; đơn chưa thanh toán (`paymentStatus` khác PAID) hoặc 0đ
/// không được điểm. eventId không ổn định nên chống trùng theo orderId (LoyaltyService + unique index).
///
/// Cùng hàng đợi nhận `payment.refunded` (payment-service, khi admin xác nhận đã hoàn tiền): thu hồi điểm
/// đã tích theo tỉ lệ tiền đã hoàn ({@link LoyaltyService#reverseOrderEarn}).
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedPointsListener {

    static final String PAID = "PAID";

    private final LoyaltyService loyaltyService;

    @RabbitListener(queues = RabbitConfig.LOYALTY_ORDER_QUEUE)
    public void onOrderCompleted(DomainEvent event) {
        if (event != null && DomainEventNames.PAYMENT_REFUNDED.equals(event.type()) && event.payload() != null) {
            onPaymentRefunded(event.payload());
            return;
        }
        if (event == null || !DomainEventNames.ORDER_COMPLETED.equals(event.type()) || event.payload() == null) {
            return;
        }
        Map<String, Object> payload = event.payload();
        Long orderId = longValue(payload.get("orderId"));
        Long userId = longValue(payload.get("userId"));
        BigDecimal paidAmount = decimal(payload.get("paidAmount"));
        String paymentStatus = payload.get("paymentStatus") == null ? null : payload.get("paymentStatus").toString().trim();
        if (orderId == null || userId == null || paidAmount == null || paidAmount.signum() <= 0
                || !PAID.equalsIgnoreCase(paymentStatus)) {
            log.debug("Order {} completed without loyalty points (paymentStatus={}, paidAmount={})",
                    orderId, paymentStatus, paidAmount);
            return;
        }
        try {
            loyaltyService.awardOrderPoints(orderId, userId, paidAmount).ifPresent(account ->
                    log.info("Awarded loyalty points for order {} to user {} (balance {})",
                            orderId, userId, account.points()));
        } catch (DataIntegrityViolationException duplicate) {
            // Sự kiện được giao lại/xử lý song song: đơn này đã được cộng điểm.
            log.info("Loyalty points for order {} already awarded", orderId);
        }
    }

    private void onPaymentRefunded(Map<String, Object> payload) {
        Long orderId = longValue(payload.get("orderId"));
        int reversed = loyaltyService.reverseOrderEarn(
                orderId, decimal(payload.get("refundedAmount")), decimal(payload.get("paidAmount")));
        if (reversed > 0) {
            log.info("Reversed {} loyalty points of refunded order {}", reversed, orderId);
        }
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        try {
            return value == null ? null : new BigDecimal(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
