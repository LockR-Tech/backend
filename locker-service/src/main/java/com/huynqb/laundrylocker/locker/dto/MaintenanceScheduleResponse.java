package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record MaintenanceScheduleResponse(
        Long id,
        Long lockerId,
        String lockerName,
        String lockerCode,
        Long droneUnitId,
        String droneCode,
        String title,
        Integer intervalDays,
        LocalDateTime lastDoneAt,
        LocalDateTime nextDueAt,
        Boolean active,
        Boolean due,
        Long assignedTechnicianId,
        String assignedTechnicianName,
        String priority,
        String description,
        String checklist,
        Long storeId,
        String address,
        String locationNote,
        String scheduledTimeSlot) {

    public MaintenanceScheduleResponse(
            Long id,
            Long lockerId,
            String lockerName,
            String lockerCode,
            Long droneUnitId,
            String droneCode,
            String title,
            Integer intervalDays,
            LocalDateTime lastDoneAt,
            LocalDateTime nextDueAt,
            Boolean active,
            Boolean due) {
        this(id, lockerId, lockerName, lockerCode, droneUnitId, droneCode, title, intervalDays,
                lastDoneAt, nextDueAt, active, due, null, null, "NORMAL", null, null, null, null, null, null);
    }

    public MaintenanceScheduleResponse(
            Long id,
            Long lockerId,
            String lockerName,
            String lockerCode,
            Long droneUnitId,
            String droneCode,
            String title,
            Integer intervalDays,
            LocalDateTime lastDoneAt,
            LocalDateTime nextDueAt,
            Boolean active,
            Boolean due,
            Long assignedTechnicianId,
            String assignedTechnicianName,
            String priority,
            String description,
            String checklist,
            Long storeId,
            String address) {
        this(id, lockerId, lockerName, lockerCode, droneUnitId, droneCode, title, intervalDays,
                lastDoneAt, nextDueAt, active, due, assignedTechnicianId, assignedTechnicianName,
                priority, description, checklist, storeId, address, null, null);
    }
}

