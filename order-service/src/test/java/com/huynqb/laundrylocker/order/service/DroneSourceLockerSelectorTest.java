package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.dto.LockerLayoutDto;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroneSourceLockerSelectorTest {
    private final LockerLookupClient lookup = mock(LockerLookupClient.class);
    private final LockerDroneClient drones = mock(LockerDroneClient.class);
    private final OrderRules rules = mock(OrderRules.class);
    private final DroneSourceLockerSelector selector = new DroneSourceLockerSelector(
            lookup, drones, rules, mock(DroneFlightControl.class));

    @Test
    void onlyIdleActiveDronesWithKnownSufficientBatteryCanServeANewPickup() {
        when(rules.droneMinPreflightBatteryPercent()).thenReturn(30);
        assertTrue(selector.ready(drone("IDLE", 90, true)));
        for (String status : List.of("RESERVED", "IN_FLIGHT", "FAULT", "MAINTENANCE")) {
            assertFalse(selector.ready(drone(status, 90, true)), status);
        }
        assertFalse(selector.ready(drone("IDLE", null, true)));
        assertFalse(selector.ready(drone("IDLE", 30, true)));
        assertFalse(selector.ready(drone("IDLE", 90, false)));
        // A flying drone is usable for its existing mission but never ready for another pickup.
        assertTrue(DroneSourceLockerSelector.usable(drone("IN_FLIGHT", 90, true)));
    }

    @Test
    void sourceNeedsAReadyDroneAndLandingPadButNoFreeDroneCell() {
        // Ô DRONE chỉ để nhận hàng: tủ bay hết ô trống vẫn phóng được vì kiện nạp thẳng lên drone.
        when(drones.getLockerLayout(1L)).thenReturn(ApiResponse.ok(new LockerLayoutDto(1L, "A", "A", "ACTIVE", true, "OK")));
        when(drones.listDroneUnits(1L)).thenReturn(ApiResponse.ok(List.of(drone("IDLE", 90, true))));
        assertTrue(selector.serviceable(1L));

        when(drones.listDroneUnits(1L)).thenReturn(ApiResponse.ok(List.of(drone("FAULT", 90, true))));
        assertFalse(selector.serviceable(1L));
        when(drones.listDroneUnits(1L)).thenReturn(ApiResponse.ok(List.of(drone("IDLE", 90, true))));
        when(drones.getLockerLayout(1L)).thenReturn(ApiResponse.ok(new LockerLayoutDto(1L, "A", "A", "ACTIVE", true, "FAULT")));
        assertFalse(selector.serviceable(1L));
    }

    @Test
    void destinationWithFaultyOrMaintenanceLandingPadIsRejected() {
        LockerInfo destFault = new LockerInfo(
                5L, 1L, "DST", "Destination", "ACTIVE", "", 10.71, 106.71, true, "M5", "FAULT", 8, 4);
        when(lookup.getLockers(null)).thenReturn(ApiResponse.ok(List.of(destFault)));

        BusinessException err = assertThrows(
                BusinessException.class, () -> selector.select(10.70, 106.70, 5L, null));
        assertEquals("LANDING_PAD_NOT_READY", err.getCode());
    }

    /// Trạm đang dừng bay không làm tủ gửi: chọn trạm kế tiếp; mọi trạm trong tầm đều dừng thì báo đúng lý do.
    @Test
    void suspendedStationsAreSkippedAsSource() {
        DroneFlightControl flightControl = mock(DroneFlightControl.class);
        DroneSourceLockerSelector withStops = new DroneSourceLockerSelector(lookup, drones, rules, flightControl);
        when(rules.droneMaxPickupMeters()).thenReturn(5000);
        when(rules.droneMaxRouteMeters()).thenReturn(10000);
        when(rules.droneMinPreflightBatteryPercent()).thenReturn(20);
        when(lookup.getLockers(null)).thenReturn(ApiResponse.ok(List.of(
                station(1L, 10.7000, 106.7000), station(2L, 10.7050, 106.7050), station(9L, 10.7200, 106.7200))));
        for (long id : new long[] {1L, 2L}) {
            when(drones.getLockerLayout(id)).thenReturn(ApiResponse.ok(new LockerLayoutDto(id, "S", "S", "ACTIVE", true, "OK")));
            when(drones.listDroneUnits(id)).thenReturn(ApiResponse.ok(List.of(drone("IDLE", 90, true))));
        }
        when(flightControl.activeSuspensions()).thenReturn(java.util.Map.of(1L, new com.huynqb.laundrylocker.order.model.DroneStationSuspension()));

        var selection = withStops.select(10.7001, 106.7001, 9L, null);
        assertEquals(2L, selection.source().id());

        when(flightControl.activeSuspensions()).thenReturn(java.util.Map.of(
                1L, new com.huynqb.laundrylocker.order.model.DroneStationSuspension(),
                2L, new com.huynqb.laundrylocker.order.model.DroneStationSuspension()));
        var error = assertThrows(BusinessException.class,
                () -> withStops.select(10.7001, 106.7001, 9L, null));
        assertEquals("DRONE_STATION_SUSPENDED", error.getCode());
    }

    /// Tủ gửi cùng chỗ với tủ nhận thì không có gì để bay: chọn tủ khác đủ xa; không còn tủ nào thì báo
    /// DRONE_ROUTE_TOO_SHORT. Demo trong phòng (đơn DEMO + Indoor Demo ArUco) vẫn được bay giữa hai tủ sát nhau.
    @Test
    void sourceTooCloseToTheDestinationIsSkippedUnlessShortRoutesAreAllowed() {
        when(rules.droneMaxPickupMeters()).thenReturn(5000);
        when(rules.droneMaxRouteMeters()).thenReturn(10000);
        when(rules.droneMinRouteMeters()).thenReturn(200);
        when(rules.droneMinPreflightBatteryPercent()).thenReturn(20);
        // Tủ 1 cùng toạ độ tủ nhận 9 (lab), tủ 2 cách ~1,1 km; điểm lấy hàng cách tủ nhận ~490 m.
        when(lookup.getLockers(null)).thenReturn(ApiResponse.ok(List.of(
                station(1L, 10.8411, 106.8097), station(2L, 10.8511, 106.8097), station(9L, 10.8411, 106.8097))));
        for (long id : new long[] {1L, 2L}) {
            when(drones.getLockerLayout(id)).thenReturn(ApiResponse.ok(new LockerLayoutDto(id, "S", "S", "ACTIVE", true, "OK")));
            when(drones.listDroneUnits(id)).thenReturn(ApiResponse.ok(List.of(drone("IDLE", 90, true))));
        }

        var farther = selector.select(10.8455, 106.8097, 9L, null);
        assertEquals(2L, farther.source().id(), "bỏ tủ sát tủ nhận, chọn tủ xa hơn");
        assertTrue(farther.routeDistanceMeters() >= 200);

        var tooShort = assertThrows(BusinessException.class, () -> selector.select(10.8455, 106.8097, 9L, 2L));
        assertEquals("DRONE_ROUTE_TOO_SHORT", tooShort.getCode());

        var demo = selector.select(10.8455, 106.8097, 9L, 2L, true);
        assertEquals(1L, demo.source().id());
        assertEquals(0, demo.routeDistanceMeters());
    }

    /// Tủ nhận sát nơi gửi (dưới quãng bay tối thiểu) thì không giao bằng drone — kể cả khi có tủ bay xa hơn
    /// làm quãng bay "đủ" (test 2026-10-29: tủ nhận cách người gửi 13 m vẫn đặt được, drone bay 1,6 km).
    @Test
    void destinationTooCloseToThePickupIsRejectedEvenWithAFartherSource() {
        when(rules.droneMaxPickupMeters()).thenReturn(5000);
        when(rules.droneMaxRouteMeters()).thenReturn(10000);
        when(rules.droneMinRouteMeters()).thenReturn(200);
        when(lookup.getLockers(null)).thenReturn(ApiResponse.ok(List.of(
                station(1L, 10.8411, 106.8097), station(2L, 10.8511, 106.8097), station(9L, 10.8411, 106.8097))));

        var error = assertThrows(BusinessException.class, () -> selector.select(10.8412, 106.8098, 9L, null));
        assertEquals("DRONE_ROUTE_TOO_SHORT", error.getCode());
        assertTrue(error.getMessage().contains("điểm lấy hàng"));
        verifyNoInteractions(drones);
    }

    /// Kiện rơi đã tìm thấy: trạm gần nơi tìm thấy nhất CÒN DRONE thắng trạm gần hơn đang hết drone; bãi đáp lỗi,
    /// trạm dừng bay, tủ nhận bị loại. Không trạm nào còn drone ⇒ trạm gần nhất có bãi đáp sẵn sàng (chờ drone).
    @Test
    void foundParcelGoesToTheNearestStationThatStillHasADrone() {
        DroneFlightControl flightControl = mock(DroneFlightControl.class);
        DroneSourceLockerSelector withStops = new DroneSourceLockerSelector(lookup, drones, rules, flightControl);
        when(rules.droneMaxRouteMeters()).thenReturn(20000);
        when(rules.droneMinPreflightBatteryPercent()).thenReturn(20);
        when(flightControl.activeSuspensions()).thenReturn(java.util.Map.of(
                4L, new com.huynqb.laundrylocker.order.model.DroneStationSuspension()));
        when(lookup.getLockers(null)).thenReturn(ApiResponse.ok(List.of(
                station(1L, 10.7001, 106.7001),   // gần nhất, hết drone
                station(2L, 10.7100, 106.7100),   // xa hơn, còn drone
                station(3L, 10.7002, 106.7002),   // bãi đáp lỗi
                station(4L, 10.7003, 106.7003),   // đang dừng bay
                station(9L, 10.7000, 106.7000)))); // tủ nhận
        when(drones.getLockerLayout(1L)).thenReturn(ApiResponse.ok(new LockerLayoutDto(1L, "S", "S", "ACTIVE", true, "OK")));
        when(drones.getLockerLayout(2L)).thenReturn(ApiResponse.ok(new LockerLayoutDto(2L, "S", "S", "ACTIVE", true, "OK")));
        when(drones.getLockerLayout(3L)).thenReturn(ApiResponse.ok(new LockerLayoutDto(3L, "S", "S", "ACTIVE", true, "FAULT")));
        when(drones.listDroneUnits(1L)).thenReturn(ApiResponse.ok(List.of(drone("IN_FLIGHT", 90, true))));
        when(drones.listDroneUnits(2L)).thenReturn(ApiResponse.ok(List.of(drone("IDLE", 90, true))));

        var withDrone = withStops.recoveryStation(10.7000, 106.7000, 9L).orElseThrow();
        assertEquals(2L, withDrone.locker().id());
        assertTrue(withDrone.droneAvailable());
        assertTrue(withDrone.distanceMeters() > 0);

        when(drones.listDroneUnits(2L)).thenReturn(ApiResponse.ok(List.of(drone("FAULT", 90, true))));
        var waiting = withStops.recoveryStation(10.7000, 106.7000, 9L).orElseThrow();
        assertEquals(1L, waiting.locker().id());
        assertFalse(waiting.droneAvailable());

        when(lookup.getLockers(null)).thenThrow(new RuntimeException("locker-service down"));
        assertTrue(withStops.recoveryStation(10.7000, 106.7000, 9L).isEmpty());
    }

    private static LockerInfo station(Long id, double lat, double lng) {
        return new LockerInfo(
                id, 1L, "S" + id, "Trạm " + id, "ACTIVE", null, lat, lng, true, null, 4, 4);
    }

    private DroneUnitDto drone(String status, Integer battery, boolean active) {
        return new DroneUnitDto(1L, 1L, "A1", status, battery, active);
    }
}
