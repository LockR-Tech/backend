package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneDeliverySession;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.DroneFlightBlock;
import com.huynqb.laundrylocker.order.model.DroneStationSuspension;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneDeliverySessionRepository;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/// Đơn drone đã trả tiền mà không phục vụ được: tủ nhận bị admin dừng bay, thời tiết tuyến bay xấu, hoặc tủ nhận
/// không mở được nắp khi drone đã tới ⇒ đơn bị chặn với lý do riêng.
class DroneDeliveryBlockStationTest {

    private final LockerOrderRepository orders = mock(LockerOrderRepository.class);
    private final DroneMissionRepository missions = mock(DroneMissionRepository.class);
    private final DronePickupRepository pickups = mock(DronePickupRepository.class);
    private final DroneFlightControl flightControl = mock(DroneFlightControl.class);
    private final DroneDeliverySessionRepository sessions = mock(DroneDeliverySessionRepository.class);
    private final LockerLookupClient lockerLookup = mock(LockerLookupClient.class);
    private final DroneWeatherService weather = mock(DroneWeatherService.class);
    private final DroneDeliveryBlockService service = new DroneDeliveryBlockService(
            orders, missions, pickups, mock(OrderStatusHistoryRepository.class), mock(LockerClient.class),
            mock(LockerDroneClient.class), mock(NotificationClient.class), mock(UserClient.class),
            mock(DroneSourceLockerSelector.class), TestOrderRules.defaults(), flightControl, sessions, lockerLookup,
            weather);

    private LockerOrder paidOrder(String stage) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setType("DRONE_DELIVERY");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage(stage);
        order.setPaymentStatus("PAID");
        order.setSourceLockerId(3L);
        order.setDestinationLockerId(5L);
        when(orders.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missions.findByOrderId(21L)).thenReturn(Optional.empty());
        when(pickups.findByOrderId(21L)).thenReturn(Optional.empty());
        return order;
    }

    @Test
    void suspendedDestinationBlocksAPaidOrderAndIsRecorded() {
        LockerOrder order = paidOrder("AWAITING_DISPATCH");
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

    @Test
    void unsafeRouteWeatherBlocksAPaidOrderThatHasNotTakenOff() {
        LockerOrder order = paidOrder("AWAITING_DISPATCH");
        LockerInfo source = new LockerInfo(3L, 1L, "S", "Tủ gửi", "ACTIVE", "", 10.84, 106.80, true, null, 0, 0);
        LockerInfo destination = new LockerInfo(5L, 1L, "D", "Tủ nhận", "ACTIVE", "", 10.87, 106.80, true, null, 0, 0);
        when(lockerLookup.getLockers(List.of(3L))).thenReturn(ApiResponse.ok(List.of(source)));
        when(lockerLookup.getLockers(List.of(5L))).thenReturn(ApiResponse.ok(List.of(destination)));
        when(weather.assessRoute(source, destination)).thenReturn(new DroneWeatherAssessment(false, "UNSAFE",
                "Không thể đặt chuyến bay: có mưa hoặc dông trên tuyến trong 30 phút tới.", OffsetDateTime.now(), 30, 2,
                5, 38, 2.4, 63));

        var review = service.review(21L, LocalDateTime.now(), new DroneDeliveryBlockService.Snapshot());

        assertEquals(DroneDeliveryBlockService.Verdict.BLOCKED, review.verdict());
        assertEquals(DroneFailureReasons.WEATHER_UNSAFE, order.getDeliveryBlockReason());
        verify(flightControl).record(eq(DroneFlightBlock.IN_PROGRESS), eq(DroneFlightBlock.WEATHER_UNSAFE),
                any(), any(), any(), any());
    }

    @Test
    void unsafeWeatherWhileAirborneWarnsTheFlightTeamWithoutExpiring() {
        LockerOrder order = paidOrder("EN_ROUTE");
        LockerInfo source = new LockerInfo(3L, 1L, "S", "Tủ gửi", "ACTIVE", "", 10.84, 106.80, true, null, 0, 0);
        LockerInfo destination = new LockerInfo(5L, 1L, "D", "Tủ nhận", "ACTIVE", "", 10.87, 106.80, true, null, 0, 0);
        when(lockerLookup.getLockers(List.of(3L))).thenReturn(ApiResponse.ok(List.of(source)));
        when(lockerLookup.getLockers(List.of(5L))).thenReturn(ApiResponse.ok(List.of(destination)));
        when(weather.assessRoute(source, destination)).thenReturn(new DroneWeatherAssessment(false, "UNSAFE",
                "Thời tiết không cho phép bay: gió giật 52 km/h.", OffsetDateTime.now(), 30, 2, 30, 52, 0, 3));
        LocalDateTime now = LocalDateTime.now();

        var review = service.review(21L, now, new DroneDeliveryBlockService.Snapshot());

        assertEquals(DroneDeliveryBlockService.Verdict.BLOCKED, review.verdict());
        assertEquals(DroneFailureReasons.WEATHER_UNSAFE, order.getDeliveryBlockReason());
        assertTrue(order.getDeliveryBlockNote().startsWith("Drone đang bay"));
        // Chặn lúc đang bay không hết hạn: kiện đang trên không, không tự đóng đơn.
        assertEquals(DroneDeliveryBlockService.Verdict.UNCHANGED,
                service.review(21L, now.plusHours(3), new DroneDeliveryBlockService.Snapshot()).verdict());
    }

    @Test
    void failedLidAtDestinationBlocksAnArrivedOrder() {
        LockerOrder order = paidOrder("ARRIVED");
        DroneDeliverySession session = new DroneDeliverySession();
        session.setState(DroneDeliverySession.LID_OPEN_FAILED);
        session.setErrorMessage("Tủ chỉ báo \"đã mở\" mà không có xác nhận phần cứng");
        when(sessions.findFirstByOrderIdOrderByCreatedAtDesc(21L)).thenReturn(Optional.of(session));

        var review = service.review(21L, LocalDateTime.now(), new DroneDeliveryBlockService.Snapshot());

        assertEquals(DroneDeliveryBlockService.Verdict.BLOCKED, review.verdict());
        assertEquals(DroneFailureReasons.DESTINATION_LOCKER_FAULT, order.getDeliveryBlockReason());
        assertTrue(order.getDeliveryBlockNote().contains("chưa mở được nắp trượt"));
    }
}
