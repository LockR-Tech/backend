package com.huynqb.laundrylocker.order.dto.admin;

/// Bản sao BoxLookupResponse của locker-service (/internal/boxes/batch).
public record BoxInfo(
        Long id,
        Long lockerId,
        Integer boxNumber,
        String size,
        String cellType,
        String status,
        Boolean active) {
}