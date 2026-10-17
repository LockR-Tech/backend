package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;

/// Bản sao hợp đồng `/internal/loyalty/redemptions` của loyalty-service.
public final class LoyaltyRedemption {

    private LoyaltyRedemption() {
    }

    /// `orderTotal` = tiền đơn sau mã giảm giá; loyalty kẹp `requestedPoints` theo quy tắc admin.
    public record Request(Long userId, Long orderId, BigDecimal orderTotal, Integer requestedPoints) {
    }

    public record Result(int pointsUsed, BigDecimal discountAmount) {
    }

    public record Refund(int pointsRefunded) {
    }
}
