package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.PaymentRefundClient;
import com.huynqb.laundrylocker.order.dto.OrderRefundResult;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroneRefundServiceTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private PaymentRefundClient paymentRefundClient;
    @Mock
    private NotificationClient notificationClient;

    private DroneRefundService service;

    @BeforeEach
    void setUp() {
        service = new DroneRefundService(orderRepository, historyRepository, paymentRefundClient, notificationClient);
    }

    @Test
    void refundsPaidDroneOrderThatWasCanceled() {
        LockerOrder order = order("CANCELED", "PAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(paymentRefundClient.refundOrder(eq(21L), any(), eq(99L)))
                .thenReturn(ApiResponse.ok(new OrderRefundResult(21L, BigDecimal.valueOf(15000), 1)));

        assertTrue(service.refundCanceledOrder(21L, 99L));

        assertEquals("REFUNDED", order.getPaymentStatus());
        verify(orderRepository).save(order);
        verify(historyRepository).save(any());
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void keepsOrderPaidWhenPaymentServiceFails() {
        LockerOrder order = order("CANCELED", "PAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(paymentRefundClient.refundOrder(eq(21L), any(), any()))
                .thenThrow(new RuntimeException("payment-service unavailable"));

        assertFalse(service.refundCanceledOrder(21L, 99L));

        // Không được đánh dấu đã hoàn khi tiền chưa về ví.
        assertEquals("PAID", order.getPaymentStatus());
        verify(orderRepository, never()).save(any());
        verify(historyRepository).save(any());
    }

    @Test
    void ignoresUnpaidOrStillActiveOrders() {
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order("CANCELED", "UNPAID")));
        assertFalse(service.refundCanceledOrder(21L, 99L));

        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order("AWAITING_DISPATCH", "PAID")));
        assertFalse(service.refundCanceledOrder(21L, 99L));

        verify(paymentRefundClient, never()).refundOrder(any(), any(), any());
    }

    @Test
    void refundsWhatWasPaidWhenCanceledWhileWeightSurchargeIsStillOwed() {
        LockerOrder order = order("CANCELED", "UNPAID");
        order.setPaidAmount(BigDecimal.valueOf(15000));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(paymentRefundClient.refundOrder(eq(21L), any(), eq(99L)))
                .thenReturn(ApiResponse.ok(new OrderRefundResult(21L, BigDecimal.valueOf(15000), 1)));

        assertTrue(service.refundCanceledOrder(21L, 99L));

        assertEquals("REFUNDED", order.getPaymentStatus());
    }

    @Test
    void doesNotRefundTwice() {
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order("CANCELED", "REFUNDED")));

        assertFalse(service.refundCanceledOrder(21L, 99L));

        verify(paymentRefundClient, never()).refundOrder(any(), any(), any());
    }

    private LockerOrder order(String status, String paymentStatus) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        return order;
    }
}
