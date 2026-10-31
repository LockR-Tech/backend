package com.huynqb.laundrylocker.loyalty.dto;

import java.util.List;

/// Giao dịch tạo nên một con số thống kê: một trang giao dịch, cộng với tổng theo loại và theo người
/// tính trên toàn bộ giao dịch khớp bộ lọc (không chỉ trang hiện tại).
public record LoyaltyTransactionsResponse(
        List<AdminPointTransactionResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        /// Tổng điểm khớp bộ lọc; với REDEEMED là số dương, khớp "Tổng điểm đã dùng".
        long totalPoints,
        List<TypeTotal> byType,
        /// Tối đa 20 người có tổng điểm lớn nhất.
        List<UserTotal> topUsers) {

    public record TypeTotal(String type, long transactions, long points) {
    }

    public record UserTotal(Long userId, long transactions, long points) {
    }
}
