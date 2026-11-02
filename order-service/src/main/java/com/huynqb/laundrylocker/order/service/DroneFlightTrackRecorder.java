package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.DroneFlightTrackPointResponse;
import com.huynqb.laundrylocker.order.model.DroneFlightTrackPoint;
import com.huynqb.laundrylocker.order.repository.DroneFlightTrackPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Lưu đường bay của đơn drone: cùng vị trí đang phát lên bản đồ trực tiếp (telemetry thật hoặc điểm nội suy
/// DEMO), tối đa một điểm mỗi {@link #MIN_INTERVAL_MS} cho mỗi đơn. Lỗi ghi bị nuốt: nhật ký chỉ để xem lại,
/// không được làm hỏng việc đẩy chặng.
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneFlightTrackRecorder {

    public static final String DRONE = OrderHistoryLocator.DRONE;
    public static final String DRONE_SIMULATED = OrderHistoryLocator.DRONE_SIMULATED;
    static final long MIN_INTERVAL_MS = 2_000;

    private final DroneFlightTrackPointRepository repository;
    private final Map<Long, Long> lastRecordedAtMs = new ConcurrentHashMap<>();

    public record Point(
            Long orderId,
            Long missionId,
            String droneCode,
            String stage,
            Double latitude,
            Double longitude,
            Double altitudeM,
            Double headingDeg,
            Double speedMps,
            Integer batteryPercent,
            String source,
            long observedAtMs) {
    }

    public void record(Point point) {
        if (point.orderId() == null || point.stage() == null || point.latitude() == null || point.longitude() == null) {
            return;
        }
        Long last = lastRecordedAtMs.get(point.orderId());
        if (last != null && point.observedAtMs() - last < MIN_INTERVAL_MS) {
            return;
        }
        try {
            DroneFlightTrackPoint row = new DroneFlightTrackPoint();
            row.setOrderId(point.orderId());
            row.setMissionId(point.missionId());
            row.setDroneCode(point.droneCode());
            row.setStage(point.stage().toUpperCase());
            row.setLatitude(point.latitude());
            row.setLongitude(point.longitude());
            row.setAltitudeM(point.altitudeM());
            row.setHeadingDeg(point.headingDeg());
            row.setSpeedMps(point.speedMps());
            row.setBatteryPercent(point.batteryPercent());
            row.setSource(point.source());
            // Cùng múi giờ với LocalDateTime.now() ở các cột thời gian khác của order-service.
            row.setRecordedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(point.observedAtMs()), ZoneId.systemDefault()));
            repository.save(row);
            lastRecordedAtMs.put(point.orderId(), point.observedAtMs());
        } catch (Exception ex) {
            log.debug("Flight track point of order {} not saved: {}", point.orderId(), ex.getMessage());
        }
    }

    public List<DroneFlightTrackPointResponse> track(Long orderId) {
        return repository.findByOrderIdOrderByRecordedAtAscIdAsc(orderId).stream()
                .map(p -> new DroneFlightTrackPointResponse(
                        p.getRecordedAt(), p.getStage(), p.getLatitude(), p.getLongitude(), p.getAltitudeM(),
                        p.getHeadingDeg(), p.getSpeedMps(), p.getBatteryPercent(), p.getDroneCode(), p.getSource()))
                .toList();
    }
}
