package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DroneParcelServiceTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private DroneMissionRepository missionRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private LockerClient lockerClient;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private UserClient userClient;

    @Test
    void senderConfirmsDropOffOnceTheOrderIsPaid() {
        LockerOrder order = order("AWAITING_DISPATCH", "AWAITING_DISPATCH", "PAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(userClient.getUsersByRole("DRONE_TECHNICIAN"))
                .thenReturn(ApiResponse.ok(List.of(new UserSummary(99L, "t@test", "0902", "Tech", "ACTIVE"))));

        service().confirmDrop(21L, 44L);

        assertNotNull(order.getParcelDroppedAt());
        // Ô gửi đã có kiện.
        verify(lockerClient).occupyBox(8001L);
        verify(historyRepository).save(any());
        // Báo đội bay là đơn đã sẵn sàng tiếp nhận.
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void dropOffConfirmationIsIdempotentAndGuarded() {
        LockerOrder order = order("AWAITING_DISPATCH", "AWAITING_DISPATCH", "UNPAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        assertEquals("ORDER_FORBIDDEN", code(() -> service().confirmDrop(21L, 7L)));
        assertEquals("DRONE_ORDER_UNPAID", code(() -> service().confirmDrop(21L, 44L)));

        order.setPaymentStatus("PAID");
        order.setDeliveryStage("ACCEPTED");
        assertEquals("DRONE_ORDER_STATUS_INVALID", code(() -> service().confirmDrop(21L, 44L)));
        assertNull(order.getParcelDroppedAt());

        LocalDateTime first = LocalDateTime.of(2026, 10, 9, 8, 0);
        order.setParcelDroppedAt(first);
        service().confirmDrop(21L, 44L);
        assertEquals(first, order.getParcelDroppedAt());
        verify(lockerClient, never()).occupyBox(any());
    }

    @Test
    void returningAParcelStillInTheSourceCellReleasesThatCell() {
        LockerOrder order = order("CANCELED", "CANCELED", "REFUND_PENDING");
        order.setParcelDroppedAt(LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.empty());

        service().confirmReturn(21L, 99L, false, "  Giao tan tay  ");

        assertNotNull(order.getParcelReturnedAt());
        assertEquals(99L, order.getParcelReturnedByUserId());
        assertEquals("Giao tan tay", order.getParcelReturnNote());
        assertNull(order.getSourceBoxId());
        verify(lockerClient).releaseBox(8001L);
        verify(notificationClient).requestNotification(any());
        assertFalse(DroneParcelCustody.returnPending(order, null));
    }

    @Test
    void onlyTheAssignedTechnicianOrAnAdminReturnsAParcelHeldByTheFlightTeam() {
        LockerOrder order = order("CANCELED", "FAILED", "REFUND_PENDING");
        order.setParcelDroppedAt(LocalDateTime.now());
        order.setSourceBoxId(null);
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setAssignedByUserId(99L);
        mission.setLoadedAt(LocalDateTime.now());
        mission.setStatus("FAILED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        assertEquals(
                "DRONE_MISSION_NOT_ASSIGNED_TO_USER", code(() -> service().confirmReturn(21L, 7L, false, null)));
        assertNull(order.getParcelReturnedAt());

        service().confirmReturn(21L, 1L, true, null);

        assertNotNull(order.getParcelReturnedAt());
        verify(lockerClient, never()).releaseBox(any());
        // Trả rồi thì không trả lần hai.
        assertEquals("DRONE_PARCEL_RETURN_NOT_PENDING", code(() -> service().confirmReturn(21L, 1L, true, null)));
    }

    @Test
    void nothingToReturnWhenTheParcelNeverEnteredTheSystemOrTheOrderIsStillRunning() {
        LockerOrder neverDropped = order("CANCELED", "CANCELED", "UNPAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(neverDropped));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.empty());
        assertEquals("DRONE_PARCEL_RETURN_NOT_PENDING", code(() -> service().confirmReturn(21L, 99L, false, null)));

        LockerOrder running = order("AWAITING_DISPATCH", "AWAITING_DISPATCH", "PAID");
        running.setParcelDroppedAt(LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(running));
        assertEquals("DRONE_PARCEL_RETURN_NOT_PENDING", code(() -> service().confirmReturn(21L, 99L, false, null)));
    }

    private DroneParcelService service() {
        return new DroneParcelService(
                orderRepository, missionRepository, historyRepository, lockerClient, notificationClient, userClient);
    }

    private static String code(Runnable call) {
        return assertThrows(BusinessException.class, call::run).getCode();
    }

    private static LockerOrder order(String status, String stage, String paymentStatus) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setStatus(status);
        order.setDeliveryStage(stage);
        order.setPaymentStatus(paymentStatus);
        order.setReservedBoxId(9001L);
        order.setSourceBoxId(8001L);
        return order;
    }
}
