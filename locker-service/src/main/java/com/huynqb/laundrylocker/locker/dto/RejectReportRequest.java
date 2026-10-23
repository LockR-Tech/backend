package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

/// KTV kiểm tra thấy không đúng như mô tả: `feedback` (bắt buộc) gửi cho người báo; ảnh hiện trường tuỳ chọn.
public record RejectReportRequest(String feedback, List<ReportAttachmentRequest> attachments) {
}
