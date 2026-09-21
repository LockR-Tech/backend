package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/// `userId` chỉ còn để tương thích client cũ — server ưu tiên `X-User-Id` từ JWT.
/// `blocking=true`: sự cố làm cả tủ ngưng nhận đơn (tủ chuyển MAINTENANCE tới khi đóng phiếu) —
/// chỉ KTV tủ/ADMIN được gửi.
public record LockerReportRequest(
        Long userId,
        @NotBlank String title,
        @NotBlank String description,
        // Số ảnh tối đa theo cấu hình admin (app.maintenance.report-photos-per-request-reporter), kiểm trong service.
        List<@Valid ReportAttachmentRequest> attachments,
        Boolean blocking) {

    public LockerReportRequest(Long userId, String title, String description, List<ReportAttachmentRequest> attachments) {
        this(userId, title, description, attachments, null);
    }
}
