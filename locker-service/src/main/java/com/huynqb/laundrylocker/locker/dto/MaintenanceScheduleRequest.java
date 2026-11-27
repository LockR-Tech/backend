package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/// Tao lich dinh ky (intervalDays >= 1) hoac phieu bao tri 1 lan (intervalDays = 0):
/// dat lockerId (Kiosk) HOAC droneUnitId (Drone).
public record MaintenanceScheduleRequest(
        Long lockerId,
        Long droneUnitId,
        @NotBlank String title,
        @NotNull @Min(0) @Max(365) Integer intervalDays,
        Long assignedTechnicianId,
        String priority,
        String description,
        String checklist,
        LocalDateTime firstDueDate,
        String locationNote,
        String scheduledTimeSlot,
        Boolean putInMaintenance) {

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
                description, checklist, firstDueDate, null, null, null);
    }

    public MaintenanceScheduleRequest(
            Long lockerId,
            Long droneUnitId,
            String title,
            Integer intervalDays,
            Long assignedTechnicianId,
            String priority,
            String description,
            String checklist,
            LocalDateTime firstDueDate,
            String locationNote,
            String scheduledTimeSlot) {
        this(lockerId, droneUnitId, title, intervalDays, assignedTechnicianId, priority,
                description, checklist, firstDueDate, locationNote, scheduledTimeSlot, null);
    }
}

