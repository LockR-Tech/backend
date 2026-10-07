package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveBankAccountRequest(
        @NotBlank(message = "Tên ngân hàng không được để trống")
        String bankName,

        @NotBlank(message = "Mã ngân hàng không được để trống")
        String bankCode,

        @NotBlank(message = "Số tài khoản không được để trống")
        String accountNumber,

        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        String accountHolderName
) {}
