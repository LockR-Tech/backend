package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.dto.CellDto;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.dto.LockerLayoutDto;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroneSourceLockerSelectorTest {
    private final LockerDroneClient drones = mock(LockerDroneClient.class);
    private final LockerCellClient cells = mock(LockerCellClient.class);
    private final OrderRules rules = mock(OrderRules.class);
    private final DroneSourceLockerSelector selector = new DroneSourceLockerSelector(
            mock(LockerLookupClient.class), drones, cells, rules);

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
    void sourceMustHaveAFreeDroneCellAsWellAsAReadyDroneAndLandingPad() {
        when(drones.getLockerLayout(1L)).thenReturn(ApiResponse.ok(new LockerLayoutDto(1L, "A", "A", "ACTIVE", true, "OK")));
        when(drones.listDroneUnits(1L)).thenReturn(ApiResponse.ok(List.of(drone("IDLE", 90, true))));
        when(cells.findAvailable(1L, null, "DRONE")).thenReturn(ApiResponse.ok(null));
        assertFalse(selector.serviceable(1L));
        when(cells.findAvailable(1L, null, "DRONE")).thenReturn(ApiResponse.ok(new CellDto(1L, 1, "M", "DRONE", 0, 0, "AVAILABLE", null)));
        assertTrue(selector.serviceable(1L));
    }

    private DroneUnitDto drone(String status, Integer battery, boolean active) {
        return new DroneUnitDto(1L, 1L, "A1", status, battery, active);
    }
}
