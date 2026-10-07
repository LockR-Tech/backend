package com.huynqb.laundrylocker.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hoàn khoản tiền tới SAU khi đơn drone đã huỷ (khách huỷ đơn lúc mã chuyển khoản còn chờ,
 * rồi tiền mới về). Lúc huỷ chưa có gì để hoàn, nên không ai hoàn khoản này nếu không làm ở đây.
 *
 * <p>Chạy trễ vài giây: payment-service phát sự kiện ngay trong giao dịch ghi nhận thanh
 * toán, hoàn ngay lập tức có thể chưa thấy khoản vừa thu.
 */
@Slf4j
@Component
public class DroneLateRefundScheduler {

    private final DroneRefundService refundService;
    private final long delayMs;

    public DroneLateRefundScheduler(
            DroneRefundService refundService,
            @Value("${app.drone.late-refund-delay-ms:5000}") long delayMs) {
        this.refundService = refundService;
        this.delayMs = delayMs;
    }

    public void schedule(Long orderId) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        if (!refundService.refundLatePayment(orderId)) {
                            log.warn("Late payment of canceled drone order {} was not refunded automatically", orderId);
                        }
                    } catch (RuntimeException ex) {
                        log.warn("Late refund of canceled drone order {} failed: {}", orderId, ex.getMessage());
                    }
                },
                CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS));
    }
}
