package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/// `amount` giới hạn theo cấu hình admin (app.payment.topup-min-amount / topup-max-amount), kiểm trong PaymentService.
public record CreateTopupRequest(
        @NotNull BigDecimal amount,
        String returnUrl,
        String bankCode,
        String locale) {
}
