package com.huynqb.laundrylocker.order.dto;

import com.huynqb.laundrylocker.common.media.MediaUpload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportDroppedParcelRequest(
        @NotBlank @Size(max = 1000) String reason,
        Double latitude,
        Double longitude,
        Double gpsAccuracyM,
        String cameraStatus,
        MediaUpload cameraSnapshot,
        String snapshotCapturedAt) {
}

