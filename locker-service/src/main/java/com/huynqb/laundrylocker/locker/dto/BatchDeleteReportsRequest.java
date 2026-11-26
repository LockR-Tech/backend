package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

/// Xoá mềm nhiều phiếu sự cố: theo danh sách id hoặc toàn bộ một nhóm (KIOSK / DRONE), luôn kèm lý do.
public record BatchDeleteReportsRequest(
        List<Long> reportIds,
        Boolean deleteAll,
        String category,
        String reason) {

    public BatchDeleteReportsRequest(List<Long> reportIds, String reason) {
        this(reportIds, false, null, reason);
    }

    public BatchDeleteReportsRequest withCategory(String newCategory) {
        return new BatchDeleteReportsRequest(reportIds, deleteAll, newCategory, reason);
    }
}
