package com.huynqb.laundrylocker.loyalty.dto;

import java.math.BigDecimal;

/// Kết quả trừ điểm cho đơn: số điểm thực trừ (đã kẹp theo quy tắc) và số tiền đơn được giảm.
public record OrderRedemptionResponse(int pointsUsed, BigDecimal discountAmount) {

    public static OrderRedemptionResponse none() {
        return new OrderRedemptionResponse(0, BigDecimal.ZERO);
    }
}
