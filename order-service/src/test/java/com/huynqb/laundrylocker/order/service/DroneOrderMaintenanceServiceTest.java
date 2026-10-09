package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.settings.TestOrderRules;

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
    void acceptCreatesAwaitingLoadingMissionWhenPreflightPasses() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "AWAITING_DISPATCH");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.empty());
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));
        // Tủ nguồn (nơi drone đỗ) cũng phải ACTIVE và có bãi đáp sẵn sàng.
        when(lockerDroneClient.getLockerLayout(3L))
                .thenReturn(ApiResponse.ok(new LockerLayoutDto(3L, "CAB-03", "Locker 3", "ACTIVE", true, "OK")));
        when(lockerDroneClient.getLockerLayout(5L))
                .thenReturn(ApiResponse.ok(new LockerLayoutDto(5L, "CAB-05", "Locker 5", "ACTIVE", true, "OK")));
        when(lockerDroneClient.transitionDroneStatus(
                        9L, new DroneStatusTransitionRequest("IDLE", "RESERVED", "Reserved for order ORD-21")))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "RESERVED", 87, true)));
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
        assertEquals("AWAITING_LOADING", response.missionStatus());
        assertEquals("ACCEPTED", response.deliveryStage());
        assertEquals("DRONE-09", response.droneCode());
        assertEquals(99L, response.assignedByUserId());
        assertEquals(1200, response.expectedWeightGrams());
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
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setSourceLockerId(3L);
        mission.setDestinationLockerId(5L);
        mission.setStatus("AWAITING_LOADING");
        mission.setLastAcceptIdempotencyKey("accept-1");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));

        DroneMissionResponse response =
                service.accept(21L, 99L, "accept-1", new AcceptDroneOrderRequest(9L));

        assertEquals(301L, response.missionId());
        assertEquals("AWAITING_LOADING", response.missionStatus());
        verify(missionRepository, never()).save(any(DroneMission.class));
        verify(orderRepository, never()).save(any(LockerOrder.class));
    }

    @Test
    void confirmLoadingRecordsAuditAndMakesMissionReadyToLaunch() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        order.setSourceBoxId(8001L);
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setStatus("AWAITING_LOADING");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "RESERVED", 87, true)));
        when(missionRepository.save(any(DroneMission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DroneMissionResponse response = service.confirmLoading(
                21L,
                99L,
                "load-1",
                new ConfirmDroneLoadingRequest(1200, " SEAL-21 ", true, true, true, "  intact  "));

        assertEquals("READY_TO_LAUNCH", response.missionStatus());
        assertEquals(1200, response.payloadWeightGrams());
        assertEquals("SEAL-21", response.sealCode());
        assertEquals(99L, response.loadedByUserId());
        assertNotNull(response.loadedAt());
        assertEquals("intact", mission.getLoadingNote());
        assertEquals("load-1", mission.getLastLoadingIdempotencyKey());
        assertTrue(mission.isParcelMatched());
        assertTrue(mission.isPayloadSecured());
        assertTrue(mission.isCompartmentLocked());
        verify(missionRepository).save(mission);
        // Kiện đã lên drone: ô gửi ở tủ nguồn được trả lại, ô nhận ở tủ đích vẫn giữ.
        verify(lockerClient).releaseBox(8001L);
        verify(lockerClient, never()).releaseBox(9001L);
        assertNull(order.getSourceBoxId());
        verify(orderRepository).save(order);
    }

    @Test
    void confirmLoadingRejectsPayloadAboveConfiguredLimit() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setStatus("AWAITING_LOADING");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.confirmLoading(
                        21L,
                        99L,
                        "load-1",
                        new ConfirmDroneLoadingRequest(5001, "SEAL-21", true, true, true, null)));

        assertEquals("DRONE_PAYLOAD_TOO_HEAVY", error.getCode());
        verify(lockerDroneClient, never()).getDroneUnit(any());
        verify(missionRepository, never()).save(any());
    }

    @Test
    void confirmLoadingRejectsAnotherDroneTechnician() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setStatus("AWAITING_LOADING");
        mission.setAssignedByUserId(88L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.confirmLoading(
                        21L,
                        99L,
                        "load-1",
                        new ConfirmDroneLoadingRequest(1200, "SEAL-21", true, true, true, null)));

        assertEquals("DRONE_MISSION_NOT_ASSIGNED_TO_USER", error.getCode());
        verify(lockerDroneClient, never()).getDroneUnit(any());
        verify(missionRepository, never()).save(any());
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
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setSourceLockerId(3L);
        mission.setDestinationLockerId(5L);
        mission.setStatus("READY_TO_LAUNCH");
        mission.setAssignedByUserId(99L);
        markLoadingConfirmed(mission);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(DroneMission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "RESERVED", 87, true)));
        when(lockerDroneClient.getLockerLayout(5L))
                .thenReturn(ApiResponse.ok(new LockerLayoutDto(5L, "CAB-05", "Locker 5", "ACTIVE", true, "OK")));
        when(lockerDroneClient.transitionDroneStatus(
                        9L, new DroneStatusTransitionRequest("RESERVED", "IN_FLIGHT", null)))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IN_FLIGHT", 87, true)));

        DroneMissionResponse response = service.launch(21L, 99L, "launch-1");

        assertSame(order.getId(), response.orderId());
        assertEquals("LAUNCHING", response.missionStatus());
        assertEquals("LAUNCHING", response.deliveryStage());
        verify(lockerDroneClient).transitionDroneStatus(
                9L, new DroneStatusTransitionRequest("RESERVED", "IN_FLIGHT", null));
        verify(missionRepository).save(mission);
        verify(orderRepository).save(order);
    }

    @Test
    void launchRejectsReadyMissionWithoutLoadingAudit() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setStatus("READY_TO_LAUNCH");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.launch(21L, 99L, "launch-1"));

        assertEquals("DRONE_LOADING_NOT_CONFIRMED", error.getCode());
        verify(lockerDroneClient, never()).getDroneUnit(any());
        verify(missionRepository, never()).save(any());
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
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        order.setStatus("AWAITING_DISPATCH");
        order.setSourceBoxId(8001L);
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setSourceLockerId(3L);
        mission.setDestinationLockerId(5L);
        mission.setStatus("READY_TO_LAUNCH");
        mission.setAssignedByUserId(99L);
        // Đã nạp lên drone: kiện không còn trong ô gửi nên ô gửi (nếu còn giữ) được nhả.
        mission.setLoadedAt(java.time.LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(
                        ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "RESERVED", 87, true)),
                        ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));
        when(lockerDroneClient.transitionDroneStatus(
                        9L, new DroneStatusTransitionRequest("RESERVED", "IDLE", null)))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));

        DroneMissionResponse response =
                service.cancel(21L, 99L, new CancelDroneOrderRequest(5, "  Gio giat manh  "));

        assertEquals("CANCELED", response.missionStatus());
        assertEquals("CANCELED", response.deliveryStage());
        assertEquals(5, order.getCancelReason());
        assertEquals("Gio giat manh", order.getStaffNote());
        assertEquals("CANCELED", order.getStatus());
        verify(lockerClient).releaseBox(9001L);
        verify(lockerClient).releaseBox(8001L);
        verify(lockerDroneClient).transitionDroneStatus(
                9L, new DroneStatusTransitionRequest("RESERVED", "IDLE", null));
        // Hồ sơ nhiệm vụ được giữ lại để đối chứng, không xoá.
        verify(missionRepository, never()).delete(any());
        verify(missionRepository).save(mission);
        assertEquals("CANCELED", mission.getStatus());
        assertEquals(5, mission.getEndReason());
        assertEquals("Gio giat manh", mission.getEndNote());
        assertEquals(99L, mission.getEndedByUserId());
        assertNotNull(mission.getEndedAt());
        assertNull(mission.getFailedStage());
        verify(historyRepository).save(any());
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void flightFailureClosesOrderReleasesDestinationBoxAndGroundsDrone() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = droneOrder(21L, "EN_ROUTE");
        order.setReceiverUserId(55L);
        DroneMission mission = inFlightMission("EN_ROUTE");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        DroneMissionResponse response =
                service.reportFlightFailure(21L, 99L, false, new CancelDroneOrderRequest(2, " Mat tin hieu "));

        assertEquals("FAILED", response.missionStatus());
        assertEquals("FAILED", response.deliveryStage());
        // CANCELED là điều kiện để DroneRefundService tạo yêu cầu hoàn tiền.
        assertEquals("CANCELED", order.getStatus());
        assertEquals("FAILED", order.getDeliveryStage());
        assertEquals(2, order.getCancelReason());
        assertEquals("Mat tin hieu", order.getStaffNote());
        assertEquals("FAILED", mission.getStatus());
        assertEquals("EN_ROUTE", mission.getFailedStage());
        assertEquals(2, mission.getEndReason());
        assertNotNull(mission.getEndedAt());
        verify(lockerClient).releaseBox(9001L);
        verify(lockerDroneClient).transitionDroneStatus(
                9L,
                new DroneStatusTransitionRequest(
                        "IN_FLIGHT", "FAULT", "Flight of order ORD-21 failed: Drone fault · Mat tin hieu"));
        verify(missionRepository, never()).delete(any());
        // Báo cả người gửi lẫn người nhận có tài khoản.
        verify(notificationClient, times(2)).requestNotification(any());
    }

    @Test
    void flightFailureStillClosesOrderWhenFleetAndLockerSyncFail() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = droneOrder(21L, "ARRIVED");
        DroneMission mission = inFlightMission("ARRIVED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.transitionDroneStatus(any(), any()))
                .thenThrow(new IllegalStateException("drone already FAULT"));
        doThrow(new IllegalStateException("locker-service down")).when(lockerClient).releaseBox(9001L);

        service.reportFlightFailure(21L, 99L, false, new CancelDroneOrderRequest(3, null));

        assertEquals("CANCELED", order.getStatus());
        assertEquals("FAILED", mission.getStatus());
    }

    @Test
    void flightFailureIsRejectedBeforeLaunchAndForOtherTechnicians() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = inFlightMission("READY_TO_LAUNCH");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException notInFlight = assertThrows(
                BusinessException.class,
                () -> service.reportFlightFailure(21L, 99L, false, new CancelDroneOrderRequest(2, null)));
        assertEquals("DRONE_MISSION_STATUS_INVALID", notInFlight.getCode());

        mission.setStatus("EN_ROUTE");
        BusinessException notAssigned = assertThrows(
                BusinessException.class,
                () -> service.reportFlightFailure(21L, 7L, false, new CancelDroneOrderRequest(2, null)));
        assertEquals("DRONE_MISSION_NOT_ASSIGNED_TO_USER", notAssigned.getCode());

        BusinessException noteRequired = assertThrows(
                BusinessException.class,
                () -> service.reportFlightFailure(21L, 99L, false, new CancelDroneOrderRequest(5, " ")));
        assertEquals("DRONE_CANCEL_NOTE_REQUIRED", noteRequired.getCode());

        assertEquals("AWAITING_DISPATCH", order.getStatus());
        verify(lockerClient, never()).releaseBox(any());
        verify(missionRepository, never()).save(any());
    }

    @Test
    void adminMayReportFlightFailureOfAnyMission() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = droneOrder(21L, "DEPARTED");
        DroneMission mission = inFlightMission("DEPARTED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        service.reportFlightFailure(21L, 1L, true, new CancelDroneOrderRequest(1, null));

        assertEquals("FAILED", mission.getStatus());
        assertEquals(1L, mission.getEndedByUserId());
    }

    @Test
    void acceptIsRejectedUntilTheSenderConfirmsTheParcelIsInTheSourceCell() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = droneOrder(21L, "AWAITING_DISPATCH");
        order.setParcelDroppedAt(null);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.accept(21L, 99L, "accept-1", new AcceptDroneOrderRequest(9L)));

        assertEquals("DRONE_PARCEL_NOT_DROPPED", error.getCode());
        verify(lockerDroneClient, never()).transitionDroneStatus(any(), any());
    }

    @Test
    void acceptIsRejectedWhenTheDroneBatteryIsUnknown() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = droneOrder(21L, "AWAITING_DISPATCH");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.empty());
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", null, true)));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.accept(21L, 99L, "accept-1", new AcceptDroneOrderRequest(9L)));

        assertEquals("DRONE_BATTERY_UNKNOWN", error.getCode());
        verify(lockerDroneClient, never()).transitionDroneStatus(any(), any());
    }

    @Test
    void acceptAndLaunchAreRejectedWhileFlightsAreSuspended() {
        DroneOrderMaintenanceService service = new DroneOrderMaintenanceService(
                orderRepository,
                missionRepository,
                lockerDroneClient,
                lockerClient,
                historyRepository,
                notificationClient,
                TestOrderRules.of(java.util.Map.of("app.order.drone-flights-suspended", true)));
        LockerOrder order = droneOrder(21L, "AWAITING_DISPATCH");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        BusinessException accept = assertThrows(
                BusinessException.class,
                () -> service.accept(21L, 99L, "accept-1", new AcceptDroneOrderRequest(9L)));
        assertEquals("DRONE_FLIGHTS_SUSPENDED", accept.getCode());

        order.setDeliveryStage("ACCEPTED");
        DroneMission mission = inFlightMission("READY_TO_LAUNCH");
        markLoadingConfirmed(mission);
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException launch =
                assertThrows(BusinessException.class, () -> service.launch(21L, 99L, "launch-1"));
        assertEquals("DRONE_FLIGHTS_SUSPENDED", launch.getCode());
        verify(lockerDroneClient, never()).transitionDroneStatus(any(), any());
    }

    @Test
    void cancelBeforeLoadingKeepsTheSourceCellThatStillHoldsTheParcel() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        order.setSourceBoxId(8001L);
        DroneMission mission = inFlightMission("AWAITING_LOADING");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));

        service.cancel(21L, 99L, new CancelDroneOrderRequest(1, null));

        assertEquals("CANCELED", order.getStatus());
        // Ô nhận được nhả; ô gửi còn kiện nên giữ lại tới khi trả kiện.
        verify(lockerClient).releaseBox(9001L);
        verify(lockerClient, never()).releaseBox(8001L);
        assertEquals(8001L, order.getSourceBoxId());
        assertTrue(DroneParcelCustody.returnPending(order, mission));
        assertEquals(DroneParcelCustody.SOURCE_BOX, DroneParcelCustody.heldAt(order, mission));
    }

    @Test
    void customerDecliningTheWeightSurchargeCancelsAndFreesTheDrone() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = surchargeOrder();
        DroneMission mission = surchargeMission(java.time.LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "RESERVED", 87, true)));
        when(lockerDroneClient.transitionDroneStatus(
                        9L, new DroneStatusTransitionRequest("RESERVED", "IDLE", null)))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));

        assertTrue(service.cancelUnpaidSurcharge(21L, 44L, null));

        assertEquals("CANCELED", order.getStatus());
        assertEquals("CANCELED", order.getDeliveryStage());
        assertEquals("CANCELED", mission.getStatus());
        verify(lockerDroneClient).transitionDroneStatus(
                9L, new DroneStatusTransitionRequest("RESERVED", "IDLE", null));
        verify(lockerClient).releaseBox(9001L);
        // Kiện đã nạp lên drone ⇒ đội bay giữ, chờ trả.
        assertEquals(DroneParcelCustody.FLIGHT_TEAM, DroneParcelCustody.heldAt(order, mission));
        assertTrue(DroneParcelCustody.returnPending(order, mission));
        // Báo điều phối viên đã nhận nhiệm vụ để dỡ kiện.
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void decliningIsOnlyForTheOwnerOfAnOrderThatOwesASurcharge() {
        DroneOrderMaintenanceService service = newService();
        LockerOrder order = surchargeOrder();
        DroneMission mission = surchargeMission(java.time.LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException notOwner = assertThrows(
                BusinessException.class, () -> service.cancelUnpaidSurcharge(21L, 7L, null));
        assertEquals("ORDER_FORBIDDEN", notOwner.getCode());

        order.setPaymentStatus("PAID");
        BusinessException notOwed = assertThrows(
                BusinessException.class, () -> service.cancelUnpaidSurcharge(21L, 44L, null));
        assertEquals("DRONE_SURCHARGE_NOT_OWED", notOwed.getCode());
        assertEquals("AWAITING_DISPATCH", order.getStatus());
    }

    @Test
    void surchargeTimeoutOnlyCancelsOncePastTheDeadline() {
        DroneOrderMaintenanceService service = newService();
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 10, 9, 12, 0);
        LockerOrder order = surchargeOrder();
        DroneMission mission = surchargeMission(now.minusMinutes(30));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        assertFalse(service.cancelUnpaidSurcharge(21L, null, now.minusMinutes(60)));
        assertEquals("AWAITING_DISPATCH", order.getStatus());

        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "IDLE", 87, true)));
        assertTrue(service.cancelUnpaidSurcharge(21L, null, now.minusMinutes(20)));
        assertEquals("CANCELED", order.getStatus());
        // Báo cả điều phối viên lẫn khách.
        verify(notificationClient, times(2)).requestNotification(any());
    }

    private LockerOrder surchargeOrder() {
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        order.setPaymentStatus("UNPAID");
        order.setPaidAmount(new java.math.BigDecimal("15000"));
        return order;
    }

    private DroneMission surchargeMission(java.time.LocalDateTime loadedAt) {
        DroneMission mission = inFlightMission("READY_TO_LAUNCH");
        markLoadingConfirmed(mission);
        mission.setLoadedAt(loadedAt);
        mission.setWeightSurcharge(new java.math.BigDecimal("3000"));
        return mission;
    }

    private DroneOrderMaintenanceService newService() {
        return new DroneOrderMaintenanceService(
                orderRepository,
                missionRepository,
                lockerDroneClient,
                lockerClient,
                historyRepository,
                notificationClient,
                TestOrderRules.defaults());
    }

    private DroneMission inFlightMission(String status) {
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setSourceLockerId(3L);
        mission.setDestinationLockerId(5L);
        mission.setStatus(status);
        mission.setAssignedByUserId(99L);
        return mission;
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
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setStatus("READY_TO_LAUNCH");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () -> service.cancel(21L, 99L, new CancelDroneOrderRequest(5, "   ")));

        assertEquals("DRONE_CANCEL_NOTE_REQUIRED", error.getCode());
        verify(lockerClient, never()).releaseBox(any());
        verify(missionRepository, never()).delete(any());
    }

    @Test
    void launchRejectsMissionWhenReservationWasLost() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setStatus("READY_TO_LAUNCH");
        mission.setAssignedByUserId(99L);
        markLoadingConfirmed(mission);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "FAULT", 87, true)));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.launch(21L, 99L, "launch-1"));

        assertEquals("DRONE_RESERVATION_LOST", error.getCode());
        verify(lockerDroneClient, never()).transitionDroneStatus(any(), any());
        verify(orderRepository, never()).save(any());
    }

    /// Đơn khai 500 g đã trả 15.000 đ, chờ nạp hàng.
    private DroneMission awaitingLoading(LockerOrder order) {
        order.setParcelWeightGrams(500);
        order.setTotalPrice(java.math.BigDecimal.valueOf(15000));
        order.setOriginalPrice(java.math.BigDecimal.valueOf(15000));
        order.setPaidAmount(java.math.BigDecimal.valueOf(15000));
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(order.getId());
        mission.setDroneUnitId(9L);
        mission.setStatus("AWAITING_LOADING");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(order.getId())).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getDroneUnit(9L))
                .thenReturn(ApiResponse.ok(new DroneUnitDto(9L, 3L, "DRONE-09", "RESERVED", 87, true)));
        when(missionRepository.save(any(DroneMission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return mission;
    }

    @Test
    void confirmLoadingGeneratesSealCodeWhenNoneIsSent() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        awaitingLoading(order);

        DroneMissionResponse response = service.confirmLoading(
                21L, 99L, "load-1", new ConfirmDroneLoadingRequest(500, "  ", true, true, true, null));

        assertTrue(response.sealCode().matches("NP-\\d{6}-[A-Z2-9]{6}"), response.sealCode());
        assertNull(response.weightSurcharge());
        assertEquals("PAID", order.getPaymentStatus());
    }

    @Test
    void confirmLoadingChargesTheDifferenceWhenParcelIsHeavierThanDeclared() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        DroneMission mission = awaitingLoading(order);

        // 1200 g = 500 g cơ bản + 3 nấc 250 g (nấc cuối chưa trọn) × 3.000 đ ⇒ 24.000 đ.
        DroneMissionResponse response = service.confirmLoading(
                21L, 99L, "load-1", new ConfirmDroneLoadingRequest(1200, null, true, true, true, null));

        assertEquals(0, java.math.BigDecimal.valueOf(9000).compareTo(response.weightSurcharge()));
        assertEquals(0, java.math.BigDecimal.valueOf(24000).compareTo(order.getTotalPrice()));
        assertEquals(0, java.math.BigDecimal.valueOf(15000).compareTo(order.getPaidAmount()));
        assertEquals("UNPAID", order.getPaymentStatus());
        assertEquals("UNPAID", response.paymentStatus());
        assertEquals("READY_TO_LAUNCH", mission.getStatus());
        // Báo khách khoản cần trả thêm.
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void confirmLoadingDoesNotChargeWithinScaleToleranceOrWhenLighter() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        awaitingLoading(order);

        DroneMissionResponse response = service.confirmLoading(
                21L, 99L, "load-1", new ConfirmDroneLoadingRequest(550, null, true, true, true, null));

        assertNull(response.weightSurcharge());
        assertEquals(0, java.math.BigDecimal.valueOf(15000).compareTo(order.getTotalPrice()));
        assertEquals("PAID", order.getPaymentStatus());
        verify(notificationClient, never()).requestNotification(any());
    }

    @Test
    void launchRejectsMissionWhileWeightSurchargeIsUnpaid() {
        DroneOrderMaintenanceService service =
                new DroneOrderMaintenanceService(
                        orderRepository,
                        missionRepository,
                        lockerDroneClient,
                        lockerClient,
                        historyRepository,
                        notificationClient,
                        TestOrderRules.defaults());
        LockerOrder order = droneOrder(21L, "ACCEPTED");
        order.setPaymentStatus("UNPAID");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setDestinationLockerId(5L);
        mission.setStatus("READY_TO_LAUNCH");
        mission.setAssignedByUserId(99L);
        markLoadingConfirmed(mission);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.launch(21L, 99L, "launch-1"));

        assertEquals("DRONE_SURCHARGE_UNPAID", error.getCode());
        verify(lockerDroneClient, never()).transitionDroneStatus(any(), any());
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
        order.setParcelWeightGrams(1200);
        order.setParcelDroppedAt(java.time.LocalDateTime.now());
        return order;
    }

    private void markLoadingConfirmed(DroneMission mission) {
        mission.setPayloadWeightGrams(1200);
        mission.setSealCode("SEAL-21");
        mission.setParcelMatched(true);
        mission.setPayloadSecured(true);
        mission.setCompartmentLocked(true);
        mission.setLoadedByUserId(99L);
        mission.setLoadedAt(java.time.LocalDateTime.now());
    }
}
