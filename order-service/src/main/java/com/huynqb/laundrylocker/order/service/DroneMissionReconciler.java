package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Đối soát nhiệm vụ bay mồ côi: nhiệm vụ chưa kết thúc trong khi đơn đã rời `AWAITING_DISPATCH` (đã vào ô, đã đóng,
 * đã huỷ, bị đổi tay, dữ liệu seed cũ…). Chặng bay chỉ đi tiếp khi đơn còn `AWAITING_DISPATCH`
 * ({@link DroneMissionProgressService#assertForwardTransition}), nên nhiệm vụ như vậy không bao giờ tự kết thúc và
 * drone của nó kẹt ở `RESERVED`/`IN_FLIGHT`, không nhận được đơn mới.
 *
 * <p>Nhiệm vụ được đóng ở `CANCELED` kèm ghi chú. Drone được trả theo chặng:
 * <ul>
 *   <li>chưa phóng: `RESERVED → IDLE`;</li>
 *   <li>đã phóng và kiện đã vào tủ nhận (đơn `STORING`/`COMPLETED`/`EXPIRED`): `IN_FLIGHT → IDLE`;</li>
 *   <li>đã phóng mà đơn huỷ hoặc không còn: `IN_FLIGHT → FAULT` để kiểm tra trước khi bay lại, như
 *       {@link DroneDeliveryFailureService}.</li>
 * </ul>
 * Drone đang thuộc một nhiệm vụ khác còn chạy thì giữ nguyên.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneMissionReconciler {

    static final List<String> OPEN_MISSION = List.of(
            "AWAITING_LOADING", "READY_TO_LAUNCH", "LAUNCHING", "DEPARTED", "EN_ROUTE", "APPROACHING", "ARRIVED");
    private static final Set<String> PRE_LAUNCH = Set.of("AWAITING_LOADING", "READY_TO_LAUNCH");
    private static final Set<String> DELIVERED_ORDER = Set.of("STORING", "COMPLETED", "EXPIRED");

    private final DroneMissionRepository missionRepository;
    private final LockerOrderRepository orderRepository;
    private final LockerDroneClient lockerDroneClient;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(cron = "${app.order.drone-mission-reconcile-cron:0 40 * * * *}")
    public void reconcileScheduled() {
        try {
            int closed = reconcile(LocalDateTime.now());
            if (closed > 0) {
                log.info("Closed orphan drone missions: {}", closed);
            }
        } catch (RuntimeException ex) {
            log.warn("Drone mission reconcile failed: {}", ex.getMessage());
        }
    }

    int reconcile(LocalDateTime now) {
        Set<Long> dronesInUse = new HashSet<>();
        List<DroneMission> candidates = new ArrayList<>();
        for (DroneMission mission : missionRepository.findByStatusIn(OPEN_MISSION)) {
            LockerOrder order = orderRepository.findById(mission.getOrderId()).orElse(null);
            if (order != null && "AWAITING_DISPATCH".equals(order.getStatus())) {
                if (mission.getDroneUnitId() != null) {
                    dronesInUse.add(mission.getDroneUnitId());
                }
            } else {
                candidates.add(mission);
            }
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int closed = 0;
        for (DroneMission candidate : candidates) {
            try {
                Closed result = tx.execute(status -> closeIfOrphan(candidate.getId(), candidate.getOrderId(), now));
                if (result != null) {
                    closed++;
                    if (result.droneId() != null && !dronesInUse.contains(result.droneId())) {
                        releaseDroneQuietly(result);
                    }
                }
            } catch (RuntimeException ex) {
                // Một nhiệm vụ lỗi không được chặn các nhiệm vụ còn lại; lượt sau thử lại.
                log.warn("Could not close orphan drone mission {}: {}", candidate.getId(), ex.getMessage());
            }
        }
        return closed;
    }

    private record Closed(Long missionId, Long droneId, String stage, Long orderId, LockerOrder order) {
    }

    /// Khoá đơn trước rồi đọc lại nhiệm vụ (cùng thứ tự khoá với đường đẩy chặng): kiện vừa vào ô ngay lúc
    /// quét thì nhiệm vụ đã DEPOSITED và được bỏ qua, không bị ghi đè.
    private Closed closeIfOrphan(Long missionId, Long orderId, LocalDateTime now) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order != null && "AWAITING_DISPATCH".equals(order.getStatus())) {
            return null;
        }
        DroneMission mission = missionRepository.findById(missionId).orElse(null);
        if (mission == null || !OPEN_MISSION.contains(mission.getStatus())) {
            return null;
        }
        String stage = mission.getStatus();
        mission.setStatus("CANCELED");
        mission.setEndedAt(now);
        mission.setEndNote("Đối soát: đơn " + (order == null ? "không còn" : order.getStatus())
                + " nhưng nhiệm vụ còn " + stage);
        missionRepository.save(mission);
        return new Closed(missionId, mission.getDroneUnitId(), stage, orderId, order);
    }

    private void releaseDroneQuietly(Closed closed) {
        DroneStatusTransitionRequest release;
        if (PRE_LAUNCH.contains(closed.stage())) {
            release = new DroneStatusTransitionRequest("RESERVED", "IDLE", null);
        } else if (closed.order() != null && DELIVERED_ORDER.contains(closed.order().getStatus())) {
            release = new DroneStatusTransitionRequest("IN_FLIGHT", "IDLE", null);
        } else {
            String code = closed.order() == null ? "#" + closed.orderId() : closed.order().getOrderCode();
            release = new DroneStatusTransitionRequest(
                    "IN_FLIGHT", "FAULT", "Đơn " + code + " đã đóng khi drone còn " + closed.stage() + " — cần kiểm tra");
        }
        try {
            lockerDroneClient.transitionDroneStatus(closed.droneId(), release);
        } catch (RuntimeException ex) {
            // Drone đã được đổi trạng thái ở nơi khác (CAS lệch) hoặc locker-service lỗi: nhiệm vụ vẫn đóng.
            log.info("Drone {} not released for orphan mission {}: {}",
                    closed.droneId(), closed.missionId(), ex.getMessage());
        }
    }
}
