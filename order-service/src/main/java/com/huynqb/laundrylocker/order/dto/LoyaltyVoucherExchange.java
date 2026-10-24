package com.huynqb.laundrylocker.order.dto;

/// Bản sao hợp đồng `/internal/loyalty/voucher-exchanges` của loyalty-service.
public final class LoyaltyVoucherExchange {

    private LoyaltyVoucherExchange() {
    }

    /// `reference` = mã của một lần đổi; gọi lại cùng mã không trừ thêm.
    public record Request(Long userId, Integer points, String reference) {
    }

    /// `exchanged = false` khi không đủ điểm (không trừ gì, `balance` = số dư hiện có).
    public record Result(boolean exchanged, int pointsSpent, int balance) {
    }
}
