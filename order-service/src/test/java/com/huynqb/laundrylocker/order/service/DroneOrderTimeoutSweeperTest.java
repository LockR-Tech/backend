package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/// Quét + đóng đơn chạy qua {@link DroneOrderExpiryService} thật, để quy tắc hạn chờ được
/// kiểm từ đầu tới cuối; chỉ repository và các service ngoài là giả.
@ExtendWith(MockitoExtension.class)
class DroneOrderTimeoutSweeperTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 12, 0);

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private LockerClient lockerClient;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private DroneOrderMaintenanceService maintenanceService;
    @Mock
    private DroneRefundService refundService;

    @Test
    void cancelsUnpaidOrderPastTheLimitAndReleasesBothCellsWithoutARefund() {
        LockerOrder order = order(21L, "UNPAID", NOW.minusMinutes(31));
        scan(order);

        assertEquals(1, sweeper(Map.of()).sweep(NOW));

        assertEquals("CANCELED", order.getStatus());
        assertEquals("CANCELED", order.getDeliveryStage());
        assertNull(order.getSourceBoxId());
        verify(lockerClient).releaseBox(9001L);
        verify(lockerClient).releaseBox(8001L);
        verify(historyRepository).save(any());
        verify(notificationClient).requestNotification(any());
        verify(refundService, never()).refundCanceledOrder(anyLong(), any());
    }

    @Test
    void keepsRecentUnpaidOrdersAndOrdersAlreadyInFlight() {
        LockerOrder recent = order(1L, "UNPAID", NOW.minusMinutes(29));
        LockerOrder inFlight = order(2L, "PAID", NOW.minusHours(9));
        inFlight.setDeliveryStage("EN_ROUTE");
        when(orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH"))
                .thenReturn(List.of(recent, inFlight));

        assertEquals(0, sweeper(Map.of()).sweep(NOW));

        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(lockerClient, never()).releaseBox(any());
        verifyNoInteractions(maintenanceService, refundService);
    }

    @Test
    void skipsOrderPaidBetweenTheScanAndTheLock() {
        LockerOrder scanned = order(21L, "UNPAID", NOW.minusHours(1));
        LockerOrder locked = order(21L, "PAID", NOW.minusHours(1));
        locked.setPaidAt(NOW.minusMinutes(1));
        when(orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH"))
                .thenReturn(List.of(scanned));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(locked));

        assertEquals(0, sweeper(Map.of()).sweep(NOW));

        assertEquals("AWAITING_DISPATCH", locked.getStatus());
        verify(lockerClient, never()).releaseBox(any());
    }

    @Test
    void paidOrderNobodyAcceptedIsCanceledRefundedAndKeepsTheCellHoldingTheParcel() {
        LockerOrder order = order(21L, "PAID", NOW.minusHours(5));
        order.setPaidAt(NOW.minusHours(5));
        // Bỏ kiện 121 phút trước, quá hạn 120 phút chờ đội bay.
        order.setParcelDroppedAt(NOW.minusMinutes(121));
        scan(order);

        assertEquals(1, sweeper(Map.of()).sweep(NOW));

        assertEquals("CANCELED", order.getStatus());
        verify(lockerClient).releaseBox(9001L);
        // Ô gửi còn kiện ⇒ giữ tới khi trả kiện.
        verify(lockerClient, never()).releaseBox(8001L);
        assertEquals(8001L, order.getSourceBoxId());
        verify(refundService).refundCanceledOrder(21L, null);
    }

    @Test
    void dispatchDeadlineRunsFromTheDropOffNotFromPayment() {
        LockerOrder order = order(21L, "PAID", NOW.minusHours(5));
        order.setPaidAt(NOW.minusHours(5));
        order.setParcelDroppedAt(NOW.minusMinutes(30));
        when(orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH"))
                .thenReturn(List.of(order));

        assertEquals(0, sweeper(Map.of()).sweep(NOW));

        assertEquals("AWAITING_DISPATCH", order.getStatus());
        verifyNoInteractions(refundService);
    }

    @Test
    void paidOrderWhoseParcelNeverArrivedReleasesBothCells() {
        LockerOrder order = order(21L, "PAID", NOW.minusHours(5));
        order.setPaidAt(NOW.minusHours(3));
        scan(order);

        assertEquals(1, sweeper(Map.of()).sweep(NOW));

        verify(lockerClient).releaseBox(9001L);
        verify(lockerClient).releaseBox(8001L);
        verify(refundService).refundCanceledOrder(21L, null);
    }

    @Test
    void unpaidWeightSurchargeIsHandedToTheMissionCancelWithItsOwnDeadline() {
        LockerOrder order = order(21L, "UNPAID", NOW.minusHours(5));
        order.setDeliveryStage("ACCEPTED");
        order.setPaidAmount(new BigDecimal("15000"));
        when(orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH"))
                .thenReturn(List.of(order));
        when(maintenanceService.cancelUnpaidSurcharge(eq(21L), isNull(), eq(NOW.minusMinutes(60))))
                .thenReturn(true);

        assertEquals(1, sweeper(Map.of()).sweep(NOW));

        verify(refundService).refundCanceledOrder(21L, null);
        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void cellReleaseFailureDoesNotKeepTheOrderAliveAndOneBadOrderDoesNotStopTheSweep() {
        LockerOrder broken = order(20L, "UNPAID", NOW.minusHours(1));
        LockerOrder order = order(21L, "UNPAID", NOW.minusHours(1));
        when(orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH"))
                .thenReturn(List.of(broken, order));
        when(orderRepository.findByIdForUpdate(20L)).thenThrow(new IllegalStateException("db timeout"));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        doThrow(new IllegalStateException("locker-service down")).when(lockerClient).releaseBox(any());

        assertEquals(1, sweeper(Map.of()).sweep(NOW));

        assertEquals("CANCELED", order.getStatus());
        assertEquals("AWAITING_DISPATCH", broken.getStatus());
    }

    @Test
    void everyDeadlineCanBeSwitchedOff() {
        Map<String, Integer> off = Map.of(
                OrderSettingsCatalog.DRONE_UNPAID_CANCEL_MINUTES, 0,
                OrderSettingsCatalog.DRONE_DISPATCH_TIMEOUT_MINUTES, 0,
                OrderSettingsCatalog.DRONE_SURCHARGE_TIMEOUT_MINUTES, 0);

        assertEquals(0, sweeper(off).sweep(NOW));

        verifyNoInteractions(orderRepository, maintenanceService, refundService);
    }

    private void scan(LockerOrder order) {
        when(orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH"))
                .thenReturn(List.of(order));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    private DroneOrderTimeoutSweeper sweeper(Map<String, ?> overrides) {
        return new DroneOrderTimeoutSweeper(
                orderRepository,
                new DroneOrderExpiryService(orderRepository, historyRepository, lockerClient, notificationClient),
                maintenanceService,
                refundService,
                TestOrderRules.of(overrides));
    }

    private static LockerOrder order(Long id, String paymentStatus, LocalDateTime createdAt) {
        LockerOrder order = new LockerOrder();
        order.setId(id);
        order.setOrderCode("ORD-" + id);
        order.setUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("AWAITING_DISPATCH");
        order.setPaymentStatus(paymentStatus);
        order.setReservedBoxId(9001L);
        order.setSourceBoxId(8001L);
        order.setCreatedAt(createdAt);
        return order;
    }
}
