package com.huynqb.laundrylocker.payment.dto.internal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Tóm tắt thanh toán của một đơn cho order-service (trang admin đơn hàng).
/// latest* = payment tạo gần nhất (mọi trạng thái); lastPaid* = payment COMPLETED gần nhất.
public record OrderPaymentSummary(
        Long orderId,
        int paymentCount,
        Long latestPaymentId,
        String latestMethod,
        String latestStatus,
        BigDecimal latestAmount,
        LocalDateTime latestCreatedAt,
        BigDecimal paidAmount,
        String lastPaidMethod,
        LocalDateTime lastPaidAt,
        BigDecimal refundedAmount) {
}
