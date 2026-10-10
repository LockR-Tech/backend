package com.huynqb.laundrylocker.locker.dto;

public record IncidentTicketBundleResponse(
        Long inspectionReportId,
        Long inspectionTechnicianId,
        Long recoveryTechnicianId,
        Long recoveryLockerId) {
}
