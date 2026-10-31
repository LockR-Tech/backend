package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.DroneTechnicianPresence;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.DroneTechnicianPresenceRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/// Gắn toạ độ "kiện đang ở đâu" vào mỗi mốc trạng thái của đơn drone, đúng lúc mốc được ghi — để khách,
/// KTV và admin xem lại hành trình thấy cùng một vị trí cho từng mốc:
///
/// - chưa lấy hàng: điểm lấy hàng khách ghim (`PICKUP_ADDRESS`);
/// - KTV đã lấy kiện, đang mang về trạm: vị trí app KTV gửi gần nhất (`TECHNICIAN`), cũ quá thì điểm lấy hàng;
/// - kiện ở trạm/tủ gửi, chờ nạp hoặc chờ phóng: toạ độ tủ gửi (`SOURCE_LOCKER`);
/// - drone đang bay: GPS telemetry (`DRONE`), đơn DEMO thì điểm nội suy đang phát lên bản đồ (`DRONE_SIMULATED`);
/// - đã tới nơi / đã vào ô / đã lấy: toạ độ tủ nhận (`DESTINATION_LOCKER`);
/// - báo rơi kiện: điểm telemetry cuối cùng của drone nếu có.
///
/// Không đoán khi không có số đo (drone thật mất tín hiệu ⇒ để trống). Lỗi tra cứu bị nuốt: toạ độ chỉ là
/// phần hiển thị, không được làm hỏng việc ghi trạng thái.
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHistoryLocator {

    public static final String PICKUP_ADDRESS = "PICKUP_ADDRESS";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String SOURCE_LOCKER = "SOURCE_LOCKER";
    public static final String DRONE = "DRONE";
    public static final String DRONE_SIMULATED = "DRONE_SIMULATED";
    public static final String DESTINATION_LOCKER = "DESTINATION_LOCKER";

    /// Vị trí KTV cũ hơn mức này coi như không còn biết KTV đang ở đâu.
    static final Duration TECHNICIAN_LOCATION_MAX_AGE = Duration.ofMinutes(10);

    private static final Set<String> AT_DESTINATION_MISSION = Set.of("DEPOSITED");
    private static final Set<String> AT_DESTINATION_STAGE = Set.of("ARRIVED", "READY_FOR_PICKUP", "COMPLETED");
    private static final Set<String> AT_SOURCE_MISSION = Set.of("AWAITING_LOADING", "READY_TO_LAUNCH", "LAUNCHING");
    private static final Set<String> ENDED_MISSION = Set.of("FAILED", "CANCELED");

    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final DronePickupRepository pickupRepository;
    private final DroneTechnicianPresenceRepository presenceRepository;
    private final DroneTelemetryRegistry telemetry;
    private final DronePositionBroadcaster positions;
    private final OrderRules rules;

    public record Location(double latitude, double longitude, String source) {
    }

    /// Điền toạ độ cho dòng lịch sử sắp lưu nếu nơi ghi chưa tự điền.
    public void fill(OrderStatusHistory history) {
        if (history.getOrderId() == null || history.getLatitude() != null) {
            return;
        }
        try {
            Location location = locate(history.getOrderId());
            if (location != null) {
                history.setLatitude(location.latitude());
                history.setLongitude(location.longitude());
                history.setLocationSource(location.source());
            }
        } catch (Exception ex) {
            log.debug("Could not locate order {} for its status history: {}", history.getOrderId(), ex.getMessage());
        }
    }

    Location locate(Long orderId) {
        LockerOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !"DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return null;
        }
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        String missionStatus = mission == null ? null : mission.getStatus();
        String stage = order.getDeliveryStage();

        if (in(AT_DESTINATION_MISSION, missionStatus) || in(AT_DESTINATION_STAGE, stage)
                || "COMPLETED".equals(order.getStatus())) {
            return locker(destinationLockerId(order), DESTINATION_LOCKER);
        }
        if (mission != null && in(DroneMissionProgressService.IN_FLIGHT_STAGES, missionStatus)
                && !"LAUNCHING".equals(missionStatus)) {
            return inFlight(order, mission);
        }
        if (mission != null && "DROP_REPORTED".equals(missionStatus)) {
            return lastTelemetry(mission);
        }
        // Nhiệm vụ huỷ/lỗi trước khi rời trạm: kiện vẫn ở tủ gửi (drone lỗi giữa trời thì còn điểm telemetry cuối).
        if (mission != null && in(ENDED_MISSION, missionStatus)) {
            Location last = mission.getDroneCode() != null && telemetry.isLive(mission.getDroneCode())
                    ? lastTelemetry(mission) : null;
            return last != null ? last : locker(sourceLockerId(order, mission), SOURCE_LOCKER);
        }
        if (mission != null && (in(AT_SOURCE_MISSION, missionStatus) || missionStatus == null)) {
            return locker(sourceLockerId(order, mission), SOURCE_LOCKER);
        }
        return beforeStation(order, mission);
    }

    /// Kiện chưa tới trạm: còn ở điểm lấy hàng, hoặc KTV đang mang về.
    private Location beforeStation(LockerOrder order, DroneMission mission) {
        boolean homePickup = order.getPickupLatitude() != null && order.getPickupLongitude() != null;
        if (!homePickup) {
            // Khách tự bỏ kiện vào tủ gửi.
            return locker(sourceLockerId(order, mission), SOURCE_LOCKER);
        }
        // Lúc mới đặt chưa có bản ghi lấy hàng (tạo khi thanh toán) — kiện vẫn ở nhà người gửi.
        DronePickup pickup = pickupRepository.findByOrderId(order.getId()).orElse(null);
        if (pickup != null && DronePickup.PICKED_UP.equals(pickup.getStatus()) && pickup.getTechnicianId() != null) {
            DroneTechnicianPresence presence = presenceRepository.findById(pickup.getTechnicianId()).orElse(null);
            if (presence != null && presence.getLatitude() != null && presence.getLongitude() != null
                    && presence.getLocationAt() != null
                    && presence.getLocationAt().isAfter(LocalDateTime.now().minus(TECHNICIAN_LOCATION_MAX_AGE))) {
                return new Location(presence.getLatitude(), presence.getLongitude(), TECHNICIAN);
            }
        }
        return new Location(order.getPickupLatitude(), order.getPickupLongitude(), PICKUP_ADDRESS);
    }

    private Location inFlight(LockerOrder order, DroneMission mission) {
        if (mission.getDroneCode() != null && telemetry.isLive(mission.getDroneCode())) {
            Location live = lastTelemetry(mission);
            if (live != null) {
                return live;
            }
        }
        if ("DEMO".equalsIgnoreCase(order.getFulfillmentMode())) {
            double[] point = DronePositionBroadcaster.interpolate(mission.getStatus(), mission.getUpdatedAt(),
                    LocalDateTime.now(), rules.droneDemoStageDelayMs(),
                    positions.coordinates(sourceLockerId(order, mission)),
                    positions.coordinates(destinationLockerId(order)));
            return point == null ? null : new Location(point[0], point[1], DRONE_SIMULATED);
        }
        return null;
    }

    private Location lastTelemetry(DroneMission mission) {
        DroneTelemetryFrame frame = telemetry.latest(mission.getDroneCode()).orElse(null);
        return frame != null && frame.hasPosition() ? new Location(frame.lat(), frame.lng(), DRONE) : null;
    }

    private Location locker(Long lockerId, String source) {
        double[] point = positions.coordinates(lockerId);
        return point == null ? null : new Location(point[0], point[1], source);
    }

    /// `Set.of(...).contains(null)` ném NPE — đơn chưa có nhiệm vụ/chặng thì giá trị là null.
    private static boolean in(java.util.Collection<String> values, String value) {
        return value != null && values.contains(value);
    }

    private static Long sourceLockerId(LockerOrder order, DroneMission mission) {
        return mission != null && mission.getSourceLockerId() != null
                ? mission.getSourceLockerId() : order.getSourceLockerId();
    }

    private static Long destinationLockerId(LockerOrder order) {
        return order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
    }
}
