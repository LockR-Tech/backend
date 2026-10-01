package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.settings.TestOrderRules;

import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
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
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private com.huynqb.laundrylocker.order.client.LockerClient lockerClient;
    @Mock
    private DronePositionBroadcaster positionBroadcaster;

    private DroneDeliverySimulator simulator(com.huynqb.laundrylocker.order.settings.OrderRules rules) {
        DroneMissionProgressService progress = new DroneMissionProgressService(
                missionRepository, orderRepository, historyRepository, notificationClient,
                lockerDroneClient, lockerClient, rules);
        return new DroneDeliverySimulator(missionRepository, orderRepository, progress, positionBroadcaster, rules);
    }

    @Test
    void defaultsDemoStagesToThreeSecondsForFastEndToEndTesting() {
        assertEquals(3000, TestOrderRules.defaults().droneDemoStageDelayMs());
    }

    @Test
    void advancesLaunchingDemoMissionToDepartedAfterConfiguredDelay() {
        DroneDeliverySimulator simulator =
                simulator(TestOrderRules.of(java.util.Map.of("app.drone.demo.stage-delay-ms", 5000)));
        DroneMission mission = mission(301L, "LAUNCHING", LocalDateTime.now().minusSeconds(6));
        LockerOrder order = order("LAUNCHING");
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(mission));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        simulator.advanceEligibleMissions(LocalDateTime.now());

        assertEquals("DEPARTED", mission.getStatus());
        assertEquals("DEPARTED", order.getDeliveryStage());
        verify(missionRepository).save(mission);
        verify(orderRepository).save(order);
        verify(notificationClient).notifyDeliveryStatus(any());
        verify(historyRepository).save(any());
    }

    @Test
    void arrivalCompletesDemoDepositAndMakesOrderReadyForPaidPickup() {
        DroneDeliverySimulator simulator =
                simulator(TestOrderRules.of(java.util.Map.of("app.drone.demo.stage-delay-ms", 5000)));
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
        // Hàng đã nằm trong ô: ô nhận chuyển từ giữ chỗ sang có hàng.
        verify(lockerClient).occupyBox(9001L);
        verify(lockerDroneClient)
                .transitionDroneStatus(9L, new DroneStatusTransitionRequest("IN_FLIGHT", "IDLE", null));
        verify(notificationClient).notifyDeliveryStatus(any());
        verify(historyRepository).save(any());
    }

    @Test
    void arrivalRemainsReadyForPickupWhenFleetStatusSyncFails() {
        DroneDeliverySimulator simulator =
                simulator(TestOrderRules.of(java.util.Map.of("app.drone.demo.stage-delay-ms", 5000)));
        DroneMission mission = mission(301L, "ARRIVED", LocalDateTime.now().minusSeconds(6));
        LockerOrder order = order("ARRIVED");
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(mission));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("locker-service unavailable"))
                .when(lockerDroneClient)
                .transitionDroneStatus(9L, new DroneStatusTransitionRequest("IN_FLIGHT", "IDLE", null));

        simulator.advanceEligibleMissions(LocalDateTime.now());

        assertEquals("DEPOSITED", mission.getStatus());
        assertEquals("READY_FOR_PICKUP", order.getDeliveryStage());
        verify(missionRepository).save(mission);
        verify(orderRepository).save(order);
    }

    @Test
    void broadcastsPositionWhileWaitingAndLeavesStandardMissionsAlone() {
        DroneDeliverySimulator simulator =
                simulator(TestOrderRules.of(java.util.Map.of("app.drone.demo.stage-delay-ms", 5000)));
        DroneMission waiting = mission(301L, "EN_ROUTE", LocalDateTime.now().minusSeconds(1));
        LockerOrder demo = order("EN_ROUTE");
        DroneMission real = mission(302L, "EN_ROUTE", LocalDateTime.now().minusSeconds(60));
        real.setOrderId(22L);
        LockerOrder standard = order("EN_ROUTE");
        standard.setId(22L);
        standard.setFulfillmentMode("STANDARD");
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(waiting, real));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(demo));
        when(orderRepository.findById(22L)).thenReturn(Optional.of(standard));

        simulator.advanceEligibleMissions(LocalDateTime.now());

        // Chưa đủ thời gian một chặng: chỉ phát vị trí, không đổi chặng.
        assertEquals("EN_ROUTE", waiting.getStatus());
        verify(positionBroadcaster).broadcast(eq(demo), eq(waiting), any(), eq(5000L));
        // Đơn STANDARD chờ điều phối viên/telemetry, bộ giả lập không đụng tới.
        assertEquals("EN_ROUTE", real.getStatus());
        verify(positionBroadcaster, never()).broadcast(eq(standard), any(), any(), anyLong());
        verify(missionRepository, never()).save(any());
    }

    @Test
    void sendsPickupCodeToReceiverWhoIsNotTheSender() {
        DroneDeliverySimulator simulator =
                simulator(TestOrderRules.of(java.util.Map.of("app.drone.demo.stage-delay-ms", 5000)));
        DroneMission mission = mission(301L, "ARRIVED", LocalDateTime.now().minusSeconds(6));
        LockerOrder order = order("ARRIVED");
        order.setReceiverPhone("0909000111");
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(mission));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        simulator.advanceEligibleMissions(LocalDateTime.now());

        // Người nhận chưa có tài khoản ⇒ mã mở ô đi qua SMS tới số ghi trên đơn.
        verify(notificationClient).notifyGuest(argThat(request ->
                "0909000111".equals(request.phone()) && request.smsMessage().contains(order.getPinCode())));
        // Người gửi vẫn nhận thông báo chặng.
        verify(notificationClient).notifyDeliveryStatus(argThat(request -> request.receiverUserId().equals(44L)));
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
        order.setReservedBoxId(9001L);
        order.setPaymentStatus("UNPAID");
        return order;
    }
}
