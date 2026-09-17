package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LockerReportResponse(
        Long id,
        Long lockerId,
        Long boxId,
        Long userId,
        String title,
        String description,
        String status,
        Long assignedToUserId,
        LocalDateTime assignedAt,
        Long resolvedByUserId,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        String lockerCode,
        String lockerName,
        String lockerAddress,
        Double lockerLatitude,
        Double lockerLongitude,
        Integer boxNumber,
        String cellType,
        Integer slaHours,
        LocalDateTime slaDueAt,
        Boolean overdue,
        Integer slaExtendedHours,
        String slaExtensionReason,
        String reporterName,
        String reporterPhone,
        List<ReportAttachmentResponse> attachments) {
}
