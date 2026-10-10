package com.huynqb.laundrylocker.loyalty.dto;

import java.util.Map;

/// Thống kê tổng quan trang /admin/loyalty. Mốc "tháng này" cắt theo giờ Việt Nam
/// (BusinessTime); "30 ngày gần nhất" tính lùi từ thời điểm gọi API.
public record LoyaltyStatisticsResponse(
        /// Giữ cho client cũ — bằng `totalMembers`.
        long accounts,
        /// Giữ cho client cũ — tổng số giao dịch điểm.
        long transactions,
        long totalMembers,
        long newMembersThisMonth,
        /// Số người dùng khác nhau có ít nhất một giao dịch điểm trong 30 ngày gần nhất.
        long activeMembersLast30Days,
        /// Hạng → số tài khoản; luôn có đủ BRONZE/SILVER/GOLD/PLATINUM (0 nếu trống).
        Map<String, Long> tierDistribution,
        /// Tổng điểm đang nằm trong các tài khoản.
        long totalPointsOutstanding,
        double averagePoints,
        double medianPoints,
        /// Tổng điểm của các giao dịch dương (cộng điểm).
        long pointsIssued,
        /// Tổng trị tuyệt đối của các giao dịch âm (đổi/trừ điểm).
        long pointsRedeemed,
        long pointsIssuedThisMonth,
        long pointsRedeemedThisMonth) {
}
