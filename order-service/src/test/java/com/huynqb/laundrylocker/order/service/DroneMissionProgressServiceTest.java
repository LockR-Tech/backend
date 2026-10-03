package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroneMissionProgressServiceTest {

    @Mock
    private DroneMissionRepository missionRepository;
    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private LockerDroneClient lockerDroneClient;
    @Mock
    private LockerClient lockerClient;
    @Mock
    private UserClient userClient;

    private DroneMissionProgressService service;

    @BeforeEach
    void setUp() {
        service = new DroneMissionProgressService(
                missionRepository, orderRepository, historyRepository, notificationClient,
                lockerDroneClient, lockerClient, userClient, TestOrderRules.defaults());
    }

    @Test
    void depositEmailsThePickupCodeToTheReceiverAndLogsWhenItWasSent() {
        LockerOrder order = order("STANDARD", "ARRIVED");
        order.setReceiverUserId(55L);
        DroneMission mission = mission("ARRIVED", 99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(userClient.getUser(55L)).thenReturn(ApiResponse.ok(
                new UserSummary(55L, "nhan.hang@gmail.com", "0900000055", "Nguoi Nhan", "ACTIVE")));
        when(notificationClient.notifyGuest(any())).thenReturn(ApiResponse.ok(
                new GuestNotification.Result(false, true, false, true)));

        service.advanceByOperator(21L, 99L);

        ArgumentCaptor<GuestNotification.Request> email = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient).notifyGuest(email.capture());
        assertEquals("nhan.hang@gmail.com", email.getValue().email());
        assertTrue(email.getValue().emailBody().contains(order.getPinCode()));
        verify(notificationClient).requestNotification(any());

        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository, times(2)).save(history.capture());
        OrderStatusHistory codeSent = history.getAllValues().get(1);
        assertEquals("READY_FOR_PICKUP", codeSent.getOldStatus());
        assertEquals("READY_FOR_PICKUP", codeSent.getNewStatus());
        assertEquals(
                "Đã gửi mã nhận hàng cho người nhận qua thông báo trong app, email nh***@gmail.com.",
                codeSent.getNote());
    }

    @Test
    void failedDeliveryOfTheCodeIsLoggedSoTheSenderKnowsToShareIt() {
        LockerOrder order = order("STANDARD", "ARRIVED");
        order.setReceiverUserId(null);
        order.setReceiverPhone("0911222333");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission("ARRIVED", 99L)));
        when(notificationClient.notifyGuest(any())).thenReturn(ApiResponse.ok(
                new GuestNotification.Result(false, false, false, false)));

        service.advanceByOperator(21L, 99L);

        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository, times(2)).save(history.capture());
        assertTrue(history.getAllValues().get(1).getNote().startsWith("Chưa gửi được mã"));
    }

    @Test
    void operatorAdvancesStandardMissionOneStageAndRecordsWhoDidIt() {
        LockerOrder order = order("STANDARD", "LAUNCHING");
        DroneMission mission = mission("LAUNCHING", 99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        String next = service.advanceByOperator(21L, 99L);

        assertEquals("DEPARTED", next);
        assertEquals("DEPARTED", mission.getStatus());
        assertEquals("DEPARTED", order.getDeliveryStage());
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertEquals("LAUNCHING", history.getValue().getOldStatus());
        assertEquals("DEPARTED", history.getValue().getNewStatus());
        assertEquals(99L, history.getValue().getChangedByUserId());
    }

    @Test
    void operatorConfirmingDepositFinishesTheDelivery() {
        LockerOrder order = order("STANDARD", "ARRIVED");
        DroneMission mission = mission("ARRIVED", 99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        service.advanceByOperator(21L, 99L);

        assertEquals("DEPOSITED", mission.getStatus());
        assertEquals("STORING", order.getStatus());
        assertEquals("READY_FOR_PICKUP", order.getDeliveryStage());
        assertNotNull(order.getPinCode());
        assertNotNull(order.getPickupDeadline());
        verify(lockerClient).occupyBox(9001L);
        verify(lockerDroneClient)
                .transitionDroneStatus(9L, new DroneStatusTransitionRequest("IN_FLIGHT", "IDLE", null));
    }

    @Test
    void demoMissionCannotBeAdvancedByHand() {
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order("DEMO", "EN_ROUTE")));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission("EN_ROUTE", 99L)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.advanceByOperator(21L, 99L));

        assertEquals("DRONE_STAGE_AUTOMATED", error.getCode());
        verify(missionRepository, never()).save(any());
    }

    @Test
    void onlyTheAssignedOperatorMayAdvance() {
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order("STANDARD", "EN_ROUTE")));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission("EN_ROUTE", 99L)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.advanceByOperator(21L, 77L));

        assertEquals("DRONE_MISSION_NOT_ASSIGNED_TO_USER", error.getCode());
    }

    @Test
    void missionThatHasNotLaunchedCannotBeAdvanced() {
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order("STANDARD", "ACCEPTED")));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission("READY_TO_LAUNCH", 99L)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.advanceByOperator(21L, 99L));

        assertEquals("DRONE_MISSION_STATUS_INVALID", error.getCode());
    }

    private DroneMission mission(String status, Long assignedBy) {
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setStatus(status);
        mission.setAssignedByUserId(assignedBy);
        return mission;
    }

    private LockerOrder order(String mode, String stage) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setReceiverUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setFulfillmentMode(mode);
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage(stage);
        order.setReservedBoxId(9001L);
        order.setPaymentStatus("PAID");
        return order;
    }
}
