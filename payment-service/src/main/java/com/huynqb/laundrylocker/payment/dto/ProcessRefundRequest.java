package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessRefundRequest(
        String action,

        String bankTransferRef,

        String rejectionReason,

        String proofImageUrl
) {}
