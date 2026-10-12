package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hai việc nền giữ cho đơn drone không đứng yên:
 *
 * <ul>
 *   <li>điều phối lấy hàng — lời mời KTV quá hạn thì mời người kế tiếp, lượt đang chờ thì thử mời lại,
 *       đơn đã thanh toán mà chưa có lượt lấy hàng (sự kiện thanh toán bị lỡ) thì mở lượt;</li>
 *   <li>soát sự cố — đơn nào đang bị phiếu sự cố/tạm dừng bay chặn, đơn nào đã hết chặn, và đơn bị
 *       chặn quá hạn thì đóng "giao không hoàn tất" rồi tạo yêu cầu hoàn tiền.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneDispatchSweeper {

    private final LockerOrderRepository orderRepository;
    private final DronePickupRepository pickupRepository;
    private final DronePickupService pickupService;
    private final DroneDeliveryBlockService blockService;
    private final DroneDeliveryFailureService failureService;
    private final DroneRefundService refundService;
    private final com.huynqb.laundrylocker.order.repository.DroneMissionRepository missionRepository;
    private final com.huynqb.laundrylocker.order.client.LockerDroneClient lockerDroneClient;
    private final DroneSourceLockerSelector sourceLockerSelector;
    private final DroneOrderMaintenanceService maintenanceService;

    @Scheduled(cron = "${app.order.drone-pickup-sweep-cron:*/30 * * * * *}")
    public void dispatchScheduled() {
        try {
            dispatch(LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.warn("Drone pickup dispatch sweep failed: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "${app.order.drone-block-sweep-cron:15 * * * * *}")
    public void blockScheduled() {
        try {
            reviewBlocksAt(LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.warn("Drone delivery block sweep failed: {}", ex.getMessage());
        }
    }

    void dispatch(LocalDateTime now) {
        for (LockerOrder order :
                orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH")) {
            if ("AWAITING_DISPATCH".equals(order.getDeliveryStage())
                    && "PAID".equalsIgnoreCase(order.getPaymentStatus())
                    && order.getPickupLatitude() != null
                    && pickupRepository.findByOrderId(order.getId()).isEmpty()) {
                try {
                    pickupService.startForPaidOrder(order.getId());
                } catch (RuntimeException ex) {
                    log.warn("Could not open pickup for drone order {}: {}", order.getId(), ex.getMessage());
                }
            }
        }
        pickupService.sweep(now);
    }

    /// @return số đơn vừa bị đóng "giao không hoàn tất" vì bị chặn quá hạn
    public int reviewBlocksAt(LocalDateTime now) {
        DroneDeliveryBlockService.Snapshot snapshot = new DroneDeliveryBlockService.Snapshot();
        int failed = 0;
        for (LockerOrder order :
                orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH")) {
            try {
                DroneDeliveryBlockService.Review review = blockService.review(order.getId(), now, snapshot);
                if (tryAlternativeDrone(order.getId())) {
                    blockService.review(order.getId(), now, new DroneDeliveryBlockService.Snapshot());
                    continue;
                }
                if (review.verdict() == DroneDeliveryBlockService.Verdict.EXPIRED) {
                    failureService.fail(order.getId(), null, DroneDeliveryFailureService.Actor.SYSTEM,
                            review.reason(), review.note(), review.ticketId());
                    refundService.refundCanceledOrder(order.getId(), null);
                    failed++;
                }
            } catch (RuntimeException ex) {
                // Một đơn lỗi không được chặn các đơn còn lại; lượt sau thử lại.
                log.warn("Could not review drone order {} for blocking incidents: {}", order.getId(), ex.getMessage());
            }
        }
        return failed;
    }

    // Each change runs in its own transaction so a reservation race on one candidate
    // does not roll back a later candidate. Never swap a drone after launch.
    private boolean tryAlternativeDrone(Long orderId) {
        var current = orderRepository.findById(orderId).orElse(null);
        if (current == null || !com.huynqb.laundrylocker.order.model.DroneFailureReasons.DRONE_FAULT
                .equals(current.getDeliveryBlockReason())) return false;
        var mission = missionRepository.findByOrderId(orderId).orElse(null);
        if (mission == null || mission.getLaunchingAt() != null
                || !("AWAITING_LOADING".equals(mission.getStatus()) || "READY_TO_LAUNCH".equals(mission.getStatus()))) return false;
        var response = lockerDroneClient.listDroneUnits(current.getSourceLockerId());
        if (response == null || response.data() == null) return false;
        for (var drone : response.data()) {
            if (!sourceLockerSelector.ready(drone)) continue;
            try {
                maintenanceService.changeDrone(orderId, mission.getAssignedByUserId(),
                        new com.huynqb.laundrylocker.order.dto.AcceptDroneOrderRequest(drone.id()));
                return true;
            } catch (com.huynqb.laundrylocker.common.exception.BusinessException unavailable) {
                log.debug("Alternative drone {} unavailable for order {}: {}", drone.id(), orderId, unavailable.getCode());
            }
        }
        return false;
    }
}
