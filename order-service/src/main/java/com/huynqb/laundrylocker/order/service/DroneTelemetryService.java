package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.DronePositionClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.dto.DronePositionUpdate;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryReport;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Biến telemetry của drone thật thành tiến trình giao hàng cho đơn STANDARD: tự đẩy chặng
 * bay theo vị trí, phát vị trí cho bản đồ trực tiếp và đồng bộ pin về đội bay.
 *
 * <p>Chặng được suy ra ở đây chứ không tin lời Pi: Pi chỉ báo số đo (toạ độ, đang bay hay
 * đã đáp), còn drone đang ở chặng nào so với tủ gửi/tủ nhận thì backend tính.
 * Đơn DEMO không đụng tới — đã có {@link DroneDeliverySimulator}. Điều phối viên vẫn xác
 * nhận tay được ({@link DroneMissionProgressService#advanceByOperator}) khi mất tín hiệu.
 */
@Slf4j
@Service
public class DroneTelemetryService {

    private static final double EARTH_RADIUS_M = 6_371_000;
    /// Pi gửi 1 bản tin/giây; chặn dày hơn thế để không dội notification-service.
    private static final long POSITION_MIN_INTERVAL_MS = 900;
    private static final long BATTERY_MIN_INTERVAL_MS = 30_000;

    private final DroneMissionRepository missionRepository;
    private final LockerOrderRepository orderRepository;
    private final DroneMissionProgressService progressService;
    private final AdminReferenceResolver references;
    private final DronePositionClient positionClient;
    private final LockerDroneClient lockerDroneClient;
    private final DroneTelemetryRegistry registry;
    private final TransactionTemplate transactions;
    private final double departedDistanceM;
    private final double approachDistanceM;
    private final double arrivalRadiusM;

    /// Toạ độ tủ không đổi trong lúc bay; nhớ lại để không gọi locker-service mỗi giây.
    private final Map<Long, double[]> lockerCoordinates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPositionAtMs = new ConcurrentHashMap<>();
    private final Map<String, BatterySync> lastBattery = new ConcurrentHashMap<>();

    public DroneTelemetryService(
            DroneMissionRepository missionRepository,
            LockerOrderRepository orderRepository,
            DroneMissionProgressService progressService,
            AdminReferenceResolver references,
            DronePositionClient positionClient,
            LockerDroneClient lockerDroneClient,
            DroneTelemetryRegistry registry,
            TransactionTemplate transactions,
            @Value("${app.drone.telemetry.departed-distance-m:30}") double departedDistanceM,
            @Value("${app.drone.telemetry.approach-distance-m:150}") double approachDistanceM,
            @Value("${app.drone.telemetry.arrival-radius-m:30}") double arrivalRadiusM) {
        this.missionRepository = missionRepository;
        this.orderRepository = orderRepository;
        this.progressService = progressService;
        this.references = references;
        this.positionClient = positionClient;
        this.lockerDroneClient = lockerDroneClient;
        this.registry = registry;
        this.transactions = transactions;
        this.departedDistanceM = departedDistanceM;
        this.approachDistanceM = approachDistanceM;
        this.arrivalRadiusM = arrivalRadiusM;
    }

    /// Xử lý một bản tin đã qua kiểm chữ ký. Không ném lỗi ra ngoài: hỏng một bản tin
    /// không được làm chết luồng nhận MQTT.
    public void ingest(String droneCode, DroneTelemetryFrame frame) {
        long now = System.currentTimeMillis();
        if (!registry.accept(droneCode, frame, now)) {
            return;
        }
        try {
            syncBatteryQuietly(droneCode, frame, now);
            track(droneCode, frame, now);
        } catch (Exception ex) {
            log.warn("Drone telemetry from {} not applied: {}", droneCode, ex.getMessage());
        }
    }

    private void track(String droneCode, DroneTelemetryFrame frame, long now) {
        DroneMission mission = missionRepository
                .findFirstByDroneCodeAndStatusInOrderByIdDesc(droneCode, DroneMissionProgressService.IN_FLIGHT_STAGES)
                .orElse(null);
        if (mission == null) {
            return;
        }
        LockerOrder order = orderRepository.findById(mission.getOrderId()).orElse(null);
        if (order == null || "DEMO".equalsIgnoreCase(order.getFulfillmentMode())) {
            return;
        }
        Route route = routeOf(order, mission);
        String stage = mission.getStatus();
        if (shouldAdvance(stage, frame, route)) {
            stage = transactions.execute(status -> advanceWhileReached(order.getId(), droneCode, frame, route));
        }
        publishPositionQuietly(order.getId(), droneCode, stage, frame, route, now);
    }

    /// Khoá đơn rồi mới kiểm lại điều kiện: điều phối viên có thể vừa xác nhận tay cùng chặng.
    /// Đẩy liên tiếp nếu bản tin này thoả nhiều chặng (mất tín hiệu giữa chừng rồi có lại).
    private String advanceWhileReached(Long orderId, String droneCode, DroneTelemetryFrame frame, Route route) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        if (order == null || mission == null || !droneCode.equals(mission.getDroneCode())) {
            return mission == null ? null : mission.getStatus();
        }
        for (int step = 0; step < DroneMissionProgressService.IN_FLIGHT_STAGES.size(); step++) {
            if (!shouldAdvance(mission.getStatus(), frame, route)) {
                break;
            }
            String next = DroneMissionProgressService.nextStage(mission.getStatus());
            progressService.advance(
                    order, mission, null, "Telemetry drone " + droneCode + ": " + DroneMissionProgressService.stageLabel(next));
        }
        return mission.getStatus();
    }

    /**
     * Bản tin này có chứng tỏ drone đã qua chặng {@code stage} không.
     *
     * <ul>
     *   <li>LAUNCHING → DEPARTED: drone đã rời mặt đất.</li>
     *   <li>DEPARTED → EN_ROUTE: đã xa tủ gửi.</li>
     *   <li>EN_ROUTE → APPROACHING: đã vào vùng tiếp cận tủ nhận.</li>
     *   <li>APPROACHING → ARRIVED: autopilot báo đã đáp, trong bán kính bãi đáp tủ nhận.</li>
     * </ul>
     * Chặng cuối (hàng vào ô, cấp mã mở ô) không tự đẩy: telemetry không biết kiện đã nằm
     * trong ô hay chưa, nên vẫn do điều phối viên xác nhận. Không có toạ độ tủ hoặc GPS
     * chưa khoá thì chỉ chặng cất cánh tự đẩy được; phần còn lại chờ xác nhận tay.
     */
    boolean shouldAdvance(String stage, DroneTelemetryFrame frame, Route route) {
        if ("LAUNCHING".equals(stage)) {
            return frame.airborne();
        }
        if (route == null || !frame.hasPosition()) {
            return false;
        }
        double fromSource = distanceM(route.source(), frame);
        double toDestination = distanceM(route.destination(), frame);
        // Tuyến ngắn hơn ngưỡng mặc định thì co ngưỡng theo chiều dài tuyến.
        double departed = Math.min(departedDistanceM, route.lengthM() / 4);
        double approach = Math.max(arrivalRadiusM, Math.min(approachDistanceM, route.lengthM() / 3));
        return switch (stage == null ? "" : stage) {
            case "DEPARTED" -> fromSource >= departed;
            case "EN_ROUTE" -> toDestination <= approach;
            case "APPROACHING" -> frame.onGround() && toDestination <= arrivalRadiusM;
            default -> false;
        };
    }

    private void publishPositionQuietly(
            Long orderId, String droneCode, String stage, DroneTelemetryFrame frame, Route route, long now) {
        if (stage == null || !frame.hasPosition() || !DroneMissionProgressService.IN_FLIGHT_STAGES.contains(stage)) {
            return;
        }
        Long last = lastPositionAtMs.get(droneCode);
        if (last != null && now - last < POSITION_MIN_INTERVAL_MS) {
            return;
        }
        lastPositionAtMs.put(droneCode, now);
        try {
            positionClient.publish(orderId, new DronePositionUpdate(
                    stage.toLowerCase(),
                    frame.lat(),
                    frame.lng(),
                    frame.headingDeg(),
                    etaMinutes(stage, frame, route),
                    frame.groundSpeedMs(),
                    frame.batteryPercent(),
                    frame.observedAtMs() == null ? now : frame.observedAtMs()));
        } catch (Exception ex) {
            // Bản đồ chỉ là phần hiển thị; chặng bay vẫn đúng khi notification-service lỗi.
            log.debug("Drone position for order {} not published: {}", orderId, ex.getMessage());
        }
    }

    /// Thời gian còn lại theo quãng đường và tốc độ đo được; drone đứng yên thì ước theo chặng.
    static Integer etaMinutes(String stage, DroneTelemetryFrame frame, Route route) {
        if ("ARRIVED".equals(stage)) {
            return 0;
        }
        if (route != null && frame.groundSpeedMs() != null && frame.groundSpeedMs() >= 1.0) {
            double seconds = distanceM(route.destination(), frame) / frame.groundSpeedMs();
            return (int) Math.ceil(seconds / 60);
        }
        return switch (stage) {
            case "LAUNCHING" -> 9;
            case "DEPARTED" -> 8;
            case "EN_ROUTE" -> 6;
            case "APPROACHING" -> 2;
            default -> 0;
        };
    }

    /// Pin thật để bước kiểm tra trước khi phóng không dựa vào số nhập tay. Chỉ gửi khi đổi
    /// và cách lần trước đủ lâu; drone chưa khai báo trên admin thì locker-service trả lỗi, bỏ qua.
    private void syncBatteryQuietly(String droneCode, DroneTelemetryFrame frame, long now) {
        Integer battery = frame.batteryPercent();
        if (battery == null) {
            return;
        }
        BatterySync last = lastBattery.get(droneCode);
        if (last != null && (last.percent() == battery || now - last.atMs() < BATTERY_MIN_INTERVAL_MS)) {
            return;
        }
        lastBattery.put(droneCode, new BatterySync(battery, now));
        try {
            lockerDroneClient.reportTelemetry(new DroneTelemetryReport(droneCode, battery));
        } catch (Exception ex) {
            log.debug("Battery of drone {} not synced: {}", droneCode, ex.getMessage());
        }
    }

    private Route routeOf(LockerOrder order, DroneMission mission) {
        Long sourceId = mission.getSourceLockerId() != null ? mission.getSourceLockerId() : order.getSourceLockerId();
        Long destinationId =
                order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        double[] source = coordinates(sourceId);
        double[] destination = coordinates(destinationId);
        if (source == null || destination == null) {
            return null;
        }
        return new Route(source, destination, distanceM(source, destination[0], destination[1]));
    }

    private double[] coordinates(Long lockerId) {
        if (lockerId == null) {
            return null;
        }
        double[] cached = lockerCoordinates.get(lockerId);
        if (cached != null) {
            return cached;
        }
        LockerInfo locker = references.lockers(List.of(lockerId)).get(lockerId);
        if (locker == null || locker.latitude() == null || locker.longitude() == null) {
            return null;
        }
        double[] coordinates = {locker.latitude(), locker.longitude()};
        lockerCoordinates.put(lockerId, coordinates);
        return coordinates;
    }

    private static double distanceM(double[] point, DroneTelemetryFrame frame) {
        return distanceM(point, frame.lat(), frame.lng());
    }

    /// Khoảng cách mặt đất (haversine), mét.
    static double distanceM(double[] from, double lat, double lng) {
        double lat1 = Math.toRadians(from[0]);
        double lat2 = Math.toRadians(lat);
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(lng - from[1]);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /// Tuyến tủ gửi → tủ nhận: toạ độ `{lat, lng}` và chiều dài đường thẳng.
    record Route(double[] source, double[] destination, double lengthM) {
    }

    private record BatterySync(int percent, long atMs) {
    }
}
