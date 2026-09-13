package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record DroneMaintenanceLogResponse(
        Long id, Long droneUnitId, Long actorUserId, String note, LocalDateTime createdAt) {
}
