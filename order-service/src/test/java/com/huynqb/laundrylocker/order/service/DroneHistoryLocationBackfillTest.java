package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DroneHistoryLocationBackfillTest {

    private static final double[] A = {10.70, 106.70};
    private static final double[] B = {10.80, 106.80};
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 2, 7, 33);
    private long ids;

    private OrderStatusHistory row(String from, String to) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setId(++ids);
        h.setOldStatus(from);
        h.setNewStatus(to);
        h.setCreatedAt(T0.plusMinutes(ids));
        return h;
    }

    private static LockerOrder homePickupOrder() {
        LockerOrder order = new LockerOrder();
        order.setType("DRONE_DELIVERY");
        order.setPickupLatitude(10.65);
        order.setPickupLongitude(106.65);
        return order;
    }

    @Test
    void realPointsForStationsAndPickupAndAnEstimateAlongTheRouteInFlight() {
        List<OrderStatusHistory> history = List.of(
                row(null, "AWAITING_DISPATCH"),
                row("PICKUP_ASSIGNED", "PICKED_UP"),
                row("PICKED_UP", "ACCEPTED"),
                row("ACCEPTED", "LAUNCHING"),
                row("LAUNCHING", "DEPARTED"),
                row("DEPARTED", "EN_ROUTE"),
                row("EN_ROUTE", "EN_ROUTE"),
                row("APPROACHING", "ARRIVED"),
                row("ARRIVED", "READY_FOR_PICKUP"),
                row("READY_FOR_PICKUP", "COMPLETED"));

        List<DroneHistoryLocationBackfill.Located> rows =
                DroneHistoryLocationBackfill.locate(homePickupOrder(), null, history, A, B);

        assertEquals(OrderHistoryLocator.PICKUP_ADDRESS, rows.get(0).source());
        assertEquals(10.65, rows.get(1).latitude());
        assertEquals(OrderHistoryLocator.SOURCE_LOCKER, rows.get(2).source());
        assertEquals(10.70, rows.get(3).latitude());
        assertEquals(OrderHistoryLocator.ESTIMATED_ROUTE, rows.get(4).source());
        assertEquals(10.70, rows.get(4).latitude(), 1e-9);
        assertEquals(10.725, rows.get(5).latitude(), 1e-9);
        // Dòng ghi chú giữ vị trí của mốc trước.
        assertEquals(rows.get(5).latitude(), rows.get(6).latitude());
        assertEquals(OrderHistoryLocator.DESTINATION_LOCKER, rows.get(7).source());
        assertEquals(10.80, rows.get(9).latitude());
    }

    @Test
    void parcelDroppedInFlightIsPlacedAtTheFailedStage() {
        DroneMission mission = new DroneMission();
        mission.setFailedStage("APPROACHING");

        List<DroneHistoryLocationBackfill.Located> rows = DroneHistoryLocationBackfill.locate(
                homePickupOrder(), mission, List.of(row("APPROACHING", "DROP_REPORTED"), row("DROP_REPORTED", "CANCELED")),
                A, B);

        assertEquals(10.78, rows.get(0).latitude(), 1e-9);
        assertEquals(rows.get(0).latitude(), rows.get(1).latitude());
    }

    @Test
    void rowsAlreadyLocatedAreKeptAndMissingLockerCoordinatesAreMarkedUnlocated() {
        OrderStatusHistory known = row("LAUNCHING", "DEPARTED");
        known.setLatitude(10.71);
        known.setLongitude(106.71);
        known.setLocationSource(OrderHistoryLocator.DRONE);
        LockerOrder walkIn = new LockerOrder();
        walkIn.setType("DRONE_DELIVERY");

        List<DroneHistoryLocationBackfill.Located> rows = DroneHistoryLocationBackfill.locate(
                walkIn, null, List.of(known, row("DEPARTED", "EN_ROUTE"), row("ARRIVED", "READY_FOR_PICKUP")), A, null);

        assertEquals(2, rows.size());
        assertEquals(DroneHistoryLocationBackfill.UNLOCATED, rows.get(0).source());
        assertNull(rows.get(0).latitude());
        assertEquals(DroneHistoryLocationBackfill.UNLOCATED, rows.get(1).source());
    }
}
