package com.huynqb.laundrylocker.order.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/// Bản sao CollectedPaymentsResponse của payment-service (/internal/payments/collected).
public record CollectedPayments(
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

    public record OrderPaidTotal(Long orderId, BigDecimal paidAmount) {
    }
}