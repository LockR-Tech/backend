package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MaintenanceInspectionLogResponse(
        Long id,
        Long scheduleId,
        Long lockerId,
        String lockerName,
        String lockerCode,
        Long droneUnitId,
        String droneCode,
        Long technicianId,
        String technicianName,
        String status,
        String note,
        List<String> photoUrls,
        String checklistResults,
        Long createdReportId,
        LocalDateTime createdAt) {
}
