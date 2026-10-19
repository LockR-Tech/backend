package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;

public record CompensationRefundResult(
        Long refundId,
        Long orderId,
        BigDecimal amount,
        String transactionId,
        String status,
        boolean bankAccountLinked) {
}
