package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull BigDecimal amount,
        String method,
        String bankCode,
        String language,
        String description,
        String referenceId) {
}
