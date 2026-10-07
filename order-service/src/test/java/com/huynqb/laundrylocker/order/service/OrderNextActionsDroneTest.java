package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Quy tắc F1.02: đội bay chỉ tiếp nhận và chỉ phóng khi đơn đã trả đủ — API phải nói đúng điều đó.
class OrderNextActionsDroneTest {

    @Test
    void unpaidDroneOrderWaitingForDispatchMustBePaidFirst() {
        LockerOrder order = drone("AWAITING_DISPATCH", "AWAITING_DISPATCH", "UNPAID");

        assertEquals("PAY_FOR_DRONE", OrderNextActions.nextAction(order));
        assertTrue(OrderNextActions.paymentRequired(order));
    }

    @Test
    void weightSurchargeOwedAfterLoadingAlsoRequiresPayment() {
        LockerOrder order = drone("AWAITING_DISPATCH", "ACCEPTED", "UNPAID");

        assertEquals("PAY_FOR_DRONE", OrderNextActions.nextAction(order));
        assertTrue(OrderNextActions.paymentRequired(order));
    }

    @Test
    void paidDroneOrderJustWaitsForTheFlightTeam() {
        LockerOrder order = drone("AWAITING_DISPATCH", "AWAITING_DISPATCH", "PAID");

        assertEquals("WAIT_FOR_DRONE", OrderNextActions.nextAction(order));
        assertFalse(OrderNextActions.paymentRequired(order));
    }

    @Test
    void canceledOrRefundedDroneOrderAsksForNothing() {
        assertFalse(OrderNextActions.paymentRequired(drone("CANCELED", "CANCELED", "UNPAID")));
        assertFalse(OrderNextActions.paymentRequired(drone("CANCELED", "CANCELED", "REFUNDED")));
        assertEquals("CANCELED", OrderNextActions.nextAction(drone("CANCELED", "CANCELED", "UNPAID")));
    }

    private static LockerOrder drone(String status, String stage, String paymentStatus) {
        LockerOrder order = new LockerOrder();
        order.setType("DRONE_DELIVERY");
        order.setStatus(status);
        order.setDeliveryStage(stage);
        order.setPaymentStatus(paymentStatus);
        return order;
    }
}
