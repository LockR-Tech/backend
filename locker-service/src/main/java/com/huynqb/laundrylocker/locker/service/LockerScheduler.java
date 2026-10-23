package com.huynqb.laundrylocker.locker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LockerScheduler {

    private final LockerService lockerService;

    // Backstop only: order-service's own sweep (every 15 min) already releases
    // a box when it auto-cancels the unconfirmed order holding it. This just
    // catches boxes left RESERVED if that sweep is ever down.
    @Scheduled(cron = "${app.locker.reserved-sweep-cron:0 0 * * * *}")
    public void sweepExpiredReservations() {
        try {
            int released = lockerService.sweepExpiredReservations();
            if (released > 0) {
                log.warn("Reserved-TTL backstop sweep released {} box(es)", released);
            }
        } catch (Exception ex) {
            log.warn("Reserved-TTL backstop sweep failed: {}", ex.getMessage());
        }
    }

    // Đơn có hàng nằm trong ô đang có phiếu sự cố mở phải được order-service dừng phí. Lần báo lúc tạo
    // phiếu có thể hỏng (order-service tạm ngừng) hoặc phiếu có từ trước bản này — quét lại định kỳ.
    @Scheduled(fixedDelayString = "${app.locker.box-incident-sync-ms:600000}", initialDelay = 90000)
    public void syncOpenBoxIncidents() {
        try {
            int synced = lockerService.syncOpenBoxIncidentsToOrders();
            if (synced > 0) {
                log.info("Synced {} open box incident(s) to their orders", synced);
            }
        } catch (Exception ex) {
            log.warn("Box incident sync failed: {}", ex.getMessage());
        }
    }

    // Ô chờ KTV kiểm tra quá hạn giữ (cấu hình admin) thì trả về hoạt động, phiếu vẫn mở.
    @Scheduled(fixedDelayString = "${app.locker.pending-check-release-ms:900000}", initialDelay = 120000)
    public void releaseStalePendingChecks() {
        try {
            int released = lockerService.releaseStalePendingChecks();
            if (released > 0) {
                log.info("Released {} box(es) left waiting for a technician check", released);
            }
        } catch (Exception ex) {
            log.warn("Pending-check release failed: {}", ex.getMessage());
        }
    }

    // Giờ VN (JVM chạy UTC). Mỗi kỳ hạn chỉ nhắc một lần nên chạy lại cũng không nhắc trùng.
    @Scheduled(cron = "${app.maintenance.schedule-reminder-cron:0 0 7 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void remindDueSchedules() {
        try {
            int reminded = lockerService.remindDueSchedules();
            if (reminded > 0) {
                log.info("Reminded technicians about {} due maintenance schedule(s)", reminded);
            }
        } catch (Exception ex) {
            log.warn("Maintenance schedule reminder failed: {}", ex.getMessage());
        }
    }
}
