package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotNull;

/// Maintenance/admin override: open a box without a customer PIN/QR.
/// Called service-to-service from locker-service's `/api/locker-technician/boxes/{id}/force-open`.
public record ForceUnlockRequest(@NotNull Long lockerId, @NotNull Long boxId, Long actorUserId) {
}
