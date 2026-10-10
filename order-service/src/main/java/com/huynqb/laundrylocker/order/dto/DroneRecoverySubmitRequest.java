package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DroneRecoverySubmitRequest(
        @NotBlank String outcome,
        String parcelCondition,
        @Size(max = 2000) String note,
        Double latitude,
        Double longitude,
        Double gpsAccuracyM,
        List<@Valid DroneIncidentEvidenceRequest> evidence) {
}

