package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long id,
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        String status,
        String reason,
        String transactionId,
        Long processedByUserId,
        LocalDateTime requestedAt,
        LocalDateTime processedAt) {
}
