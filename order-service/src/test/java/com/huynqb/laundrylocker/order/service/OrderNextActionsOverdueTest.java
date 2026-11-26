package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Cờ "quá hạn" của đơn (trang chi tiết đơn admin): đơn đã lấy hàng đúng hạn không bị coi là quá hạn chỉ vì
/// bây giờ đã qua mốc hạn — ví dụ thuê 1 giờ, 2 phút sau đã lấy đồ.
class OrderNextActionsOverdueTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 11, 25, 10, 57);

    @Test
    void completedOnTimeIsNeverOverdue() {
        LockerOrder order = order("COMPLETED", BigDecimal.ZERO);

        assertFalse(OrderNextActions.overdue(order, DEADLINE.plusDays(1)));
    }

    @Test
    void closedOrderIsOverdueOnlyWhenAnOverdueFeeWasCharged() {
        assertTrue(OrderNextActions.overdue(order("COMPLETED", BigDecimal.valueOf(5000)), DEADLINE.plusDays(1)));
        assertFalse(OrderNextActions.overdue(order("CANCELED", null), DEADLINE.plusDays(1)));
    }

    @Test
    void openOrderIsOverdueOncePastTheDeadline() {
        LockerOrder storing = order("STORING", BigDecimal.ZERO);

        assertFalse(OrderNextActions.overdue(storing, DEADLINE.minusMinutes(1)));
        assertTrue(OrderNextActions.overdue(storing, DEADLINE.plusMinutes(1)));
        assertTrue(OrderNextActions.overdue(order("EXPIRED", BigDecimal.ZERO), DEADLINE.plusHours(30)));
    }

    @Test
    void orderWithoutDeadlineIsNotOverdue() {
        LockerOrder order = order("INITIALIZED", null);
        order.setPickupDeadline(null);

        assertFalse(OrderNextActions.overdue(order, DEADLINE.plusDays(1)));
    }

    private static LockerOrder order(String status, BigDecimal extraFee) {
        LockerOrder order = new LockerOrder();
        order.setType("RENTAL");
        order.setStatus(status);
        order.setExtraFee(extraFee);
        order.setPickupDeadline(DEADLINE);
        return order;
    }
}
