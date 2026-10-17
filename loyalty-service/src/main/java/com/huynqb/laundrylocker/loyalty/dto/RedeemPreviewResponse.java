package com.huynqb.laundrylocker.loyalty.dto;

import java.math.BigDecimal;

/// Khách xem trước được dùng bao nhiêu điểm cho một đơn (`GET /api/loyalty/redeem-preview`).
/// `maxPoints` = min(balance, floor(orderTotal × maxPercent / 100 / vndPerPoint)); dưới `minPoints`
/// hoặc `enabled = false` ⇒ `maxPoints = 0`, `maxDiscount = 0`.
public record RedeemPreviewResponse(
        boolean enabled,
        int balance,
        int vndPerPoint,
        int maxPercent,
        int minPoints,
        int maxPoints,
        BigDecimal maxDiscount) {
}
