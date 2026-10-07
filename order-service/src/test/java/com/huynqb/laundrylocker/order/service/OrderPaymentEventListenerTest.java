package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventListenerTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private DroneLateRefundScheduler droneLateRefunds;

    @Test
    void paymentCompletedMarksDroneOrderPaidWithoutChangingDispatchStage() {
        OrderPaymentEventListener listener = new OrderPaymentEventListener(orderRepository, droneLateRefunds);
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("DRONE_DELIVERY");
        order.setPaymentStatus("UNPAID");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("ACCEPTED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentEvent(
                DomainEvent.of(
                        DomainEventNames.PAYMENT_COMPLETED,
                        "payment-service",
                        Map.of("orderId", 21L)));

        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("AWAITING_DISPATCH", order.getStatus());
        assertEquals("ACCEPTED", order.getDeliveryStage());
        verify(orderRepository).save(order);
        verify(droneLateRefunds, never()).schedule(any());
    }

    @Test
    void moneyArrivingAfterADroneOrderWasCanceledIsSentBackNotKept() {
        OrderPaymentEventListener listener = new OrderPaymentEventListener(orderRepository, droneLateRefunds);
        LockerOrder order = droneOrder("CANCELED", "UNPAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        listener.onPaymentEvent(completed());

        // Vẫn ghi nhận đã thu để admin thấy nếu hoàn tự động hỏng.
        assertEquals("PAID", order.getPaymentStatus());
        verify(droneLateRefunds).schedule(21L);
    }

    @Test
    void aSecondLatePaymentOnAnAlreadyRefundedDroneOrderIsAlsoRefunded() {
        OrderPaymentEventListener listener = new OrderPaymentEventListener(orderRepository, droneLateRefunds);
        LockerOrder order = droneOrder("CANCELED", "REFUNDED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        listener.onPaymentEvent(completed());

        assertEquals("REFUNDED", order.getPaymentStatus());
        verify(orderRepository, never()).save(any());
        verify(droneLateRefunds).schedule(21L);
    }

    private static LockerOrder droneOrder(String status, String paymentStatus) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("DRONE_DELIVERY");
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        return order;
    }

    private static DomainEvent completed() {
        return DomainEvent.of(DomainEventNames.PAYMENT_COMPLETED, "payment-service", Map.of("orderId", 21L));
    }
}
