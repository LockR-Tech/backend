package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record DroneParcelIncidentResponse(
        Long id,
        String incidentCode,
        Long orderId,
        String orderCode,
        Long missionId,
        Long droneUnitId,
        String droneCode,
        Long customerUserId,
        Long reportedByUserId,
        String reason,
        String status,
        String parcelStatus,
        String recoveryStatus,
        String inspectionStatus,
        String redeliveryStatus,
        String compensationStatus,
        String returnFlightStatus,
        Double dropLatitude,
        Double dropLongitude,
        Double gpsAccuracyM,
        String gpsSource,
        Instant telemetryObservedAt,
        boolean telemetryStale,
        String telemetryJson,
        String cameraStatus,
        String cameraSnapshotUrl,
        Long inspectionReportId,
        Long recoveryAssignedToUserId,
        Long recoveryLockerId,
        String recoveryOutcome,
        String parcelCondition,
        String recoveryNote,
        Double recoveredLatitude,
        Double recoveredLongitude,
        Double recoveredGpsAccuracyM,
        LocalDateTime returnedToHubAt,
        Long redeliveryOrderId,
        String compensationReference,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DroneIncidentEvidenceResponse> evidence,
        List<DroneIncidentTimelineResponse> timeline,
        List<DroneIncidentProposalResponse> proposals,
        /// Mức bồi thường gợi ý theo chính sách của đơn (giá trị khai báo × tỉ lệ, chặn trần) —
        /// cùng công thức dùng khi lập đề xuất; null khi đơn tắt chính sách bồi thường sự cố.
        BigDecimal suggestedCompensation) {
}
