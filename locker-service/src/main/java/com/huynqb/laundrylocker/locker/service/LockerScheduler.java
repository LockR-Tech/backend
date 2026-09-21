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
