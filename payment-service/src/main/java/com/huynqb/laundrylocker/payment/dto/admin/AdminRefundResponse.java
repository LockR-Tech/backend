package com.huynqb.laundrylocker.payment.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Hoàn tiền cho trang admin: mọi field RefundResponse + payment gốc, đơn, khách, người xử lý.
public record AdminRefundResponse(
        Long id,
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        String status,
        String reason,
        String transactionId,
        Long processedByUserId,
        LocalDateTime requestedAt,
        LocalDateTime processedAt,
        Long userId,
        String paymentMethod,
        BigDecimal paymentAmount,
        String paymentReferenceId,
        OrderRef order,
        PersonRef customer,
        PersonRef processedBy) {
}