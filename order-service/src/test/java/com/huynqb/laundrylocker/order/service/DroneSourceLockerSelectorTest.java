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

    private static LockerInfo station(Long id, double lat, double lng) {
        return new LockerInfo(
                id, 1L, "S" + id, "Trạm " + id, "ACTIVE", null, lat, lng, true, null, 4, 4);
    }

    private DroneUnitDto drone(String status, Integer battery, boolean active) {
        return new DroneUnitDto(1L, 1L, "A1", status, battery, active);
    }
}
