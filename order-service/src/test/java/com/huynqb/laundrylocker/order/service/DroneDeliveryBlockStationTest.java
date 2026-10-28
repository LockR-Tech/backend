package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.DroneFlightBlock;
import com.huynqb.laundrylocker.order.model.DroneStationSuspension;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/// Đơn drone đã trả tiền mà tủ nhận đang bị admin dừng bay ⇒ đơn bị chặn với lý do riêng và vào lịch sử chặn bay.
class DroneDeliveryBlockStationTest {

    private final LockerOrderRepository orders = mock(LockerOrderRepository.class);
    private final DroneMissionRepository missions = mock(DroneMissionRepository.class);
    private final DronePickupRepository pickups = mock(DronePickupRepository.class);
    private final DroneFlightControl flightControl = mock(DroneFlightControl.class);
    private final DroneDeliveryBlockService service = new DroneDeliveryBlockService(
            orders, missions, pickups, mock(OrderStatusHistoryRepository.class), mock(LockerClient.class),
            mock(LockerDroneClient.class), mock(NotificationClient.class), mock(UserClient.class),
            mock(DroneSourceLockerSelector.class), TestOrderRules.defaults(), flightControl);

    @Test
    void suspendedDestinationBlocksAPaidOrderAndIsRecorded() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setType("DRONE_DELIVERY");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("AWAITING_DISPATCH");
        order.setPaymentStatus("PAID");
        order.setSourceLockerId(3L);
        order.setDestinationLockerId(5L);
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missions.findByOrderId(21L)).thenReturn(Optional.empty());
        when(pickups.findByOrderId(21L)).thenReturn(Optional.empty());
        DroneStationSuspension stop = new DroneStationSuspension();
        stop.setLockerId(5L);
        stop.setReason("WEATHER");
        when(flightControl.activeSuspension(5L)).thenReturn(Optional.of(stop));

        var review = service.review(21L, LocalDateTime.now(), new DroneDeliveryBlockService.Snapshot());

        assertEquals(DroneDeliveryBlockService.Verdict.BLOCKED, review.verdict());
        assertEquals(DroneFailureReasons.STATION_SUSPENDED, order.getDeliveryBlockReason());
        verify(flightControl).record(eq(DroneFlightBlock.IN_PROGRESS), eq(DroneFlightBlock.STATION_SUSPENDED),
                argThat(context -> Long.valueOf(21L).equals(context.orderId())), eq(5L), any(), any());
    }
}
