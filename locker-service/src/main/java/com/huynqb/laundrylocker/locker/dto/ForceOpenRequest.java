package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/// Body (không bắt buộc) của mở ô khẩn cấp. App KTV luôn gửi `reason`; web admin cũ không gửi body.
/// `reportId` ⇒ thêm một dòng nhật ký xử lý (kèm ảnh `attachments`) vào phiếu đó; ảnh chỉ lưu được
/// khi có phiếu.
public record ForceOpenRequest(
        @Size(max = 255) String reason,
        @Size(max = 1000) String note,
        // Số ảnh tối đa theo cấu hình admin (app.maintenance.report-photos-per-request-staff), kiểm trong service.
        List<@Valid ReportAttachmentRequest> attachments,
        Long reportId) {
}
