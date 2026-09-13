package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/// `lockerId` is optional (nullable): the MQTT status handler fills it from the
/// `cabinet/{lockerId}/locker/{boxId}/status` topic; REST callers may omit it.
public record BoxStatusUpdateRequest(@NotNull Long boxId, @NotBlank String status, Long lockerId) {
}
