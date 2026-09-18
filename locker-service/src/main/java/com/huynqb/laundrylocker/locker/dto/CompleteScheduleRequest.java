package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

/// DTO gửi lên khi hoàn tất ca kiểm tra định kỳ (KTV hoặc Admin).
public record CompleteScheduleRequest(
        Long technicianId,
        String technicianName,
        String status, // PASSED, ATTENTION, DEFECT_DETECTED, FAILED
        String note,
        List<String> photoUrls,
        String checklistResults,
        Boolean autoCreateReport,
        Long faultBoxId,
        String faultReason) {
}
