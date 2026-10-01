package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;

/// Bản sao `OrderRefundResult` của payment-service.
public record OrderRefundResult(Long orderId, BigDecimal refundedAmount, int refundedPayments) {
}
