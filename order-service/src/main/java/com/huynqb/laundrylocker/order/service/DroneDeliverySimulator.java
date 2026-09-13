package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusUpdateRequest;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class DroneDeliverySimulator {

    private static final List<String> ACTIVE_STAGES =
            List.of("LAUNCHING", "DEPARTED", "EN_ROUTE", "APPROACHING", "ARRIVED");

    private final DroneMissionRepository missionRepository;
    private final LockerOrderRepository orderRepository;
    private final NotificationClient notificationClient;
    private final LockerDroneClient lockerDroneClient;

    @Value("${app.drone.demo.stage-delay-ms:7000}")
    private long stageDelayMs = 7000L;

    @Scheduled(fixedDelayString = "${app.drone.demo.scheduler-delay-ms:1000}")
    @Transactional
    public void advanceScheduledMissions() {
        advanceEligibleMissions(LocalDateTime.now());
    }

    void advanceEligibleMissions(LocalDateTime now) {
        for (DroneMission mission : missionRepository.findByStatusIn(ACTIVE_STAGES)) {
            LockerOrder order = orderRepository.findById(mission.getOrderId()).orElse(null);
            if (order == null || !"DEMO".equalsIgnoreCase(order.getFulfillmentMode())) {
                continue;
            }
            LocalDateTime updatedAt = mission.getUpdatedAt();
            if (updatedAt == null || Duration.between(updatedAt, now).toMillis() < stageDelayMs) {
                continue;
            }
            advance(order, mission);
        }
    }

    private void advance(LockerOrder order, DroneMission mission) {
        String nextStage = switch (mission.getStatus()) {
            case "LAUNCHING" -> "DEPARTED";
            case "DEPARTED" -> "EN_ROUTE";
            case "EN_ROUTE" -> "APPROACHING";
            case "APPROACHING" -> "ARRIVED";
            case "ARRIVED" -> "READY_FOR_PICKUP";
            default -> null;
        };
        if (nextStage == null) {
            return;
        }

        if ("READY_FOR_PICKUP".equals(nextStage)) {
            mission.setStatus("DEPOSITED");
            order.setStatus("STORING");
            order.setDeliveryStage(nextStage);
            order.setPinCode(String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
            order.setPinCodeIssuedAt(LocalDateTime.now());
            order.setPickupDeadline(LocalDateTime.now().plusHours(24));
        } else {
            mission.setStatus(nextStage);
            order.setDeliveryStage(nextStage);
        }
        missionRepository.save(mission);
        orderRepository.save(order);

        if ("READY_FOR_PICKUP".equals(nextStage)) {
            releaseDroneQuietly(mission);
        }
        if (List.of("DEPARTED", "APPROACHING", "ARRIVED", "READY_FOR_PICKUP").contains(nextStage)) {
            notifyQuietly(order, nextStage);
        }
    }

    private void releaseDroneQuietly(DroneMission mission) {
        if (mission.getDroneUnitId() == null) {
            return;
        }
        try {
            lockerDroneClient.updateDroneStatus(
                    mission.getDroneUnitId(), new DroneStatusUpdateRequest("IDLE", null));
        } catch (Exception ignored) {
            // A delivered parcel stays authoritative even if fleet status sync is temporarily unavailable.
        }
    }

    private void notifyQuietly(LockerOrder order, String stage) {
        String message = switch (stage) {
            case "DEPARTED" -> "Drone đã rời trạm và bắt đầu giao hàng.";
            case "APPROACHING" -> "Drone sắp đến tủ nhận hàng.";
            case "ARRIVED" -> "Drone đã đến tủ đích.";
            case "READY_FOR_PICKUP" -> "Hàng đã sẵn sàng. Vui lòng thanh toán trước khi mở tủ.";
            default -> "Đơn drone có cập nhật mới.";
        };
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            order.getUserId(),
                            "Cập nhật giao drone",
                            message,
                            "DRONE_DELIVERY_STATUS_CHANGED",
                            order.getId(),
                            "ORDER"));
        } catch (Exception ignored) {
            // Stage progression remains authoritative if notification-service is down.
        }
    }
}
