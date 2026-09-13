package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String method,
        String status,
        String referenceId,
        String referenceTransactionId,
        String paymentUrl,
        String qrCodeUrl,
        String deeplink,
        String description) {
}
