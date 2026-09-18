package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/// Tao lich dinh ky: dat lockerId (lich cho tu) HOAC droneUnitId (lich cho drone).
public record MaintenanceScheduleRequest(
        Long lockerId,
        Long droneUnitId,
        @NotBlank String title,
        @NotNull @Min(1) @Max(365) Integer intervalDays,
        Long assignedTechnicianId,
        String priority,
        String description,
        String checklist,
        LocalDateTime firstDueDate,
        String locationNote,
        String scheduledTimeSlot) {

    public MaintenanceScheduleRequest(
            Long lockerId,
            Long droneUnitId,
            String title,
            Integer intervalDays,
            Long assignedTechnicianId,
            String priority,
            String description,
            String checklist,
            LocalDateTime firstDueDate) {
        this(lockerId, droneUnitId, title, intervalDays, assignedTechnicianId, priority,
                description, checklist, firstDueDate, null, null);
    }
}

