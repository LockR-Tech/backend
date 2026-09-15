package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/// `userId` chỉ còn để tương thích client cũ — server ưu tiên `X-User-Id` từ JWT.
public record LockerReportRequest(
        Long userId,
        @NotBlank String title,
        @NotBlank String description,
        @Size(max = 5) List<@Valid ReportAttachmentRequest> attachments) {
}
