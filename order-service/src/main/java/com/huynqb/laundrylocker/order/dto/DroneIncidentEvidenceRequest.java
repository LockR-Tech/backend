package com.huynqb.laundrylocker.order.dto;

import com.huynqb.laundrylocker.common.media.MediaUpload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DroneIncidentEvidenceRequest(
        @NotNull @Valid MediaUpload media,
        @Size(max = 500) String caption,
        Double latitude,
        Double longitude,
        Double gpsAccuracyM,
        String capturedAt) {
}

