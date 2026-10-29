package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.DronePositionClient;
import com.huynqb.laundrylocker.order.dto.DronePositionUpdate;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phát vị trí drone của đơn DEMO cho bản đồ trực tiếp.
 *
 * <p>Đơn DEMO không có drone thật nên vị trí là điểm nội suy trên đoạn thẳng tủ gửi →
 * tủ nhận, theo chặng hiện tại và thời gian đã trôi trong chặng. Đơn STANDARD không
 * phát gì ở đây: vị trí thật phải đến từ telemetry của drone.
 */
@Component
@RequiredArgsConstructor
public class DronePositionBroadcaster {

    /// Phần quãng đường đã đi khi BẮT ĐẦU mỗi chặng (0 = tại tủ gửi, 1 = tại tủ nhận).
    private static final Map<String, double[]> STAGE_PROGRESS = Map.of(
            "LAUNCHING", new double[] {0.0, 0.0},
            "DEPARTED", new double[] {0.0, 0.25},
            "EN_ROUTE", new double[] {0.25, 0.80},
            "APPROACHING", new double[] {0.80, 1.0},
            "ARRIVED", new double[] {1.0, 1.0});

    private final AdminReferenceResolver references;
    private final DronePositionClient positionClient;

    /// Nhớ toạ độ tủ để không gọi locker-service mỗi giây, nhưng chỉ trong một phút: admin dời tủ
    /// (sửa toạ độ) thì chuyến bay mô phỏng kế tiếp phải bay tới chỗ mới mà không cần khởi động lại.
    static final long LOCKER_COORDINATES_TTL_MS = 60_000;

    private final Map<Long, CachedCoordinates> lockerCoordinates = new ConcurrentHashMap<>();

    private record CachedCoordinates(double[] coordinates, long fetchedAtMs) {
    }

    /// Lỗi tra toạ độ hay lỗi gửi đều bị nuốt: bản đồ chỉ là phần hiển thị, không được
    /// làm chậm hay làm hỏng việc đẩy chặng.
    public void broadcast(LockerOrder order, DroneMission mission, LocalDateTime now, long stageDelayMs) {
        try {
            DronePositionUpdate position = positionOf(order, mission, now, stageDelayMs);
            if (position != null) {
                positionClient.publish(order.getId(), position);
            }
        } catch (Exception ignored) {
            // Xem chú thích trên.
        }
    }

    DronePositionUpdate positionOf(LockerOrder order, DroneMission mission, LocalDateTime now, long stageDelayMs) {
        double[] range = STAGE_PROGRESS.get(mission.getStatus());
        if (range == null) {
            return null;
        }
        Long sourceId = mission.getSourceLockerId() != null ? mission.getSourceLockerId() : order.getSourceLockerId();
        Long destinationId =
                order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        double[] source = coordinates(sourceId);
        double[] destination = coordinates(destinationId);
        if (source == null || destination == null) {
            return null;
        }

        double[] point = interpolate(mission.getStatus(), mission.getUpdatedAt(), now, stageDelayMs, source, destination);
        return new DronePositionUpdate(
                mission.getStatus().toLowerCase(),
                point[0],
                point[1],
                bearing(source, destination),
                etaMinutes(mission.getStatus()),
                null,
                null,
                System.currentTimeMillis());
    }

    /// Điểm drone DEMO trên đoạn thẳng tủ gửi → tủ nhận: phần quãng đường của chặng + thời gian đã trôi
    /// trong chặng. Dùng chung cho luồng STOMP và API chi tiết đơn, để bản đồ và ô toạ độ cùng một số.
    /// null khi chặng không phải chặng bay.
    static double[] interpolate(
            String stage, LocalDateTime stageStartedAt, LocalDateTime now, long stageDelayMs,
            double[] source, double[] destination) {
        double[] range = STAGE_PROGRESS.get(stage);
        if (range == null || source == null || destination == null) {
            return null;
        }
        double inStage = 0;
        if (stageStartedAt != null && stageDelayMs > 0) {
            long elapsed = Duration.between(stageStartedAt, now).toMillis();
            inStage = Math.max(0, Math.min(1, (double) elapsed / stageDelayMs));
        }
        double progress = range[0] + (range[1] - range[0]) * inStage;
        return new double[] {
            source[0] + (destination[0] - source[0]) * progress,
            source[1] + (destination[1] - source[1]) * progress
        };
    }

    private double[] coordinates(Long lockerId) {
        if (lockerId == null) {
            return null;
        }
        long nowMs = System.currentTimeMillis();
        CachedCoordinates cached = lockerCoordinates.get(lockerId);
        if (cached != null && nowMs - cached.fetchedAtMs() < LOCKER_COORDINATES_TTL_MS) {
            return cached.coordinates();
        }
        LockerInfo locker = references.lockers(List.of(lockerId)).get(lockerId);
        if (locker == null || locker.latitude() == null || locker.longitude() == null) {
            return null;
        }
        double[] coordinates = {locker.latitude(), locker.longitude()};
        lockerCoordinates.put(lockerId, new CachedCoordinates(coordinates, nowMs));
        return coordinates;
    }

    /// Hướng mũi drone, độ (0 = Bắc, thuận kim đồng hồ).
    private static double bearing(double[] from, double[] to) {
        double lat1 = Math.toRadians(from[0]);
        double lat2 = Math.toRadians(to[0]);
        double deltaLng = Math.toRadians(to[1] - from[1]);
        double y = Math.sin(deltaLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static Integer etaMinutes(String stage) {
        return switch (stage) {
            case "LAUNCHING" -> 9;
            case "DEPARTED" -> 8;
            case "EN_ROUTE" -> 6;
            case "APPROACHING" -> 2;
            default -> 0;
        };
    }
}
