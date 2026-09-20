package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;

/** Internal compare-and-set request used to prevent two missions from taking the same drone. */
public record DroneStatusTransitionRequest(
        @NotBlank String expectedStatus,
        @NotBlank String status,
        String reason) {
}
