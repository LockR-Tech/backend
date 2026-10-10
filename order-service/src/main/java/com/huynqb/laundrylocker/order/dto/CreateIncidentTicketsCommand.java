package com.huynqb.laundrylocker.order.dto;

public record CreateIncidentTicketsCommand(
        Long incidentId,
        String incidentCode,
        Long orderId,
        String orderCode,
        Long droneUnitId,
        String droneCode,
        Long reporterUserId,
        String reason,
        Double latitude,
        Double longitude,
        Double gpsAccuracyM,
        String cameraSnapshotUrl) {
}

