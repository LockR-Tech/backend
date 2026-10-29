package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.dto.LockerLayoutDto;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chọn tủ gửi cho một đơn drone lấy hàng tận nhà: tủ có bãi đáp GẦN ĐIỂM LẤY HÀNG NHẤT mà còn
 * phục vụ được. Người gửi không chọn tủ này — KTV lấy kiện ở nhà họ rồi mang về đây phóng drone.
 *
 * <p>Một tủ phục vụ được khi: đang hoạt động, có bãi đáp đang sẵn sàng, còn ít nhất một drone dùng
 * được (không lỗi, không bảo trì), nằm trong bán kính lấy hàng, và tới tủ nhận không quá tầm bay.
 * Ô DRONE chỉ dùng để NHẬN hàng: KTV nạp kiện thẳng lên drone, nên tủ bay không cần ô trống nào.
 * Trạm admin đang tạm dừng bay ({@link DroneFlightControl}) không được chọn làm tủ gửi.
 */
@Service
@RequiredArgsConstructor
public class DroneSourceLockerSelector {

    /// Drone ở các trạng thái này không bay được cho tới khi KTV xử lý xong.
    private static final Set<String> UNUSABLE_DRONE_STATUSES = Set.of("FAULT", "MAINTENANCE");

    private final LockerLookupClient lockerLookupClient;
    private final LockerDroneClient lockerDroneClient;
    private final OrderRules rules;
    private final DroneFlightControl flightControl;

    /// Tủ gửi được chọn kèm các khoảng cách (mét, đường chim bay).
    public record Selection(LockerInfo source, LockerInfo destination, int pickupDistanceMeters, int routeDistanceMeters) {
    }

    /**
     * @param excludedSourceLockerId tủ gửi cũ vừa gặp sự cố (chọn lại tủ khác); null khi đặt đơn.
     * @throws BusinessException khi điểm lấy hàng nằm ngoài vùng phục vụ hoặc không tủ nào phục vụ được.
     */
    public Selection select(double pickupLatitude, double pickupLongitude, Long destinationLockerId,
            Long excludedSourceLockerId) {
        return select(pickupLatitude, pickupLongitude, destinationLockerId, excludedSourceLockerId, false);
    }

