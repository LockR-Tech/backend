package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Đóng đơn drone chờ quá hạn — mỗi kiểu chờ giữ một tài nguyên mà không ai nhả:
 *
 * <ul>
 *   <li>chưa thanh toán: giữ ô DRONE ở tủ nhận (job tự huỷ chung chỉ quét đơn `INITIALIZED`, đơn
 *       drone chờ ở `AWAITING_DISPATCH`);</li>
 *   <li>đã thanh toán mà không KTV drone nào nhận lấy hàng: giữ tiền của khách — đơn kết thúc
 *       "giao không hoàn tất";</li>
 *   <li>nợ phụ thu cân lệch không trả: giữ drone ở `RESERVED`.</li>
 * </ul>
 *
 * Hai kiểu sau đã thu tiền nên được tạo yêu cầu hoàn tiền sau khi việc đóng đơn commit. Tiền về sau khi
 * đơn đã đóng ở đây vẫn được hoàn nhờ {@link DroneLateRefundScheduler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneOrderTimeoutSweeper {

    private final LockerOrderRepository orderRepository;
    private final DroneOrderExpiryService expiryService;
    private final DroneOrderMaintenanceService maintenanceService;
    private final DroneDeliveryFailureService failureService;
    private final DroneRefundService refundService;
    private final OrderRules rules;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.huynqb.laundrylocker.order.repository.DroneParcelIncidentRepository incidentRepository;

    @Scheduled(cron = "${app.order.drone-timeout-sweep-cron:0 */5 * * * *}")
    public void sweepScheduled() {
        try {
            int canceled = sweep(LocalDateTime.now());
            if (canceled > 0) {
                log.info("Auto-closed timed-out drone orders: {}", canceled);
            }
        } catch (RuntimeException ex) {
            log.warn("Drone order timeout sweep failed: {}", ex.getMessage());
        }
    }

    int sweep(LocalDateTime now) {
        int unpaidMinutes = rules.droneUnpaidCancelMinutes();
        int dispatchMinutes = rules.droneDispatchTimeoutMinutes();
        int surchargeMinutes = rules.droneSurchargeTimeoutMinutes();
        if (unpaidMinutes <= 0 && dispatchMinutes <= 0 && surchargeMinutes <= 0) {
            return 0;
        }
        int canceled = 0;
        // Đơn drone giữ status AWAITING_DISPATCH suốt từ lúc tạo tới khi hàng vào ô nhận.
        for (LockerOrder order :
                orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH")) {
            try {
                if (expire(order, now, unpaidMinutes, dispatchMinutes, surchargeMinutes)) {
                    canceled++;
                }
            } catch (RuntimeException ex) {
                // Một đơn lỗi (locker-service chập chờn…) không được chặn các đơn còn lại; lượt sau thử lại.
                log.warn("Could not expire drone order {}: {}", order.getId(), ex.getMessage());
            }
        }
        return canceled;
    }

    private boolean expire(
            LockerOrder order, LocalDateTime now, int unpaidMinutes, int dispatchMinutes, int surchargeMinutes) {
        Long orderId = order.getId();
        if (unpaidMinutes > 0 && DroneOrderExpiryService.unpaidPast(order, now.minusMinutes(unpaidMinutes))) {
            return expiryService.cancelUnpaid(orderId, now.minusMinutes(unpaidMinutes), unpaidMinutes);
        }
        boolean canceled = false;
        // Đơn giao lại sau sự cố rơi kiện (0đ, kiện ở kho) không tự đóng "không có KTV": đóng thì kiện không còn đơn
        // nào giao và sự cố kẹt "đang giao lại". Đơn này chờ KTV drone tiếp nhận.
        if (dispatchMinutes > 0
                && DroneOrderExpiryService.unassignedPast(order, now.minusMinutes(dispatchMinutes))
                && !incidentRedelivery(orderId)) {
            // Khoá và kiểm lại nằm trong fail(): KTV vừa nhận thì chặng đã đổi và lệnh này bị từ chối.
            if (expiryService.stillUnassigned(orderId, now.minusMinutes(dispatchMinutes))) {
                failureService.fail(orderId, null, DroneDeliveryFailureService.Actor.SYSTEM,
                        DroneFailureReasons.NO_TECHNICIAN_AVAILABLE,
                        "Không có kỹ thuật viên drone nhận lấy hàng trong " + dispatchMinutes + " phút", null);
                canceled = true;
            }
        } else if (surchargeMinutes > 0
                && "ACCEPTED".equals(order.getDeliveryStage())
                && "UNPAID".equalsIgnoreCase(order.getPaymentStatus())) {
            canceled = maintenanceService.cancelUnpaidSurcharge(orderId, null, now.minusMinutes(surchargeMinutes));
        }
        if (canceled) {
            refundService.refundCanceledOrder(orderId, null);
        }
        return canceled;
    }

    private boolean incidentRedelivery(Long orderId) {
        if (incidentRepository == null) {
            return false;
        }
        try {
            return incidentRepository.findFirstByRedeliveryOrderId(orderId).isPresent();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
