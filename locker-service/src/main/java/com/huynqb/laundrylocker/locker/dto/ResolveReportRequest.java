package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/// Body tuỳ chọn khi hoàn tất phiếu: ghi chú nghiệm thu + ảnh nghiệm thu (stage RESOLUTION).
public record ResolveReportRequest(
        @Size(max = 2000) String note,
        // Số ảnh tối đa theo cấu hình admin (app.maintenance.report-photos-per-request-staff), kiểm trong service.
        List<@Valid ReportAttachmentRequest> attachments) {
}
