package com.huynqb.laundrylocker.order.dto;

public record IncidentInspectionStatus(
        Long id,
        String status,
        Long assignedToUserId,
        String resolutionNote) {
}
