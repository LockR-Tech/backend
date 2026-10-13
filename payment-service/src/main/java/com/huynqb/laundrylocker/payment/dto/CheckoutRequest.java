package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Pay for an existing order. method = WALLET | SEPAY | CASH (VNPAY/MOMO đã ngừng hỗ trợ ⇒ 400).
 */
public record CheckoutRequest(
        @NotNull Long orderId,
        @NotBlank String method,
        String returnUrl,
        /// Lý do trả tiền lần này, hiện lên chi tiết đơn của khách để phân biệt nhiều
        /// lần trả trên cùng một đơn ("Phí quá hạn"…). Trống thì server tự suy.
        String description,
        String orderCode) {
    public CheckoutRequest(Long orderId, String method, String returnUrl, String description) {
        this(orderId, method, returnUrl, description, null);
    }
}
