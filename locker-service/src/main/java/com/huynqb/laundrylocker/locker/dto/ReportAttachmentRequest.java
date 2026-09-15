package com.huynqb.laundrylocker.locker.dto;

import com.huynqb.laundrylocker.common.media.MediaUpload;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/// Ảnh vừa upload lên Cloudinary (các trường MediaUpload) + thông tin chụp tại hiện trường.
/// `capturedAt` nhận ISO có hoặc không có múi giờ (`2026-09-15T08:10:00`, `…Z`, `…+07:00`).
public record ReportAttachmentRequest(
        @NotBlank String publicId,
        @NotNull Long version,
        @NotBlank String signature,
        String format,
        Long bytes,
        Integer width,
        Integer height,
        @Size(max = 500) String caption,
        String capturedAt,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {

    public MediaUpload media() {
        return new MediaUpload(publicId, version, signature, format, bytes, width, height);
    }
}
