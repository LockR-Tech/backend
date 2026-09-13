package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/// Booking → IoT sync (GAP 1): tells the cabinet a box's lifecycle state
/// changed (RESERVED/OCCUPIED/AVAILABLE/FAULT) so it can mirror the booking
/// on its display. Called service-to-service from locker-service whenever a
/// box is reserved/occupied/released/faulted/cleared. Informational only —
/// not a command that needs a hardware reply, so it never blocks the order
/// flow.
public record BoxStateSyncRequest(
        @NotNull Long lockerId, @NotNull Long boxId, @NotBlank String state, Long orderId) {
}
