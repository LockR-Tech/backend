package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/// Maintenance/admin override: open a box without a customer PIN/QR.
/// Called service-to-service from locker-service's `/api/locker-technician/boxes/{id}/force-open`.
/// `reason`/`note` (KTV nhập trên app) được ghi vào `box_access_logs.message`; web admin cũ không gửi.
public record ForceUnlockRequest(
        @NotNull Long lockerId,
        @NotNull Long boxId,
        Long actorUserId,
        @Size(max = 255) String reason,
        @Size(max = 1000) String note) {

    public ForceUnlockRequest(Long lockerId, Long boxId, Long actorUserId) {
        this(lockerId, boxId, actorUserId, null, null);
    }
}
