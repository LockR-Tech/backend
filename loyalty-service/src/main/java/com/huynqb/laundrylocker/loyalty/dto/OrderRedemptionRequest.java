package com.huynqb.laundrylocker.loyalty.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/// order-service xin trừ điểm cho một đơn vừa tạo (`POST /internal/loyalty/redemptions`).
/// `orderTotal` = số tiền đơn sau mã giảm giá; `requestedPoints` ≤ 0 ⇒ không trừ gì.
public record OrderRedemptionRequest(
        @NotNull Long userId,
        @NotNull Long orderId,
        BigDecimal orderTotal,
        Integer requestedPoints) {
}
