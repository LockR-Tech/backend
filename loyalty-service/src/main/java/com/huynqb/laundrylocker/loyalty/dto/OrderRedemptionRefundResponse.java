package com.huynqb.laundrylocker.loyalty.dto;

/// Số điểm vừa hoàn cho đơn; 0 khi đơn không dùng điểm hoặc đã hoàn trước đó.
public record OrderRedemptionRefundResponse(int pointsRefunded) {
}
