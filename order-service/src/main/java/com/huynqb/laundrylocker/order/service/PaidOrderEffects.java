package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Các bước chạy ngay sau khi một đơn vừa thành `PAID`: báo realtime + thông báo "thanh toán thành công",
 * và với đơn drone thì mở lượt tìm KTV tới lấy hàng. Dùng chung cho sự kiện `payment.completed` từ
 * payment-service ({@link OrderPaymentEventListener}) và đơn 0đ sau mã giảm giá / điểm thưởng
 * ({@link #settleIfFree}), để hai đường không lệch nhau.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaidOrderEffects {

    private final RabbitTemplate rabbitTemplate;
    private final NotificationClient notificationClient;
    private final DronePickupService dronePickupService;
    private final LockerOrderRepository orderRepository;

    /// Đơn còn 0đ sau mã giảm giá / điểm thưởng coi như đã thanh toán ngay: PAID, paidAt, paidAmount 0 và
    /// chạy đúng các bước như khi nhận `payment.completed` (đơn drone mở lượt tìm KTV lấy hàng sau commit).
    /// Job tự huỷ đơn chưa thanh toán vì thế không đụng tới đơn này. Trả true nếu vừa ghi PAID.
    public boolean settleIfFree(LockerOrder order) {
        BigDecimal total = order.getTotalPrice();
        if (total == null || total.signum() > 0 || "PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return false;
        }
        order.setPaymentStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        order.setPaidAmount(BigDecimal.ZERO);
        LockerOrder saved = orderRepository.save(order);
        log.info("Order {} is free after discounts, marked PAID without payment", saved.getId());
        afterPaid(saved);
        return true;
    }

    /// Đơn `order` vừa được ghi `PAID` (đã lưu) trong giao dịch hiện tại.
    public void afterPaid(LockerOrder order) {
        publishPaymentCompleted(order);
        startDronePickupAfterCommit(order);
    }

    void publishPaymentCompleted(LockerOrder order) {
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
            notificationClient.requestNotification(new NotificationRequest(
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

    /**
     * Đơn drone vừa trả đủ tiền: mở lượt lấy hàng và mời KTV drone rảnh gần người gửi nhất. Chạy SAU
     * khi việc ghi nhận thanh toán commit — lỗi lúc tìm KTV không được làm mất việc đơn đã PAID;
     * {@link DroneDispatchSweeper} mở lại lượt lấy hàng nếu bước này lỡ.
     */
    void startDronePickupAfterCommit(LockerOrder order) {
        if (!"DRONE_DELIVERY".equals(order.getType()) || !"AWAITING_DISPATCH".equals(order.getDeliveryStage())) {
            return;
        }
        Long orderId = order.getId();
        Runnable start = () -> {
            try {
                dronePickupService.startForPaidOrder(orderId);
            } catch (RuntimeException ex) {
                log.warn("Could not open pickup for paid drone order {}: {}", orderId, ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    start.run();
                }
            });
        } else {
            start.run();
        }
    }
}
