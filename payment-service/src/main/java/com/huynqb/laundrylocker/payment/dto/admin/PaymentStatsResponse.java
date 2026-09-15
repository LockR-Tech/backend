package com.huynqb.laundrylocker.payment.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/// Thống kê giao dịch theo khoảng ngày (giờ Việt Nam) + kỳ trước cùng độ dài + hôm nay/hôm qua.
/// Giao dịch được xếp vào ngày theo createdAt; hoàn tiền theo processedAt (fallback requestedAt).
public record PaymentStatsResponse(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        PeriodStats current,
        PeriodStats previous,
        PeriodStats today,
        PeriodStats yesterday,
        Changes changes,
        Changes todayChanges) {

    public record PeriodStats(
            LocalDate from,
            LocalDate to,
            long totalCount,
            BigDecimal totalAmount,
            long completedCount,
            BigDecimal completedAmount,
            long pendingCount,
            BigDecimal pendingAmount,
            long failedCount,
            BigDecimal failedAmount,
            long otherCount,
            BigDecimal otherAmount,
            /// % completed/total, 2 chữ số; null khi không có giao dịch.
            BigDecimal successRate,
            KindStats orderPayments,
            KindStats topups,
            long refundCount,
            BigDecimal refundAmount,
            /// Tiền đơn đã thu (không gồm nạp ví) trừ hoàn tiền.
            BigDecimal netCollectedAmount,
            List<Bucket> byStatus,
            List<Bucket> byMethod) {
    }

    public record KindStats(long count, BigDecimal amount, long completedCount, BigDecimal completedAmount) {
    }

    public record Bucket(String key, long count, BigDecimal amount, long completedCount, BigDecimal completedAmount) {
    }

    /// % thay đổi so với kỳ so sánh (2 chữ số); null khi kỳ so sánh bằng 0.
    /// successRatePoints: chênh lệch điểm phần trăm tỉ lệ thành công.
    public record Changes(
            BigDecimal totalCountPct,
            BigDecimal totalAmountPct,
            BigDecimal completedCountPct,
            BigDecimal completedAmountPct,
            BigDecimal refundAmountPct,
            BigDecimal netCollectedAmountPct,
            BigDecimal successRatePoints) {
    }
}
