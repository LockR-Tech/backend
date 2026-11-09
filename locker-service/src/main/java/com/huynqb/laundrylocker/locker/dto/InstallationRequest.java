package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/// Admin lập / sửa hồ sơ lắp đặt tủ (F3-G12). `technicianId` = KTV tủ lắp đặt, đồng thời thành KTV phụ trách tủ.
/// `networkType`: WIFI, 4G hoặc LAN.
public record InstallationRequest(
        @NotNull Long technicianId,
        LocalDateTime scheduledAt,
        @Size(max = 100) String siteContactName,
        @Size(max = 20) String siteContactPhone,
        @Size(max = 100) String powerSource,
        @Pattern(regexp = "(?i)WIFI|4G|LAN") String networkType,
        @Size(max = 255) String networkNote,
        @Size(max = 100) String cabinetModel,
        @Size(max = 100) String cabinetSerial,
        @Size(max = 1000) String notes) {
}
