package com.huynqb.laundrylocker.payment.dto.internal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/// Tiền đã thu (và hoàn) trong một khoảng thời gian — nguồn số liệu cho báo cáo
/// doanh thu bên order-service. Xem PaymentReportRules cho định nghĩa.
public record CollectedPaymentsResponse(
        List<CollectedPayment> payments,
        List<CollectedRefund> refunds,
        List<OrderPaidTotal> orderPaidTotals) {

    public record CollectedPayment(
            Long paymentId, Long orderId, Long userId, BigDecimal amount, String method, LocalDateTime paidAt) {
    }

    public record CollectedRefund(
            Long refundId, Long paymentId, Long orderId, Long userId, BigDecimal amount, String method,
            LocalDateTime refundedAt) {
    }

    /// Tổng tiền đã thu mọi thời điểm của đơn (để tách phần phí quá hạn).
    public record OrderPaidTotal(Long orderId, BigDecimal paidAmount) {
    }
}
