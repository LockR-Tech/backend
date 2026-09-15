package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/// Bổ sung ảnh vào phiếu. `stage` bỏ trống ở API của người báo (luôn là REPORT);
/// `note` có giá trị ⇒ tạo một dòng nhật ký và gắn ảnh vào dòng đó.
public record ReportAttachmentsRequest(
        String stage,
        @Size(max = 2000) String note,
        @NotEmpty @Size(max = 10) List<@Valid ReportAttachmentRequest> attachments) {
}
