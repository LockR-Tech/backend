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
        String bankName,
        String bankCode,
        String accountNumber,
        String accountHolderName,
        String rejectionReason,
        String bankTransferRef,
        Long processedByUserId,
        LocalDateTime requestedAt,
        LocalDateTime processedAt) {

    public RefundResponse(
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
        this(
                id,
                paymentId,
                orderId,
                amount,
                status,
                reason,
                transactionId,
                null,
                null,
                null,
                null,
                null,
                null,
                processedByUserId,
                requestedAt,
                processedAt);
    }
}
