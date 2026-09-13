package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RefundRequest(@NotNull BigDecimal amount, String reason) {
}
