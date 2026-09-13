package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record DroneDeliveryResponse(
        Long id,
        Long lockerId,
        String lockerCode,
        String lockerName,
        String lockerAddress,
        Long boxId,
        Integer boxNumber,
        Long requesterUserId,
        String receiverPhone,
        String description,
        String status,
        Long droneUnitId,
        String droneCode,
        Long dispatchedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
