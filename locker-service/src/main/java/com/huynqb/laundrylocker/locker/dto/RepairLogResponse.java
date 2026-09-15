package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RepairLogResponse(
        Long id,
        Long reportId,
        Long actorUserId,
        String note,
        LocalDateTime createdAt,
        List<ReportAttachmentResponse> attachments) {
}
