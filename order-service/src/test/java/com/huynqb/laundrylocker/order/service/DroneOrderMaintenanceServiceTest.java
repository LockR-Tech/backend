package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DroneOrderMaintenanceServiceTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private DroneMissionRepository missionRepository;
    @Mock
    private LockerDroneClient lockerDroneClient;
    @Mock
    private LockerClient lockerClient;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private NotificationClient notificationClient;

    @Test
    void acceptCreatesReadyToLaunchMissionWhenPreflightPasses() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient);
        LockerOrder order = droneOrder(21L, "AWAITING_DISPATCH");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.empty());
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));
        when(lockerDroneClient.getLockerLayout(5L))
                .thenReturn(ApiResponse.ok(new LockerLayoutDto(5L, "CAB-05", "Locker 5", "ACTIVE", true, "OK")));
        when(missionRepository.save(any(DroneMission.class)))
                .thenAnswer(
                        invocation -> {
                            DroneMission mission = invocation.getArgument(0);
                            mission.setId(301L);
                            return mission;
                        });
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DroneMissionResponse response =
                service.accept(21L, 99L, "accept-1", new AcceptDroneOrderRequest(9L));

        assertEquals(21L, response.orderId());
        assertEquals(301L, response.missionId());
        assertEquals("READY_TO_LAUNCH", response.missionStatus());
        assertEquals("ACCEPTED", response.deliveryStage());
        assertEquals("DRONE-09", response.droneCode());
        verify(missionRepository).save(any(DroneMission.class));
        verify(orderRepository).save(order);
    }

    @Test
    void acceptReturnsExistingMissionForSameIdempotencyKey() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient);
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setSourceLockerId(3L);
        mission.setDestinationLockerId(5L);
        mission.setStatus("READY_TO_LAUNCH");
        mission.setLastAcceptIdempotencyKey("accept-1");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));

        DroneMissionResponse response =
                service.accept(21L, 99L, "accept-1", new AcceptDroneOrderRequest(9L));

        assertEquals(301L, response.missionId());
        assertEquals("READY_TO_LAUNCH", response.missionStatus());
        verify(missionRepository, never()).save(any(DroneMission.class));
        verify(orderRepository, never()).save(any(LockerOrder.class));
    }

    @Test
    void launchMarksMissionLaunchingAndRequestsDroneStateChange() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient);
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setSourceLockerId(3L);
        mission.setDestinationLockerId(5L);
        mission.setStatus("READY_TO_LAUNCH");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(DroneMission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerDroneClient.updateDroneStatus(9L, new DroneStatusUpdateRequest("IN_FLIGHT", null)))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IN_FLIGHT", 87, true)));

        DroneMissionResponse response = service.launch(21L, 99L, "launch-1");

        assertSame(order.getId(), response.orderId());
        assertEquals("LAUNCHING", response.missionStatus());
        assertEquals("LAUNCHING", response.deliveryStage());
        verify(lockerDroneClient).updateDroneStatus(9L, new DroneStatusUpdateRequest("IN_FLIGHT", null));
        verify(missionRepository).save(mission);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelStoresReasonAndNoteThenReleasesReservedBox() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient);
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        order.setStatus("AWAITING_DISPATCH");
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setSourceLockerId(3L);
        mission.setDestinationLockerId(5L);
        mission.setStatus("READY_TO_LAUNCH");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));

        DroneMissionResponse response =
                service.cancel(21L, 99L, new CancelDroneOrderRequest(5, "  Gio giat manh  "));

        assertEquals("CANCELED", response.missionStatus());
        assertEquals("CANCELED", response.deliveryStage());
        assertEquals(5, order.getCancelReason());
        assertEquals("Gio giat manh", order.getStaffNote());
        assertEquals("CANCELED", order.getStatus());
        verify(lockerClient).releaseBox(9001L);
        verify(missionRepository).delete(mission);
        verify(historyRepository).save(any());
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void cancelRequiresNoteWhenReasonIsOther() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient);
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setStatus("READY_TO_LAUNCH");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () -> service.cancel(21L, 99L, new CancelDroneOrderRequest(5, "   ")));

        assertEquals("DRONE_CANCEL_NOTE_REQUIRED", error.getCode());
        verify(lockerClient, never()).releaseBox(any());
        verify(missionRepository, never()).delete(any());
    }

    private LockerOrder droneOrder(Long orderId, String deliveryStage) {
        LockerOrder order = new LockerOrder();
        order.setId(orderId);
        order.setOrderCode("ORD-" + orderId);
        order.setUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setPaymentStatus("PAID");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage(deliveryStage);
        order.setDestinationLockerId(5L);
        order.setReservedBoxId(9001L);
        return order;
    }
}
