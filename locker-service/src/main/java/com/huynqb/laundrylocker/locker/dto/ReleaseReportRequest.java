package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.Size;

/// KTV trả phiếu đang giữ về hàng chờ — lý do bắt buộc (kiểm trong service: REPORT_RELEASE_REASON_REQUIRED).
public record ReleaseReportRequest(@Size(max = 1000) String reason) {
}
