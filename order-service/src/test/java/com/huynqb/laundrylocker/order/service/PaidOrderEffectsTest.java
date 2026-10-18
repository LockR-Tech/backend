package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Đơn 0đ sau mã giảm giá / điểm thưởng đi đúng đường "đã thanh toán" như payment.completed.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaidOrderEffectsTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private NotificationClient notificationClient;
    @Mock private DronePickupService dronePickupService;
    @Mock private LockerOrderRepository orderRepository;

    private PaidOrderEffects effects;

    @BeforeEach
    void setUp() {
        effects = new PaidOrderEffects(rabbitTemplate, notificationClient, dronePickupService, orderRepository);
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void freeDroneOrderIsPaidAndStartsLookingForAPickupTechnician() {
        LockerOrder order = droneOrder(BigDecimal.ZERO);

        assertTrue(effects.settleIfFree(order));

        assertEquals("PAID", order.getPaymentStatus());
        assertNotNull(order.getPaidAt());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getPaidAmount()));
        verify(rabbitTemplate).convertAndSend(anyString(), eq(DomainEventNames.ORDER_STATUS_CHANGED), any(Object.class));
        verify(notificationClient).requestNotification(any());
        // Không có giao dịch: chạy ngay (trong ứng dụng chạy sau commit).
        verify(dronePickupService).startForPaidOrder(21L);
    }

    @Test
    void ordersWithSomethingToPayOrAlreadyPaidAreLeftAlone() {
        LockerOrder unpaid = droneOrder(new BigDecimal("5000"));
        assertFalse(effects.settleIfFree(unpaid));
        assertEquals("UNPAID", unpaid.getPaymentStatus());

        LockerOrder paid = droneOrder(BigDecimal.ZERO);
        paid.setPaymentStatus("PAID");
        assertFalse(effects.settleIfFree(paid));

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate, notificationClient);
        verify(dronePickupService, never()).startForPaidOrder(anyLong());
    }

    private static LockerOrder droneOrder(BigDecimal total) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("AWAITING_DISPATCH");
        order.setPaymentStatus("UNPAID");
        order.setOriginalPrice(new BigDecimal("15000"));
        order.setDiscount(new BigDecimal("15000").subtract(total));
        order.setTotalPrice(total);
        return order;
    }
}
