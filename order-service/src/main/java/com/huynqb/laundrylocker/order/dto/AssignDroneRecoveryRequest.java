package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotNull;

public record AssignDroneRecoveryRequest(
        @NotNull Long technicianId,
        Long responsibilityLockerId,
        String note) {
}
