package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WithdrawRequest(
        @NotNull(message = "Số tiền rút không được để trống")
        // Mức tối thiểu là quy tắc admin chỉnh (`app.payment.withdraw-min-amount`), kiểm ở WalletService.
        @Positive(message = "Số tiền rút phải lớn hơn 0")
        BigDecimal amount,

        @NotBlank(message = "Tên ngân hàng không được để trống")
        String bankName,

        @NotBlank(message = "Mã ngân hàng không được để trống")
        String bankCode,

        @NotBlank(message = "Số tài khoản không được để trống")
        String accountNumber,

        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        String accountHolderName
) {}
