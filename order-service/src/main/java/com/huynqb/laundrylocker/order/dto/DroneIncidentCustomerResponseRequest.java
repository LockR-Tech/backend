package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DroneIncidentCustomerResponseRequest(
        @NotBlank String decision,
        @Size(max = 1000) String note) {
}

