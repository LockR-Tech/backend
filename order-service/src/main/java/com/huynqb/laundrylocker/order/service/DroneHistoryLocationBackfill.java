package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/// Bù toạ độ cho các mốc của đơn drone có từ trước khi order-service ghi toạ độ lúc ghi mốc (Flyway V37), để
/// khách, KTV và admin xem lại hành trình đơn cũ cũng thấy kiện ở đâu.
///
/// Chỉ xử lý đơn chưa có mốc nào được định vị — đơn mới vẫn theo {@link OrderHistoryLocator}, không đoán khi
/// mất tín hiệu. Điểm lấy hàng, tủ gửi và tủ nhận là toạ độ thật. Không có số đo GPS của chuyến bay cũ nên chặng
/// đang bay là điểm ước trên đoạn thẳng tủ gửi → tủ nhận, nguồn {@link OrderHistoryLocator#ESTIMATED_ROUTE}.
/// Dòng không định vị được ghi nguồn {@link #UNLOCATED} để không thử lại mãi.
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneHistoryLocationBackfill {

    static final String UNLOCATED = "UNLOCATED";
    static final int BATCH_SIZE = 100;

    /// Phần quãng đường A → B lúc bắt đầu mỗi chặng bay — cùng mốc với bộ giả lập (`DronePositionBroadcaster`).
    private static final Map<String, Double> FLIGHT_PROGRESS = Map.of(
            "DEPARTED", 0.0,
            "EN_ROUTE", 0.25,
            "APPROACHING", 0.80);
    private static final Set<String> AT_SOURCE = Set.of(
            "ACCEPTED", "AWAITING_LOADING", "READY_TO_LAUNCH", "LAUNCHING");
    private static final Set<String> AT_DESTINATION = Set.of(
            "ARRIVED", "DEPOSITED", "READY_FOR_PICKUP", "STORING", "COMPLETED", "EXPIRED");
    private static final Set<String> BEFORE_PICKUP = Set.of(
            "INITIALIZED", "AWAITING_DISPATCH", "AWAITING_PAYMENT", "PICKUP_ASSIGNED", "PENDING_PICKUP");

    private static final String PENDING_ORDERS = """
            SELECT o.id FROM order_schema.orders o
            WHERE o.type = 'DRONE_DELIVERY'
              AND EXISTS (SELECT 1 FROM order_schema.order_status_history h
                          WHERE h.order_id = o.id AND h.location_source IS NULL)
              AND NOT EXISTS (SELECT 1 FROM order_schema.order_status_history h
                              WHERE h.order_id = o.id AND h.location_source IS NOT NULL
                                AND h.location_source <> 'UNLOCATED')
            ORDER BY o.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final AdminReferenceResolver references;

    record Located(Long historyId, Double latitude, Double longitude, String source) {
    }

    @Scheduled(initialDelayString = "${app.drone.history-backfill.initial-delay-ms:120000}",
            fixedDelayString = "${app.drone.history-backfill.delay-ms:1800000}")
    public void run() {
        try {
            int located = backfill();
            if (located > 0) {
                log.info("Backfilled locations of {} old drone order milestones", located);
            }
        } catch (Exception ex) {
            log.warn("Drone history location backfill stopped: {}", ex.getMessage());
        }
    }

    /// Một lượt tối đa {@link #BATCH_SIZE} đơn; trả số mốc vừa có toạ độ.
    public int backfill() {
        int located = 0;
        for (Long orderId : jdbc.queryForList(PENDING_ORDERS, Long.class, BATCH_SIZE)) {
            located += backfillOrder(orderId);
        }
        return located;
    }

    int backfillOrder(Long orderId) {
        LockerOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return 0;
        }
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        Long sourceId = mission != null && mission.getSourceLockerId() != null
                ? mission.getSourceLockerId() : order.getSourceLockerId();
        Long destinationId = order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        Lookup<LockerInfo> lockers = references.lockers(
                Stream.of(sourceId, destinationId).filter(Objects::nonNull).distinct().toList());
        if (!lockers.available()) {
            // locker-service lỗi: để lượt sau, không đánh dấu nhầm là không định vị được.
            return 0;
        }
        List<Located> result = locate(order, mission,
                historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId),
                coordinates(sourceId == null ? null : lockers.values().get(sourceId)),
                coordinates(destinationId == null ? null : lockers.values().get(destinationId)));
        int located = 0;
        for (Located row : result) {
            jdbc.update("UPDATE order_schema.order_status_history SET latitude = ?, longitude = ?, location_source = ? "
                            + "WHERE id = ? AND location_source IS NULL",
                    row.latitude(), row.longitude(), row.source(), row.historyId());
            if (row.latitude() != null) {
                located++;
            }
        }
        return located;
    }

    /// Toạ độ cho từng dòng lịch sử chưa có nguồn, theo trạng thái/chặng mà dòng ghi lại. Dòng chỉ ghi chú, huỷ
    /// hay lỗi giữ vị trí của dòng trước nó.
    static List<Located> locate(LockerOrder order, DroneMission mission, List<OrderStatusHistory> oldestFirst,
                                double[] source, double[] destination) {
        boolean homePickup = order.getPickupLatitude() != null && order.getPickupLongitude() != null;
        double[] pickup = homePickup ? new double[] {order.getPickupLatitude(), order.getPickupLongitude()} : null;
        List<Located> result = new ArrayList<>();
        Located previous = null;
        for (OrderStatusHistory h : oldestFirst) {
            Located here = locateOne(h, mission, pickup, source, destination, previous);
            if (h.getLocationSource() == null) {
                result.add(here == null
                        ? new Located(h.getId(), null, null, UNLOCATED)
                        : new Located(h.getId(), here.latitude(), here.longitude(), here.source()));
            }
            if (here != null) {
                previous = here;
            }
        }
        return result;
    }

    private static Located locateOne(OrderStatusHistory h, DroneMission mission, double[] pickup,
                                     double[] source, double[] destination, Located previous) {
        if (h.getLocationSource() != null && h.getLatitude() != null) {
            return new Located(h.getId(), h.getLatitude(), h.getLongitude(), h.getLocationSource());
        }
        String stage = h.getNewStatus() == null ? "" : h.getNewStatus().toUpperCase();
        boolean noteOnly = Objects.equals(h.getOldStatus(), h.getNewStatus());
        if (noteOnly && previous != null) {
            return previous;
        }
        if (AT_DESTINATION.contains(stage)) {
            return point(h, destination, OrderHistoryLocator.DESTINATION_LOCKER);
        }
        if (FLIGHT_PROGRESS.containsKey(stage)) {
            return along(h, source, destination, FLIGHT_PROGRESS.get(stage));
        }
        if ("DROP_REPORTED".equals(stage)) {
            String failed = mission == null || mission.getFailedStage() == null ? "EN_ROUTE" : mission.getFailedStage();
            return along(h, source, destination, FLIGHT_PROGRESS.getOrDefault(failed.toUpperCase(), 0.5));
        }
        if (AT_SOURCE.contains(stage)) {
            return point(h, source, OrderHistoryLocator.SOURCE_LOCKER);
        }
        if ("PICKED_UP".equals(stage) && pickup != null) {
            return point(h, pickup, OrderHistoryLocator.PICKUP_ADDRESS);
        }
        if (BEFORE_PICKUP.contains(stage) || previous == null) {
            return pickup != null
                    ? point(h, pickup, OrderHistoryLocator.PICKUP_ADDRESS)
                    : point(h, source, OrderHistoryLocator.SOURCE_LOCKER);
        }
        // Huỷ, lỗi, chặng lạ: kiện vẫn ở chỗ của mốc trước.
        return previous;
    }

    private static Located point(OrderStatusHistory h, double[] at, String source) {
        return at == null ? null : new Located(h.getId(), at[0], at[1], source);
    }

    private static Located along(OrderStatusHistory h, double[] source, double[] destination, double progress) {
        if (source == null || destination == null) {
            return null;
        }
        return new Located(h.getId(),
                source[0] + (destination[0] - source[0]) * progress,
                source[1] + (destination[1] - source[1]) * progress,
                OrderHistoryLocator.ESTIMATED_ROUTE);
    }

    private static double[] coordinates(LockerInfo locker) {
        return locker == null || locker.latitude() == null || locker.longitude() == null
                ? null : new double[] {locker.latitude(), locker.longitude()};
    }
}
