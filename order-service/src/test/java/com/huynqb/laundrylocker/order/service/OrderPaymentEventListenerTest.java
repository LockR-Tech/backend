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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventListenerTest {

    @Mock
    private LockerOrderRepository orderRepository;

    @Test
    void paymentCompletedMarksDroneOrderPaidWithoutChangingDispatchStage() {
        OrderPaymentEventListener listener = new OrderPaymentEventListener(orderRepository);
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("DRONE_DELIVERY");
        order.setPaymentStatus("UNPAID");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("ACCEPTED");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
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
    }
}
