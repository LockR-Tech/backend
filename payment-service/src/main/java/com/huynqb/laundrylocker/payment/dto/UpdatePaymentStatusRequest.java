package com.huynqb.laundrylocker.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePaymentStatusRequest(@NotBlank String status) {
}
