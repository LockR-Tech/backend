package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminWithdrawProcessRequest(
        @NotBlank(message = "Hành động xử lý không được để trống (APPROVE hoặc REJECT)")
        String action,
        String reason
) {}
