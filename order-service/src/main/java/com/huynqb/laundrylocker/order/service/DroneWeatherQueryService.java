package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.dto.DroneWeatherViews;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Thời tiết cho màn hình: tuyến bay của một đơn (khách, KTV, admin cùng thấy một kết quả) và thời tiết
 * tại các tủ có bãi đáp (KTV, admin điều phối). Quyền xem đơn kiểm ở controller trước khi gọi vào đây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneWeatherQueryService {

    private static final Set<String> AWAITING_PICKUP_STAGES = Set.of("AWAITING_DISPATCH", "PICKUP_ASSIGNED");

    /// Các trạng thái / chặng mà chuyến bay đã kết thúc (kiện đã vào ô nhận hoặc đơn đã đóng/huỷ/rơi kiện):
    /// không còn lấy dự báo thời tiết 30 phút cho đơn nữa.
    private static final Set<String> FLIGHT_ENDED_ORDER_STATUSES = Set.of(
            "STORING", "COMPLETED", "CANCELED", "CANCELLED", "EXPIRED");
    private static final Set<String> FLIGHT_ENDED_DELIVERY_STAGES = Set.of(
            "READY_FOR_PICKUP", "DELIVERED", "COMPLETED", "FAILED",
            "CANCELED", "CANCELLED", "EXPIRED", "DROP_REPORTED");

    private final LockerOrderRepository orderRepository;
    private final LockerLookupClient lockerLookupClient;
    private final DroneWeatherService weatherService;
    private final DroneFlightControl flightControl;

    public DroneWeatherViews.RouteWeather forOrder(Long orderId) {
        LockerOrder order = orderRepository.findById(orderId)
                .filter(found -> "DRONE_DELIVERY".equalsIgnoreCase(found.getType()))
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        Long sourceId = order.getSourceLockerId();
        Long destinationId = order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        List<LockerInfo> lockers = lockers(Stream.of(sourceId, destinationId).filter(Objects::nonNull).toList());
        LockerInfo source = find(lockers, sourceId);
        LockerInfo destination = find(lockers, destinationId);
        if (isFlightEnded(order)) {
            return new DroneWeatherViews.RouteWeather(
                    order.getId(),
                    sourceId, source == null ? null : source.name(),
                    destinationId, destination == null ? null : destination.name(),
                    null);
        }
        // Kiện chưa rời nhà người gửi: xét cả chặng nhà → tủ bay như lúc đặt đơn; đã lấy rồi thì chỉ còn tuyến bay.
        boolean beforePickup = AWAITING_PICKUP_STAGES.contains(order.getDeliveryStage());
        DroneWeatherAssessment weather = source == null || destination == null
                ? weatherService.unavailable(OffsetDateTime.now(ZoneOffset.UTC))
                : beforePickup
                        ? weatherService.assessJourney(order.getPickupLatitude(), order.getPickupLongitude(), source, destination)
                        : weatherService.assessRoute(source, destination);
        return new DroneWeatherViews.RouteWeather(
                order.getId(),
                sourceId, source == null ? null : source.name(),
                destinationId, destination == null ? null : destination.name(),
                weather);
    }

    private static boolean isFlightEnded(LockerOrder order) {
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase(Locale.ROOT);
        if (FLIGHT_ENDED_ORDER_STATUSES.contains(status)) {
            return true;
        }
        String stage = order.getDeliveryStage() == null ? "" : order.getDeliveryStage().trim().toUpperCase(Locale.ROOT);
        return FLIGHT_ENDED_DELIVERY_STAGES.contains(stage);
    }

    /// Tủ đang hoạt động, có bãi đáp và toạ độ — sắp theo tên — kèm lệnh dừng bay của từng trạm.
    public List<DroneWeatherViews.LockerWeather> droneLockers() {
        var suspended = flightControl.activeSuspensions();
        return lockers(null).stream()
                .filter(locker -> "ACTIVE".equalsIgnoreCase(locker.status()))
                .filter(locker -> Boolean.TRUE.equals(locker.landingPad()))
                .filter(locker -> locker.latitude() != null && locker.longitude() != null)
                .sorted(Comparator.comparing(locker -> locker.name() == null ? "" : locker.name()))
                .map(locker -> new DroneWeatherViews.LockerWeather(
                        locker.id(), locker.code(), locker.name(), locker.address(),
                        locker.latitude(), locker.longitude(), weatherService.assessLocker(locker),
                        DroneFlightControl.view(suspended.get(locker.id()))))
                .toList();
    }

    private List<LockerInfo> lockers(List<Long> ids) {
        try {
            var response = lockerLookupClient.getLockers(ids);
            List<LockerInfo> data = response == null ? null : response.data();
            return data == null ? List.of() : data;
        } catch (Exception ex) {
            log.warn("Could not load lockers for the weather view: {}", ex.getMessage());
            return List.of();
        }
    }

    private static LockerInfo find(List<LockerInfo> lockers, Long id) {
        return id == null ? null : lockers.stream().filter(locker -> id.equals(locker.id())).findFirst().orElse(null);
    }
}
