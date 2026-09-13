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
    @Scheduled(cron = "${app.order.auto-cancel-cron:0 */15 * * * *}")
    public void sweepUnconfirmedReservations() {
        try {
            orderService.autoCancelUnconfirmedOrders();
        } catch (Exception ex) {
            log.warn("Auto-cancel sweep job failed: {}", ex.getMessage());
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
