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
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Thời tiết cho màn hình: tuyến bay của một đơn (khách, KTV, admin cùng thấy một kết quả) và thời tiết
 * tại các tủ có bãi đáp (KTV, admin điều phối). Quyền xem đơn kiểm ở controller trước khi gọi vào đây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneWeatherQueryService {

    private static final java.util.Set<String> AWAITING_PICKUP_STAGES = java.util.Set.of("AWAITING_DISPATCH", "PICKUP_ASSIGNED");

    private final LockerOrderRepository orderRepository;
    private final LockerLookupClient lockerLookupClient;
    private final DroneWeatherService weatherService;

    public DroneWeatherViews.RouteWeather forOrder(Long orderId) {
        LockerOrder order = orderRepository.findById(orderId)
                .filter(found -> "DRONE_DELIVERY".equalsIgnoreCase(found.getType()))
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        Long sourceId = order.getSourceLockerId();
        Long destinationId = order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        List<LockerInfo> lockers = lockers(Stream.of(sourceId, destinationId).filter(Objects::nonNull).toList());
        LockerInfo source = find(lockers, sourceId);
        LockerInfo destination = find(lockers, destinationId);
        // Kiện chưa rời nhà người gửi: xét cả chặng nhà → tủ bay như lúc đặt đơn; đã lấy rồi thì chỉ còn tuyến bay.
        boolean beforePickup = AWAITING_PICKUP_STAGES.contains(order.getDeliveryStage());
        DroneWeatherAssessment weather = source == null || destination == null
                ? DroneWeatherAssessment.unavailable(OffsetDateTime.now(ZoneOffset.UTC))
                : beforePickup
                        ? weatherService.assessJourney(order.getPickupLatitude(), order.getPickupLongitude(), source, destination)
                        : weatherService.assessRoute(source, destination);
        return new DroneWeatherViews.RouteWeather(
                order.getId(),
                sourceId, source == null ? null : source.name(),
                destinationId, destination == null ? null : destination.name(),
                weather);
    }

    /// Tủ đang hoạt động, có bãi đáp và toạ độ — sắp theo tên.
    public List<DroneWeatherViews.LockerWeather> droneLockers() {
        return lockers(null).stream()
                .filter(locker -> "ACTIVE".equalsIgnoreCase(locker.status()))
                .filter(locker -> Boolean.TRUE.equals(locker.landingPad()))
                .filter(locker -> locker.latitude() != null && locker.longitude() != null)
                .sorted(Comparator.comparing(locker -> locker.name() == null ? "" : locker.name()))
                .map(locker -> new DroneWeatherViews.LockerWeather(
                        locker.id(), locker.code(), locker.name(), locker.address(),
                        locker.latitude(), locker.longitude(), weatherService.assessLocker(locker)))
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
