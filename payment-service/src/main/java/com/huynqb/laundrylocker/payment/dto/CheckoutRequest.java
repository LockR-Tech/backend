package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Pay for an existing order. method = WALLET | VNPAY | MOMO | CASH.
 */
public record CheckoutRequest(
        @NotNull Long orderId,
        @NotBlank String method,
        String bankCode,
        String returnUrl,
        String language) {
}
