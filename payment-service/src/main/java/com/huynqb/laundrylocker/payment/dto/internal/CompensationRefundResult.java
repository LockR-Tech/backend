package com.huynqb.laundrylocker.payment.dto.internal;

import java.math.BigDecimal;

public record CompensationRefundResult(
        Long refundId,
        Long orderId,
        BigDecimal amount,
        String transactionId,
        String status,
        boolean bankAccountLinked) {
}
