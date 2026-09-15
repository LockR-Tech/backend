package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RepairLogRequest(
        @NotBlank @Size(max = 2000) String note,
        // Số ảnh tối đa theo cấu hình admin (app.maintenance.report-photos-per-request-staff), kiểm trong service.
        List<@Valid ReportAttachmentRequest> attachments) {
}
