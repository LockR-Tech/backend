package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BoxFaultRequest(
        @Size(max = 2000) String reason,
        // Số ảnh tối đa theo cấu hình admin (app.maintenance.report-photos-per-request-reporter), kiểm trong service.
        List<@Valid ReportAttachmentRequest> attachments) {
}
