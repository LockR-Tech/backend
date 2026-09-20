package com.huynqb.laundrylocker.order.dto;

public record DroneStatusTransitionRequest(String expectedStatus, String status, String reason) {
}
