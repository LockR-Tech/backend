package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderAccessPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    private final OrderRules defaults = TestOrderRules.of(Map.of("app.order.require-payment-before-drop", true));

    @Test
    void unpaidOrderAwaitingDropCannotOpen() {
        assertEquals(OrderAccessPolicy.ORDER_UNPAID,
                OrderAccessPolicy.blockReason(order("SEND", "INITIALIZED", "UNPAID", 15000, null), defaults, NOW));
        assertEquals(OrderAccessPolicy.ORDER_UNPAID,
                OrderAccessPolicy.blockReason(order("RENTAL", "INITIALIZED", "UNPAID", 20000, null), defaults, NOW));
    }

    @Test
    void paidOrFreeOrderAwaitingDropCanOpen() {
        assertNull(OrderAccessPolicy.blockReason(order("SEND", "INITIALIZED", "PAID", 15000, null), defaults, NOW));
        assertNull(OrderAccessPolicy.blockReason(order("SEND", "INITIALIZED", "UNPAID", 0, null), defaults, NOW));
    }

    @Test
    void paymentRuleCanBeTurnedOff() {
        OrderRules relaxed = TestOrderRules.of(Map.of("app.order.require-payment-before-drop", false));
        assertNull(OrderAccessPolicy.blockReason(order("SEND", "INITIALIZED", "UNPAID", 15000, null), relaxed, NOW));
    }

    @Test
    void rentalPinWorksUntilDeadlineOnly() {
        assertNull(OrderAccessPolicy.blockReason(
                order("RENTAL", "STORING", "PAID", 20000, NOW.plusMinutes(1)), defaults, NOW));
        assertEquals(OrderAccessPolicy.RENTAL_EXPIRED, OrderAccessPolicy.blockReason(
                order("RENTAL", "STORING", "PAID", 20000, NOW.minusMinutes(1)), defaults, NOW));
    }

    @Test
    void unpaidRentalExtensionBlocksOnlyWhenEnabled() {
        LockerOrder extended = order("RENTAL", "STORING", "UNPAID", 30000, NOW.plusHours(2));
        assertNull(OrderAccessPolicy.blockReason(extended, defaults, NOW));

        OrderRules strict = TestOrderRules.of(Map.of(
                "app.order.require-payment-before-drop", true,
                "app.order.block-unpaid-rental-access", true));
        assertEquals(OrderAccessPolicy.RENTAL_UNPAID, OrderAccessPolicy.blockReason(extended, strict, NOW));
    }

    @Test
    void sendPickupIsNeverBlockedByDeadline() {
        assertNull(OrderAccessPolicy.blockReason(
                order("SEND", "STORING", "PAID", 15000, NOW.minusHours(5)), defaults, NOW));
    }

    private static LockerOrder order(
            String type, String status, String paymentStatus, long total, LocalDateTime deadline) {
        LockerOrder order = new LockerOrder();
        order.setType(type);
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        order.setTotalPrice(BigDecimal.valueOf(total));
        order.setPickupDeadline(deadline);
        return order;
    }
}
