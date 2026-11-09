package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/// Gắn ảnh vào hồ sơ lắp đặt. `item` = bước nghiệm thu (MOUNTING, KIOSK, …) hoặc bỏ trống = ảnh hiện trường chung.
/// Mỗi ảnh là kết quả upload Cloudinary với purpose INSTALLATION_PHOTO.
public record InstallationPhotosRequest(
        String item,
        @NotEmpty @Size(max = 10) List<@Valid ReportAttachmentRequest> photos) {
}
