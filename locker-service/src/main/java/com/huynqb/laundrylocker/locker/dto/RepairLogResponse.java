package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;
import java.util.List;

/// `actorName`: họ tên người ghi dòng nhật ký (tra user-service), null khi không tra được.
public record RepairLogResponse(
        Long id,
        Long reportId,
        Long actorUserId,
        String note,
        LocalDateTime createdAt,
        List<ReportAttachmentResponse> attachments,
        String actorName) {

    public RepairLogResponse(
            Long id, Long reportId, Long actorUserId, String note, LocalDateTime createdAt,
            List<ReportAttachmentResponse> attachments) {
        this(id, reportId, actorUserId, note, createdAt, attachments, null);
    }
}
