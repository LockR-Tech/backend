package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/// Bộ giả lập cho đơn DEMO: mỗi nhịp phát vị trí nội suy cho bản đồ trực tiếp và, khi
/// đủ thời gian một chặng, đẩy mission sang chặng kế tiếp. Đơn STANDARD không bị đụng tới.
@Service
@RequiredArgsConstructor
public class DroneDeliverySimulator {

    private final DroneMissionRepository missionRepository;
    private final LockerOrderRepository orderRepository;
    private final DroneMissionProgressService progressService;
    private final DronePositionBroadcaster positionBroadcaster;
    private final OrderRules rules;

    @Scheduled(fixedDelayString = "${app.drone.demo.scheduler-delay-ms:1000}")
    @Transactional
    public void advanceScheduledMissions() {
        advanceEligibleMissions(LocalDateTime.now());
    }

    void advanceEligibleMissions(LocalDateTime now) {
        long stageDelayMs = rules.droneDemoStageDelayMs();
        for (DroneMission mission : missionRepository.findByStatusIn(DroneMissionProgressService.IN_FLIGHT_STAGES)) {
            LockerOrder order = orderRepository.findById(mission.getOrderId()).orElse(null);
            if (order == null || !"DEMO".equalsIgnoreCase(order.getFulfillmentMode())) {
                continue;
            }
            LocalDateTime updatedAt = mission.getUpdatedAt();
            if (updatedAt == null || Duration.between(updatedAt, now).toMillis() < stageDelayMs) {
                positionBroadcaster.broadcast(order, mission, now, stageDelayMs);
                continue;
            }
            String previousStage = order.getDeliveryStage();
            progressService.advance(
                    order,
                    mission,
                    null,
                    "Drone demo chuyển chặng " + previousStage + " → "
                            + DroneMissionProgressService.nextStage(mission.getStatus()));
            positionBroadcaster.broadcast(order, mission, now, stageDelayMs);
        }
    }
}
