package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record DroneUnitResponse(
        Long id,
        Long lockerId,
        String lockerCode,
        String lockerName,
        String code,
        String status,
        Integer batteryPercent,
        String faultReason,
        Long assignedTechnicianId,
        String assignedTechnicianName,
        LocalDateTime lastChargedAt,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
