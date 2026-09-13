package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusUpdateRequest;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DroneDeliverySimulatorTest {

    @Mock
    private DroneMissionRepository missionRepository;
    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private LockerDroneClient lockerDroneClient;

    @Test
    void advancesLaunchingDemoMissionToDepartedAfterConfiguredDelay() {
        DroneDeliverySimulator simulator =
                new DroneDeliverySimulator(
                        missionRepository, orderRepository, notificationClient, lockerDroneClient);
        ReflectionTestUtils.setField(simulator, "stageDelayMs", 5_000L);
        DroneMission mission = mission(301L, "LAUNCHING", LocalDateTime.now().minusSeconds(6));
        LockerOrder order = order("LAUNCHING");
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(mission));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        simulator.advanceEligibleMissions(LocalDateTime.now());

        assertEquals("DEPARTED", mission.getStatus());
        assertEquals("DEPARTED", order.getDeliveryStage());
        verify(missionRepository).save(mission);
        verify(orderRepository).save(order);
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void arrivalCompletesDemoDepositAndMakesOrderReadyForPaidPickup() {
        DroneDeliverySimulator simulator =
                new DroneDeliverySimulator(
                        missionRepository, orderRepository, notificationClient, lockerDroneClient);
        ReflectionTestUtils.setField(simulator, "stageDelayMs", 5_000L);
        DroneMission mission = mission(301L, "ARRIVED", LocalDateTime.now().minusSeconds(6));
        LockerOrder order = order("ARRIVED");
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(mission));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        simulator.advanceEligibleMissions(LocalDateTime.now());

        assertEquals("DEPOSITED", mission.getStatus());
        assertEquals("READY_FOR_PICKUP", order.getDeliveryStage());
        assertEquals("STORING", order.getStatus());
        assertNotNull(order.getPinCode());
        assertNotNull(order.getPickupDeadline());
        verify(lockerDroneClient)
                .updateDroneStatus(9L, new DroneStatusUpdateRequest("IDLE", null));
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void arrivalRemainsReadyForPickupWhenFleetStatusSyncFails() {
        DroneDeliverySimulator simulator =
                new DroneDeliverySimulator(
                        missionRepository, orderRepository, notificationClient, lockerDroneClient);
        ReflectionTestUtils.setField(simulator, "stageDelayMs", 5_000L);
        DroneMission mission = mission(301L, "ARRIVED", LocalDateTime.now().minusSeconds(6));
        LockerOrder order = order("ARRIVED");
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(mission));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("locker-service unavailable"))
                .when(lockerDroneClient)
                .updateDroneStatus(9L, new DroneStatusUpdateRequest("IDLE", null));

        simulator.advanceEligibleMissions(LocalDateTime.now());

        assertEquals("DEPOSITED", mission.getStatus());
        assertEquals("READY_FOR_PICKUP", order.getDeliveryStage());
        verify(missionRepository).save(mission);
        verify(orderRepository).save(order);
    }

    private DroneMission mission(Long id, String status, LocalDateTime updatedAt) {
        DroneMission mission = new DroneMission();
        mission.setId(id);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setDroneCode("DRONE-09");
        mission.setStatus(status);
        mission.setUpdatedAt(updatedAt);
        return mission;
    }

    private LockerOrder order(String stage) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setFulfillmentMode("DEMO");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage(stage);
        order.setPaymentStatus("UNPAID");
        return order;
    }
}
