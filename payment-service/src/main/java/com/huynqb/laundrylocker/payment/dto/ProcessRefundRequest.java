package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessRefundRequest(
        @NotBlank(message = "Hành động (APPROVE hoặc REJECT) là bắt buộc")
        String action,

        String bankTransferRef,

        String rejectionReason
) {}
