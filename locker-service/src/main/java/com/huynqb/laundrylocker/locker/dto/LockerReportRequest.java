package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/// `userId` chỉ còn để tương thích client cũ — server ưu tiên `X-User-Id` từ JWT.
public record LockerReportRequest(
        Long userId,
        @NotBlank String title,
        @NotBlank String description,
        // Số ảnh tối đa theo cấu hình admin (app.maintenance.report-photos-per-request-reporter), kiểm trong service.
        List<@Valid ReportAttachmentRequest> attachments) {
}
