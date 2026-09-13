package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

public record PickupResponse(Long orderId, String status, LocalDateTime completedAt, String message) {
}
