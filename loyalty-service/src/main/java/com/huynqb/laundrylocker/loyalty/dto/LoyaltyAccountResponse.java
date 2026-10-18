package com.huynqb.laundrylocker.loyalty.dto;

/// `points` = số dư dùng được; `lifetimePoints` = tổng điểm đã tích từ trước tới nay, quyết định `tier`.
public record LoyaltyAccountResponse(
        Long id, Long userId, Integer points, Integer stamps, String tier, Integer lifetimePoints) {
}
