package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.settings.OrderRules;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Lý do mã (PIN/QR) của một đơn tạm thời không được mở ô, dù mã đúng và đơn còn hiệu lực.
/// Trả về null khi được mở. iot-service đọc giá trị này qua `/internal/orders/by-access`,
/// nên quy tắc chỉ nằm một chỗ ở đây.
public final class OrderAccessPolicy {

    public static final String ORDER_UNPAID = "ORDER_UNPAID";
    public static final String RENTAL_EXPIRED = "RENTAL_EXPIRED";
    public static final String RENTAL_UNPAID = "RENTAL_UNPAID";

    private OrderAccessPolicy() {
    }

    public static String blockReason(LockerOrder order, OrderRules rules, LocalDateTime now) {
        String status = order.getStatus();
        boolean rental = "RENTAL".equalsIgnoreCase(order.getType());
        if ("INITIALIZED".equalsIgnoreCase(status)
                && !"DRONE_DELIVERY".equalsIgnoreCase(order.getType())
                && rules.requirePaymentBeforeDrop()
                && owesPayment(order)) {
            return ORDER_UNPAID;
        }
        if (rental && "STORING".equalsIgnoreCase(status)) {
            if (order.getPickupDeadline() != null && now.isAfter(order.getPickupDeadline())) {
                return RENTAL_EXPIRED;
            }
            if (rules.blockUnpaidRentalAccess() && owesPayment(order)) {
                return RENTAL_UNPAID;
            }
        }
        return null;
    }

    private static boolean owesPayment(LockerOrder order) {
        BigDecimal total = order.getTotalPrice();
        return total != null
                && total.compareTo(BigDecimal.ZERO) > 0
                && !"PAID".equalsIgnoreCase(order.getPaymentStatus());
    }
}
