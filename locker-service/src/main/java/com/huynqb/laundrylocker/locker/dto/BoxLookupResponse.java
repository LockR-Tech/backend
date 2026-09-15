package com.huynqb.laundrylocker.locker.dto;

/// Thông tin tối thiểu của một ô để service khác hiển thị số ô / loại ô.
public record BoxLookupResponse(
        Long id,
        Long lockerId,
        Integer boxNumber,
        String size,
        String cellType,
        String status,
        Boolean active) {
}
