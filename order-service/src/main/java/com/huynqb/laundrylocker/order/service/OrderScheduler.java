package com.huynqb.laundrylocker.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderService orderService;
    private final OrderBenefitService benefits;
    private final DroneRefundService refundService;

    // Đơn huỷ/hoàn tiền có dùng điểm mà lúc huỷ loyalty-service lỗi: thử hoàn điểm lại (idempotent).
    @Scheduled(cron = "${app.order.points-refund-sweep-cron:0 */10 * * * *}")
    public void retryPointRefunds() {
        try {
            int refunded = benefits.retryPendingPointRefunds(50);
            if (refunded > 0) {
                log.info("Refunded loyalty points of {} canceled/refunded orders on retry", refunded);
            }
        } catch (Exception ex) {
            log.warn("Loyalty point refund retry job failed: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.order.reminder-interval-ms:600000}", initialDelay = 60000)
    public void remindOverduePickups() {
        try {
            orderService.sendPickupReminders();
        } catch (Exception ex) {
            log.warn("Pickup reminder job failed: {}", ex.getMessage());
        }
    }

    // Safety net: orders completed through the blunt PATCH /status endpoint by
    // legacy clients may leave cells held; sweep them nightly.
    @Scheduled(cron = "${app.order.release-sweep-cron:0 15 3 * * *}")
    public void releaseCompletedBoxes() {
        try {
            orderService.releaseBoxesAfterCompletion();
        } catch (Exception ex) {
            log.warn("Release sweep job failed: {}", ex.getMessage());
        }
    }

    // Reservation TTL: cancel unconfirmed orders past the hold window and release
    // the cells they were holding, so abandoned reservations don't stick RESERVED.
    @Scheduled(cron = "${app.order.auto-cancel-cron:0 */2 * * * *}")
    public void sweepUnconfirmedReservations() {
        try {
            refundPaidCanceled(orderService.autoCancelUnconfirmedOrders(), refundService);
        } catch (Exception ex) {
            log.warn("Auto-cancel sweep job failed: {}", ex.getMessage());
        }
    }

    /// Đơn đã trả tiền mà bị tự huỷ (không bỏ hàng kịp hạn) ⇒ tạo yêu cầu hoàn tiền. Gọi sau khi
    /// lượt huỷ đã commit (yêu cầu lỗi thì đơn giữ PAID để admin xử lý tay).
    public static void refundPaidCanceled(java.util.Map<String, Object> sweepResult, DroneRefundService refundService) {
        Object ids = sweepResult == null ? null : sweepResult.get("paidCanceledOrderIds");
        if (!(ids instanceof java.util.Collection<?> orderIds)) {
            return;
        }
        for (Object id : orderIds) {
            try {
                refundService.refundCanceledOrder(((Number) id).longValue(), null);
            } catch (Exception ex) {
                log.warn("Refund of auto-canceled order {} failed: {}", id, ex.getMessage());
            }
        }
    }

    // G3: đơn quá hạn lấy hàng quá lâu → chuyển kho (EXPIRED) + nhả ô, để ô
    // không bị chiếm vô thời hạn chỉ vì khách không quay lại.
    @Scheduled(cron = "${app.order.overdue-release-cron:0 5 * * * *}")
    public void releaseOverdueOrders() {
        try {
            orderService.releaseOverdueOrders();
        } catch (Exception ex) {
            log.warn("Overdue release job failed: {}", ex.getMessage());
        }
    }

    // G4: đối soát trạng thái ô locker-service với đơn đang hoạt động —
    // occupy/release là best-effort nên hai bên có thể lệch dần theo thời gian.
    @Scheduled(cron = "${app.order.reconcile-cron:0 35 * * * *}")
    public void reconcileBoxStates() {
        try {
            orderService.reconcileBoxStates();
        } catch (Exception ex) {
            log.warn("Box reconcile job failed: {}", ex.getMessage());
        }
    }
}
