package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnFlightUpdateRequest(
        @NotBlank String status,
        @Size(max = 1000) String detail) {
}
