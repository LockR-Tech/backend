package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record ReportAttachmentResponse(
        Long id,
        Long reportId,
        Long repairLogId,
        String stage,
        String url,
        String thumbnailUrl,
        String publicId,
        String format,
        Long bytes,
        Integer width,
        Integer height,
        String caption,
        Double latitude,
        Double longitude,
        LocalDateTime capturedAt,
        Long uploadedByUserId,
        LocalDateTime createdAt) {
}
