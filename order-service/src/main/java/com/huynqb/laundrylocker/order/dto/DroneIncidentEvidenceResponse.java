package com.huynqb.laundrylocker.order.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public record DroneIncidentEvidenceResponse(
        Long id, String stage, String url, String secureUrl, String caption,
        Double latitude, Double longitude, Double gpsAccuracyM,
        Instant capturedAt, Long uploadedByUserId, LocalDateTime createdAt) {

    public DroneIncidentEvidenceResponse(
            Long id, String stage, String url, String caption,
            Double latitude, Double longitude, Double gpsAccuracyM,
            Instant capturedAt, Long uploadedByUserId, LocalDateTime createdAt) {
        this(id, stage, url, url, caption, latitude, longitude, gpsAccuracyM, capturedAt, uploadedByUserId, createdAt);
    }
}

