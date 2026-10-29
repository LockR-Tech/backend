package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroneMissionReconcilerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Mock private DroneMissionRepository missionRepository;
    @Mock private LockerOrderRepository orderRepository;
    @Mock private LockerDroneClient lockerDroneClient;
    @Mock private PlatformTransactionManager transactionManager;

    private DroneMissionReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new DroneMissionReconciler(missionRepository, orderRepository, lockerDroneClient, transactionManager);
    }

    /// DRONE-05: nhiệm vụ của ORD-DRN-0021 còn LAUNCHING trong khi đơn đã COMPLETED.
    @Test
    void closesLaunchedMissionOfCompletedOrderAndReturnsDroneToIdle() {
        DroneMission mission = mission(1L, 21L, 5L, "LAUNCHING");
        stub(Map.of(21L, order(21L, "COMPLETED")), mission);

        assertEquals(1, reconciler.reconcile(NOW));

        assertEquals("CANCELED", mission.getStatus());
        assertEquals(NOW, mission.getEndedAt());
        assertEquals(new DroneStatusTransitionRequest("IN_FLIGHT", "IDLE", null), released(5L));
    }

    @Test
    void returnsReservedDroneOfMissionThatNeverLaunched() {
        DroneMission mission = mission(1L, 21L, 5L, "AWAITING_LOADING");
        stub(Map.of(21L, order(21L, "CANCELED")), mission);

        assertEquals(1, reconciler.reconcile(NOW));

        assertEquals(new DroneStatusTransitionRequest("RESERVED", "IDLE", null), released(5L));
    }

    @Test
    void launchedMissionOfCanceledOrderSendsDroneToInspection() {
        DroneMission mission = mission(1L, 21L, 5L, "EN_ROUTE");
        stub(Map.of(21L, order(21L, "CANCELED")), mission);

        reconciler.reconcile(NOW);

        DroneStatusTransitionRequest request = released(5L);
        assertEquals("IN_FLIGHT", request.expectedStatus());
        assertEquals("FAULT", request.status());
    }

    @Test
    void leavesMissionOfOrderStillBeingDeliveredAlone() {
        DroneMission mission = mission(1L, 21L, 5L, "EN_ROUTE");
        stub(Map.of(21L, order(21L, "AWAITING_DISPATCH")), mission);

        assertEquals(0, reconciler.reconcile(NOW));

        assertEquals("EN_ROUTE", mission.getStatus());
        assertNull(mission.getEndedAt());
        verify(lockerDroneClient, never()).transitionDroneStatus(anyLong(), any());
    }

    @Test
    void keepsDroneThatAlreadyFliesAnotherLiveMission() {
        DroneMission orphan = mission(1L, 21L, 5L, "LAUNCHING");
        DroneMission live = mission(2L, 22L, 5L, "EN_ROUTE");
        stub(Map.of(21L, order(21L, "COMPLETED"), 22L, order(22L, "AWAITING_DISPATCH")), orphan, live);

        assertEquals(1, reconciler.reconcile(NOW));

        assertEquals("CANCELED", orphan.getStatus());
        assertEquals("EN_ROUTE", live.getStatus());
        verify(lockerDroneClient, never()).transitionDroneStatus(anyLong(), any());
    }

    private void stub(Map<Long, LockerOrder> orders, DroneMission... missions) {
        when(missionRepository.findByStatusIn(DroneMissionReconciler.OPEN_MISSION)).thenReturn(List.of(missions));
        for (DroneMission mission : missions) {
            LockerOrder order = orders.get(mission.getOrderId());
            when(orderRepository.findById(mission.getOrderId())).thenReturn(Optional.ofNullable(order));
            lenient().when(orderRepository.findByIdForUpdate(mission.getOrderId())).thenReturn(Optional.ofNullable(order));
            lenient().when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        }
    }

    private DroneStatusTransitionRequest released(Long droneId) {
        ArgumentCaptor<DroneStatusTransitionRequest> captor = ArgumentCaptor.forClass(DroneStatusTransitionRequest.class);
        verify(lockerDroneClient).transitionDroneStatus(eq(droneId), captor.capture());
        return captor.getValue();
    }

    private static DroneMission mission(Long id, Long orderId, Long droneId, String status) {
        DroneMission mission = new DroneMission();
        mission.setId(id);
        mission.setOrderId(orderId);
        mission.setDroneUnitId(droneId);
        mission.setStatus(status);
        return mission;
    }

    private static LockerOrder order(Long id, String status) {
        LockerOrder order = new LockerOrder();
        order.setId(id);
        order.setOrderCode("ORD-" + id);
        order.setStatus(status);
        return order;
    }
}
