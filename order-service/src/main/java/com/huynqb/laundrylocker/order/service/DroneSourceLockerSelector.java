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
 */
@Service
@RequiredArgsConstructor
public class DroneSourceLockerSelector {

    /// Drone ở các trạng thái này không bay được cho tới khi KTV xử lý xong.
    private static final Set<String> UNUSABLE_DRONE_STATUSES = Set.of("FAULT", "MAINTENANCE");

    private final LockerLookupClient lockerLookupClient;
    private final LockerDroneClient lockerDroneClient;
    private final com.huynqb.laundrylocker.order.client.LockerCellClient lockerCellClient;
    private final OrderRules rules;

    /// Tủ gửi được chọn kèm các khoảng cách (mét, đường chim bay).
    public record Selection(LockerInfo source, LockerInfo destination, int pickupDistanceMeters, int routeDistanceMeters) {
    }

    /**
     * @param excludedSourceLockerId tủ gửi cũ vừa gặp sự cố (chọn lại tủ khác); null khi đặt đơn.
     * @throws BusinessException khi điểm lấy hàng nằm ngoài vùng phục vụ hoặc không tủ nào phục vụ được.
     */
    public Selection select(double pickupLatitude, double pickupLongitude, Long destinationLockerId,
            Long excludedSourceLockerId) {
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
        if (destination.latitude() == null || destination.longitude() == null) {
            throw new BusinessException(
                    "DRONE_LOCKER_LOCATION_MISSING", "Destination locker has no coordinates; drone range cannot be checked");
        }

        double[] pickup = {pickupLatitude, pickupLongitude};
        int maxPickupMeters = rules.droneMaxPickupMeters();
        int maxRouteMeters = rules.droneMaxRouteMeters();

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

        boolean anyWithinRoute = false;
        for (Candidate candidate : nearby) {
            int routeMeters = meters(
                    new double[] {candidate.locker().latitude(), candidate.locker().longitude()}, destination);
            if (maxRouteMeters > 0 && routeMeters > maxRouteMeters) {
                continue;
            }
            anyWithinRoute = true;
            if (serviceable(candidate.locker().id())) {
                return new Selection(candidate.locker(), destination, candidate.pickupMeters(), routeMeters);
            }
        }
        if (!anyWithinRoute) {
            throw new BusinessException(
                    "DRONE_ROUTE_TOO_FAR",
                    "The destination locker is beyond the drone range of " + maxRouteMeters
                            + " m from every locker near the pickup location");
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
        if (!hasUsableDrone(lockerId)) return false;
        var cell = data(lockerCellClient.findAvailable(lockerId, null, "DRONE"));
        return cell != null && "AVAILABLE".equalsIgnoreCase(cell.status());
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
