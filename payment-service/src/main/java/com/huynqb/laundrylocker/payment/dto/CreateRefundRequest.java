package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateRefundRequest(
        @NotNull(message = "Mã đơn hàng không được để trống")
        Long orderId,

        String reason,

        String bankName,

        String bankCode,

        String accountNumber,

        String accountHolderName,

        Boolean saveAsDefault
) {}
