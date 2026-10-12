package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Nhận `order.completed`: đơn đã thanh toán được cộng điểm theo `paidAmount`, mỗi đơn một lần.
@ExtendWith(MockitoExtension.class)
class OrderCompletedPointsListenerTest {

    @Mock LoyaltyService loyaltyService;
    @InjectMocks OrderCompletedPointsListener listener;

    @Test
    void paidOrderEarnsOnPaidAmount() {
        when(loyaltyService.awardOrderPoints(any(), any(), any())).thenReturn(Optional.empty());

        listener.onOrderCompleted(completed("PAID", new BigDecimal("25000")));

        verify(loyaltyService).awardOrderPoints(55L, 44L, new BigDecimal("25000"));
    }

    @Test
    void unpaidFreeOrRefundedOrdersEarnNothing() {
        listener.onOrderCompleted(completed("UNPAID", new BigDecimal("25000")));
        listener.onOrderCompleted(completed("REFUND_PENDING", new BigDecimal("25000")));
        listener.onOrderCompleted(completed(null, new BigDecimal("25000")));
        listener.onOrderCompleted(completed("PAID", BigDecimal.ZERO));
        listener.onOrderCompleted(completed("PAID", null));

        verifyNoInteractions(loyaltyService);
    }

    @Test
    void otherEventsAreIgnored() {
        Map<String, Object> payload = new HashMap<>(completed("PAID", new BigDecimal("25000")).payload());
        listener.onOrderCompleted(DomainEvent.of(DomainEventNames.ORDER_STATUS_CHANGED, "order-service", payload));
        listener.onOrderCompleted(null);

        verifyNoInteractions(loyaltyService);
    }

    @Test
    void redeliveredEventHittingUniqueIndexIsIgnored() {
        when(loyaltyService.awardOrderPoints(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uq_point_transactions_order_earn"));

        listener.onOrderCompleted(completed("PAID", new BigDecimal("25000")));
    }

    private static DomainEvent completed(String paymentStatus, BigDecimal paidAmount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", 55L);
        payload.put("orderCode", "ORD-20261012-ABC123");
        payload.put("userId", 44L);
        payload.put("type", "LAUNDRY");
        payload.put("paidAmount", paidAmount);
        payload.put("totalPrice", new BigDecimal("25000"));
        payload.put("paymentStatus", paymentStatus);
        payload.put("completedAt", "2026-10-12T09:00:00");
        return DomainEvent.of(DomainEventNames.ORDER_COMPLETED, "order-service", payload);
    }
}
