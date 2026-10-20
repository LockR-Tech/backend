package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

public record BatchDeleteReportsRequest(
        List<Long> reportIds,
        Boolean deleteAll,
        String category) {
    public BatchDeleteReportsRequest(List<Long> reportIds) {
        this(reportIds, false, null);
    }
}
