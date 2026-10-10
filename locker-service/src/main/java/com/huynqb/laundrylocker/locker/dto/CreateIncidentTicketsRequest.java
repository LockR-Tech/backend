package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIncidentTicketsRequest(
        @NotNull Long incidentId,
        @NotBlank String incidentCode,
        @NotNull Long orderId,
        @NotBlank String orderCode,
        @NotNull Long droneUnitId,
        @NotBlank String droneCode,
        @NotNull Long reporterUserId,
        String reason,
        Double latitude,
        Double longitude,
        Double gpsAccuracyM,
        String cameraSnapshotUrl) {
}

