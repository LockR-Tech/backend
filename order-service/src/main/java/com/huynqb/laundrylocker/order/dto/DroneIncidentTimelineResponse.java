package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

public record DroneIncidentTimelineResponse(
        Long id, String eventType, String fromStatus, String toStatus,
        Long actorUserId, String note, String metadataJson, LocalDateTime createdAt) {
}

