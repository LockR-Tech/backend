package com.huynqb.laundrylocker.locker.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/// Hồ sơ lắp đặt tủ cho admin web và app KTV (F3-G12).
/// `readyToSubmit` + `missing`: còn thiếu gì để KTV gửi duyệt / admin duyệt.
/// `iotAvailable` = false khi không đọc được dữ liệu tủ từ iot-service (các bước tự động giữ kết quả cũ).
public record InstallationResponse(
        Long id,
        Long lockerId,
        String lockerCode,
        String lockerName,
        String lockerStatus,
        String status,
        Long technicianId,
        LocalDateTime scheduledAt,
        String siteContactName,
        String siteContactPhone,
        String powerSource,
        String networkType,
        String networkNote,
        String cabinetModel,
        String cabinetSerial,
        String notes,
        String returnReason,
        String cancelReason,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime approvedAt,
        LocalDateTime cancelledAt,
        String controllerMac,
        Boolean controllerOnline,
        boolean iotAvailable,
        boolean readyToSubmit,
        List<String> missing,
        List<Check> checks,
        List<Photo> photos) {

    public record Check(
            String item,
            String label,
            String status,
            boolean auto,
            boolean photoRequired,
            boolean notApplicableAllowed,
            String verifiedBy,
            String note,
            JsonNode detail,
            Long checkedBy,
            LocalDateTime checkedAt,
            int photoCount) {
    }

    public record Photo(
            Long id,
            String item,
            String url,
            String thumbnailUrl,
            String caption,
            Double latitude,
            Double longitude,
            LocalDateTime capturedAt,
            Long uploadedByUserId,
            LocalDateTime createdAt) {
    }
}
