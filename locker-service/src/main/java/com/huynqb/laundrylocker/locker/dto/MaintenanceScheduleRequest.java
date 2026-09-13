package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/// Tao lich dinh ky: dat lockerId (lich cho tu) HOAC droneUnitId (lich cho drone).
public record MaintenanceScheduleRequest(
        Long lockerId,
        Long droneUnitId,
        @NotBlank String title,
        @NotNull @Min(1) @Max(365) Integer intervalDays) {
}
