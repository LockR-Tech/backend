package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Admin manual adjustment. Positive amount credits, negative debits.
 */
public record WalletAdjustRequest(@NotNull BigDecimal amount, String reason) {
}
