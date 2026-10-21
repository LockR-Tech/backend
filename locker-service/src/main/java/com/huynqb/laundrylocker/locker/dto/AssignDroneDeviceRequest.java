package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotNull;

public record AssignDroneDeviceRequest(
        @NotNull Long droneId) {
}

