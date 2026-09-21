package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;

import java.util.List;

/// DTO gửi lên khi hoàn tất ca kiểm tra định kỳ (KTV hoặc Admin).
/// Có `items` ⇒ server tự suy kết quả (một mục FAIL ⇒ FAILED) và bỏ qua `status`/`autoCreateReport`;
/// không có `items` (client cũ) ⇒ dùng `status`.
public record CompleteScheduleRequest(
        Long technicianId,
        String technicianName,
        String status, // PASSED, ATTENTION, DEFECT_DETECTED, FAILED
        String note,
        List<String> photoUrls,
        String checklistResults,
        Boolean autoCreateReport,
        Long faultBoxId,
        String faultReason,
        List<@Valid InspectionItemResult> items) {

    public CompleteScheduleRequest(
            Long technicianId, String technicianName, String status, String note, List<String> photoUrls,
            String checklistResults, Boolean autoCreateReport, Long faultBoxId, String faultReason) {
        this(technicianId, technicianName, status, note, photoUrls, checklistResults, autoCreateReport,
                faultBoxId, faultReason, null);
    }
}
