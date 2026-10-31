package com.huynqb.laundrylocker.loyalty.dto;

import java.time.LocalDateTime;

/// Một thành viên trong danh sách /admin/loyalty. `tier`, `newThisMonth`, `active30Days` tính cùng mốc với
/// thống kê tổng nên lọc theo chúng ra đúng con số trên thẻ thống kê.
public record LoyaltyMemberResponse(
        Long userId,
        int points,
        int lifetimePoints,
        int stamps,
        String tier,
        LocalDateTime joinedAt,
        long transactionCount,
        long pointsIssued,
        long pointsRedeemed,
        LocalDateTime lastTransactionAt,
        boolean newThisMonth,
        boolean active30Days) {
}
