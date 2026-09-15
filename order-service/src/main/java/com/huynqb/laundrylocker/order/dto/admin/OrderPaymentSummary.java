package com.huynqb.laundrylocker.order.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Bản sao OrderPaymentSummary của payment-service (/internal/payments/order-summaries).
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