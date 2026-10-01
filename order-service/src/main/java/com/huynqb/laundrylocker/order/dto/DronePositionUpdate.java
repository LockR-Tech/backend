package com.huynqb.laundrylocker.order.dto;

/// Bản sao `DronePositionRequest` của notification-service.
public record DronePositionUpdate(
        String status,
        Double lat,
        Double lng,
        Double heading,
        Integer etaMinutes,
        Double speed,
        Integer battery,
        Long ts) {
}
