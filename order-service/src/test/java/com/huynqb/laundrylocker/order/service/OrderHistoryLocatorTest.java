package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.DroneTechnicianPresence;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.DroneTechnicianPresenceRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderHistoryLocatorTest {

    private static final long ORDER_ID = 501L;
    private static final double[] SOURCE = {10.7700, 106.7000};
    private static final double[] DESTINATION = {10.7800, 106.6600};

    private final LockerOrderRepository orders = mock(LockerOrderRepository.class);
    private final DroneMissionRepository missions = mock(DroneMissionRepository.class);
    private final DronePickupRepository pickups = mock(DronePickupRepository.class);
    private final DroneTechnicianPresenceRepository presences = mock(DroneTechnicianPresenceRepository.class);
    private final DroneTelemetryRegistry telemetry = mock(DroneTelemetryRegistry.class);
    private final DronePositionBroadcaster positions = mock(DronePositionBroadcaster.class);
    private final OrderRules rules = mock(OrderRules.class);
    private final OrderHistoryLocator locator =
            new OrderHistoryLocator(orders, missions, pickups, presences, telemetry, positions, rules);

    private LockerOrder order;

    @BeforeEach
    void setUp() {
        order = new LockerOrder();
        order.setId(ORDER_ID);
        order.setType("DRONE_DELIVERY");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("AWAITING_DISPATCH");
        order.setFulfillmentMode("DEMO");
        order.setSourceLockerId(11L);
        order.setDestinationLockerId(22L);
        order.setPickupLatitude(10.8031);
        order.setPickupLongitude(106.6654);
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(missions.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(pickups.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(positions.coordinates(11L)).thenReturn(SOURCE);
        when(positions.coordinates(22L)).thenReturn(DESTINATION);
        when(rules.droneDemoStageDelayMs()).thenReturn(10_000L);
    }

    private DroneMission mission(String status) {
        DroneMission mission = new DroneMission();
        mission.setStatus(status);
        mission.setSourceLockerId(11L);
        mission.setDroneCode("LR-DRN-07");
        mission.setUpdatedAt(LocalDateTime.now());
        when(missions.findByOrderId(ORDER_ID)).thenReturn(Optional.of(mission));
        return mission;
    }

    private DronePickup pickup(String status) {
        DronePickup pickup = new DronePickup();
        pickup.setStatus(status);
        pickup.setTechnicianId(77L);
        when(pickups.findByOrderId(ORDER_ID)).thenReturn(Optional.of(pickup));
        return pickup;
    }

    private static void assertAt(OrderHistoryLocator.Location location, double[] point, String source) {
        assertEquals(source, location.source());
        assertEquals(point[0], location.latitude(), 1e-9);
        assertEquals(point[1], location.longitude(), 1e-9);
    }

    @Test
    void notADroneOrderHasNoLocation() {
        order.setType("SEND");
        assertNull(locator.locate(ORDER_ID));
    }

    @Test
    void beforePickupTheParcelIsAtThePinnedPickupPoint() {
        // Vừa đặt: bản ghi lấy hàng chưa có (tạo lúc thanh toán).
        assertAt(locator.locate(ORDER_ID), new double[] {10.8031, 106.6654}, OrderHistoryLocator.PICKUP_ADDRESS);
        pickup(DronePickup.ACCEPTED);
        assertAt(locator.locate(ORDER_ID), new double[] {10.8031, 106.6654}, OrderHistoryLocator.PICKUP_ADDRESS);
    }

    @Test
    void pickedUpUsesTheTechniciansFreshLocationElseThePickupPoint() {
        order.setDeliveryStage("PICKED_UP");
        pickup(DronePickup.PICKED_UP);
        DroneTechnicianPresence presence = new DroneTechnicianPresence();
        presence.setLatitude(10.8040);
        presence.setLongitude(106.6660);
        presence.setLocationAt(LocalDateTime.now().minusMinutes(1));
        when(presences.findById(77L)).thenReturn(Optional.of(presence));
        assertAt(locator.locate(ORDER_ID), new double[] {10.8040, 106.6660}, OrderHistoryLocator.TECHNICIAN);

        presence.setLocationAt(LocalDateTime.now().minusMinutes(30));
        assertAt(locator.locate(ORDER_ID), new double[] {10.8031, 106.6654}, OrderHistoryLocator.PICKUP_ADDRESS);
    }

    @Test
    void withoutHomePickupTheParcelWaitsInTheSourceLocker() {
        order.setPickupLatitude(null);
        order.setPickupLongitude(null);
        assertAt(locator.locate(ORDER_ID), SOURCE, OrderHistoryLocator.SOURCE_LOCKER);
    }

    @Test
    void loadingAndLaunchingHappenAtTheSourceLocker() {
        order.setDeliveryStage("ACCEPTED");
        mission("AWAITING_LOADING");
        assertAt(locator.locate(ORDER_ID), SOURCE, OrderHistoryLocator.SOURCE_LOCKER);
        mission("LAUNCHING");
        assertAt(locator.locate(ORDER_ID), SOURCE, OrderHistoryLocator.SOURCE_LOCKER);
    }

    @Test
    void demoFlightUsesTheInterpolatedPointAtTheStartOfTheStage() {
        order.setDeliveryStage("EN_ROUTE");
        mission("EN_ROUTE");
        // EN_ROUTE bắt đầu ở 25 % quãng đường tủ gửi → tủ nhận.
        double[] expected = {
            SOURCE[0] + (DESTINATION[0] - SOURCE[0]) * 0.25,
            SOURCE[1] + (DESTINATION[1] - SOURCE[1]) * 0.25
        };
        OrderHistoryLocator.Location location = locator.locate(ORDER_ID);
        assertEquals(OrderHistoryLocator.DRONE_SIMULATED, location.source());
        assertEquals(expected[0], location.latitude(), 1e-4);
        assertEquals(expected[1], location.longitude(), 1e-4);
    }

    @Test
    void realFlightUsesLiveTelemetryAndNeverGuessesWithoutIt() {
        order.setFulfillmentMode("STANDARD");
        order.setDeliveryStage("EN_ROUTE");
        mission("EN_ROUTE");
        assertNull(locator.locate(ORDER_ID));

        DroneTelemetryFrame frame = mock(DroneTelemetryFrame.class);
        when(frame.hasPosition()).thenReturn(true);
        when(frame.lat()).thenReturn(10.7755);
        when(frame.lng()).thenReturn(106.6800);
        when(telemetry.isLive("LR-DRN-07")).thenReturn(true);
        when(telemetry.latest("LR-DRN-07")).thenReturn(Optional.of(frame));
        assertAt(locator.locate(ORDER_ID), new double[] {10.7755, 106.6800}, OrderHistoryLocator.DRONE);
    }

    @Test
    void arrivedDepositedAndCompletedAreAtTheDestinationLocker() {
        order.setDeliveryStage("ARRIVED");
        mission("ARRIVED");
        assertAt(locator.locate(ORDER_ID), DESTINATION, OrderHistoryLocator.DESTINATION_LOCKER);
        order.setDeliveryStage("READY_FOR_PICKUP");
        mission("DEPOSITED");
        assertAt(locator.locate(ORDER_ID), DESTINATION, OrderHistoryLocator.DESTINATION_LOCKER);
    }

    @Test
    void fillKeepsCoordinatesTheWriterAlreadySetAndSwallowsLookupErrors() {
        OrderStatusHistory preset = new OrderStatusHistory();
        preset.setOrderId(ORDER_ID);
        preset.setLatitude(1.0);
        preset.setLongitude(2.0);
        preset.setLocationSource("TECHNICIAN");
        locator.fill(preset);
        assertEquals(1.0, preset.getLatitude());
        assertEquals("TECHNICIAN", preset.getLocationSource());

        when(orders.findById(any())).thenThrow(new IllegalStateException("db down"));
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(ORDER_ID);
        locator.fill(history);
        assertNull(history.getLatitude());
        assertNull(history.getLocationSource());
    }
}
