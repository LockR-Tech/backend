package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

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
        String description,
        Long assignedByUserId,
        Integer expectedWeightGrams,
        Integer payloadWeightGrams,
        String sealCode,
        Long loadedByUserId,
        LocalDateTime loadedAt,
        LocalDateTime readyToLaunchAt) {
}