    /**
     * @param allowShortRoute bỏ qua quãng bay tối thiểu — chỉ cho đơn DEMO khi bật Indoor Demo ArUco
     *                        ({@link OrderRules#droneShortRouteAllowed}).
     */
    public Selection select(double pickupLatitude, double pickupLongitude, Long destinationLockerId,
            Long excludedSourceLockerId, boolean allowShortRoute) {
        List<LockerInfo> lockers = allLockers();
        LockerInfo destination = lockers.stream()
                .filter(locker -> Objects.equals(locker.id(), destinationLockerId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "DRONE_DESTINATION_NOT_FOUND", "Destination locker was not found"));
        if (!"ACTIVE".equalsIgnoreCase(destination.status())) {
            throw new BusinessException("DRONE_LOCKER_INACTIVE", "Drone destination locker is not active");
        }
        if (!Boolean.TRUE.equals(destination.landingPad())) {
            throw new BusinessException("LANDING_PAD_ABSENT", "Drone destination locker has no landing pad");
        }
        if (destination.landingPadStatus() != null && !"OK".equalsIgnoreCase(destination.landingPadStatus())) {
            throw new BusinessException("LANDING_PAD_NOT_READY", "Drone destination landing pad is not ready");
        }
        if (destination.latitude() == null || destination.longitude() == null) {
            throw new BusinessException(
                    "DRONE_LOCKER_LOCATION_MISSING", "Destination locker has no coordinates; drone range cannot be checked");
        }

        double[] pickup = {pickupLatitude, pickupLongitude};
        int maxPickupMeters = rules.droneMaxPickupMeters();
        int maxRouteMeters = rules.droneMaxRouteMeters();
        int minRouteMeters = allowShortRoute ? 0 : rules.droneMinRouteMeters();

        // Tủ nhận sát nơi gửi thì không giao bằng drone, dù có tủ bay xa hơn để "đủ" quãng bay: trước đây hệ
        // thống chọn tủ bay cách 1,6 km rồi bay ngược về tủ cách người gửi 13 m (test 2026-10-29).
        int pickupToDestination = meters(pickup, destination);
        if (minRouteMeters > 0 && pickupToDestination < minRouteMeters) {
            throw new BusinessException(
                    "DRONE_ROUTE_TOO_SHORT",
                    "Tủ nhận chỉ cách điểm lấy hàng " + pickupToDestination + " m (dưới " + minRouteMeters
                            + " m) — không cần giao bằng drone, hãy gửi thẳng vào tủ nhận");
        }

        // Tủ có bãi đáp, có toạ độ, khác tủ nhận — xếp gần điểm lấy hàng nhất trước.
        List<Candidate> nearby = lockers.stream()
                .filter(locker -> !Objects.equals(locker.id(), destinationLockerId))
                .filter(locker -> !Objects.equals(locker.id(), excludedSourceLockerId))
                .filter(locker -> "ACTIVE".equalsIgnoreCase(locker.status()))
                .filter(locker -> Boolean.TRUE.equals(locker.landingPad()))
                .filter(locker -> locker.latitude() != null && locker.longitude() != null)
                .map(locker -> new Candidate(locker, meters(pickup, locker)))
                .filter(candidate -> candidate.pickupMeters() <= maxPickupMeters)
                .sorted(Comparator.comparingInt(Candidate::pickupMeters))
                .toList();
        if (nearby.isEmpty()) {
            throw new BusinessException(
                    "DRONE_PICKUP_OUT_OF_RANGE",
                    "No drone locker within " + maxPickupMeters + " m of the pickup location");
        }

        Set<Long> suspended = flightControl.activeSuspensions().keySet();
        boolean anyWithinRoute = false;
        boolean anyTooClose = false;
        boolean anySuspended = false;
        for (Candidate candidate : nearby) {
            int routeMeters = meters(
                    new double[] {candidate.locker().latitude(), candidate.locker().longitude()}, destination);
            if (maxRouteMeters > 0 && routeMeters > maxRouteMeters) {
                continue;
            }
            // Tủ gửi sát tủ nhận: không có gì để bay — thử tủ khác xa hơn.
            if (minRouteMeters > 0 && routeMeters < minRouteMeters) {
                anyTooClose = true;
                continue;
            }
            anyWithinRoute = true;
            if (suspended.contains(candidate.locker().id())) {
                anySuspended = true;
                continue;
            }
            if (serviceable(candidate.locker().id())) {
                return new Selection(candidate.locker(), destination, candidate.pickupMeters(), routeMeters);
            }
        }
        if (!anyWithinRoute && anyTooClose) {
            throw new BusinessException(
                    "DRONE_ROUTE_TOO_SHORT",
                    "Tủ nhận quá gần tủ gửi (dưới " + minRouteMeters + " m) — không cần giao bằng drone, hãy gửi "
                            + "thẳng vào tủ nhận");
        }
        if (!anyWithinRoute) {
            throw new BusinessException(
                    "DRONE_ROUTE_TOO_FAR",
                    "The destination locker is beyond the drone range of " + maxRouteMeters
                            + " m from every locker near the pickup location");
        }
        if (anySuspended) {
            throw new BusinessException(
                    "DRONE_STATION_SUSPENDED",
                    "Các trạm bay gần điểm lấy hàng đang tạm dừng hoặc chưa sẵn sàng. Vui lòng thử lại sau.");
        }
        throw new BusinessException(
                "DRONE_NO_SOURCE_LOCKER",
                "No locker near the pickup location currently has a ready landing pad and a usable drone");
    }

    /// Bãi đáp đang sẵn sàng và còn ít nhất một drone dùng được.
    boolean serviceable(Long lockerId) {
        LockerLayoutDto layout = data(lockerDroneClient.getLockerLayout(lockerId));
        if (layout == null
                || !"ACTIVE".equalsIgnoreCase(layout.status())
                || !Boolean.TRUE.equals(layout.landingPad())
                || !"OK".equalsIgnoreCase(layout.landingPadStatus())) {
            return false;
        }
        return hasUsableDrone(lockerId);
    }

    public boolean hasUsableDrone(Long lockerId) {
        List<DroneUnitDto> drones = data(lockerDroneClient.listDroneUnits(lockerId));
        return drones != null && drones.stream().anyMatch(this::ready);
    }

    public boolean ready(DroneUnitDto drone) {
        return usable(drone) && "IDLE".equals(drone.status())
                && drone.batteryPercent() != null && drone.batteryPercent() > rules.droneMinPreflightBatteryPercent();
    }

    public static boolean usable(DroneUnitDto drone) {
        return drone != null
                && Boolean.TRUE.equals(drone.active())
                && drone.status() != null
                && !UNUSABLE_DRONE_STATUSES.contains(drone.status());
    }

    private List<LockerInfo> allLockers() {
        List<LockerInfo> lockers = data(lockerLookupClient.getLockers(null));
        return lockers == null ? List.of() : lockers;
    }

    private static int meters(double[] from, LockerInfo to) {
        return (int) Math.round(DroneTelemetryService.distanceM(from, to.latitude(), to.longitude()));
    }

    private static <T> T data(com.huynqb.laundrylocker.common.dto.ApiResponse<T> response) {
        return response == null ? null : response.data();
    }

    private record Candidate(LockerInfo locker, int pickupMeters) {
    }
}
