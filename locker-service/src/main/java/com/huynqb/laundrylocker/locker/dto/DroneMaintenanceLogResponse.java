package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

/// `actorName`: họ tên người ghi dòng nhật ký (tra user-service), null khi không tra được hoặc do hệ thống ghi.
public record DroneMaintenanceLogResponse(
        Long id, Long droneUnitId, Long actorUserId, String note, LocalDateTime createdAt, String actorName) {
}
