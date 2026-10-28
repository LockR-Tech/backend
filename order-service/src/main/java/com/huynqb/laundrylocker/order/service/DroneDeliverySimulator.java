package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneDeliverySessionRepository;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/// Bộ giả lập cho đơn DEMO: mỗi nhịp phát vị trí nội suy cho bản đồ trực tiếp và, khi
/// đủ thời gian một chặng, đẩy mission sang chặng kế tiếp. Đơn STANDARD không bị đụng tới.
@Service
@RequiredArgsConstructor
public class DroneDeliverySimulator {

    /// Hai chặng cuối do camera ArUco và nắp tủ thật quyết định (Chế độ B), không do đồng hồ.
    private static final Set<String> CAMERA_STAGES = Set.of("APPROACHING", "ARRIVED");

    private final DroneMissionRepository missionRepository;
    private final LockerOrderRepository orderRepository;
    private final DroneMissionProgressService progressService;
    private final DronePositionBroadcaster positionBroadcaster;
    private final OrderRules rules;
    private final DroneTelemetryRegistry telemetryRegistry;
    private final DroneDeliverySessionRepository sessionRepository;

    @Scheduled(fixedDelayString = "${app.drone.demo.scheduler-delay-ms:1000}")
    @Transactional
    public void advanceScheduledMissions() {
        advanceEligibleMissions(LocalDateTime.now());
    }

    void advanceEligibleMissions(LocalDateTime now) {
        long stageDelayMs = rules.droneDemoStageDelayMs();
        for (DroneMission mission : missionRepository.findByStatusIn(DroneMissionProgressService.IN_FLIGHT_STAGES)) {
            // Khoá đơn rồi mới đọc: lệnh "giao không hoàn tất" cũng khoá đơn, nên hai bên không chạy
            // xen. Danh sách nhiệm vụ ở trên được đọc TRƯỚC khi khoá và có thể đã cũ — chặng của đơn
            // (vừa đọc dưới khoá) phải còn khớp chặng của nhiệm vụ, nếu không đơn vừa bị đóng/đổi
            // chặng và lượt này bỏ qua, thay vì ghi đè một đơn đã kết thúc về "đang bay".
            LockerOrder order = orderRepository.findByIdForUpdate(mission.getOrderId()).orElse(null);
            if (order == null
                    || !"DEMO".equalsIgnoreCase(order.getFulfillmentMode())
                    || !"AWAITING_DISPATCH".equals(order.getStatus())
                    || !mission.getStatus().equals(order.getDeliveryStage())) {
                continue;
            }
            if (CAMERA_STAGES.contains(mission.getStatus()) && waitsForCamera(order, mission)) {
                positionBroadcaster.broadcast(order, mission, now, stageDelayMs);
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
                    "Drone (mô phỏng) chuyển chặng: "
                            + DroneMissionProgressService.stageLabel(previousStage) + " → "
                            + DroneMissionProgressService.stageLabel(DroneMissionProgressService.nextStage(mission.getStatus())));
            positionBroadcaster.broadcast(order, mission, now, stageDelayMs);
        }
    }

    /// Đơn đã có phiên giao ArUco (camera đã nhận mã, nắp tủ đang/đã được điều khiển) thì kết quả do tủ thật quyết
    /// định — kể cả khi admin vừa tắt chế độ ArUco, đồng hồ không được tự "giao xong" một kiện chưa vào ô.
    /// Chưa có phiên: chỉ chờ camera khi bật Chế độ B **và** drone của nhiệm vụ có Pi đang gửi telemetry; drone
    /// không có camera thì không bao giờ quét được mã, nên vẫn bay tiếp theo giờ như demo thường.
    private boolean waitsForCamera(LockerOrder order, DroneMission mission) {
        if (sessionRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).isPresent()) {
            return true;
        }
        return rules.droneDemoIndoorArucoEnabled()
                && mission.getDroneCode() != null
                && telemetryRegistry.latest(mission.getDroneCode()).isPresent();
    }
}
