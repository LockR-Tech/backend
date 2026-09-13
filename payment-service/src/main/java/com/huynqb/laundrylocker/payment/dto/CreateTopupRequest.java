package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateTopupRequest(
        @NotNull @Min(1000) BigDecimal amount,
        String returnUrl,
        String bankCode,
        String locale) {
}
