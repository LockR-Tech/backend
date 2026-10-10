package com.huynqb.laundrylocker.order.dto;

public record IncidentTicketBundle(
        Long inspectionReportId,
        Long inspectionTechnicianId,
        Long recoveryTechnicianId,
        Long recoveryLockerId) {
}

