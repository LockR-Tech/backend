package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;

public record IncidentCompensationUpdateRequest(
        @NotBlank String status,
        String reference,
        String detail) {
}
