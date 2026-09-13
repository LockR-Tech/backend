package com.huynqb.laundrylocker.order.dto;

public record DroneMissionResponse(
        Long orderId,
        Long missionId,
        String missionStatus,
        String deliveryStage,
        Long droneUnitId,
        String droneCode,
        Long sourceLockerId,
        Long destinationLockerId,
        Long reservedBoxId,
        String description) {
}
