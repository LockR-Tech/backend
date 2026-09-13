package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotNull;

public record AcceptDroneOrderRequest(@NotNull Long droneUnitId) {
}
