package com.huynqb.laundrylocker.order.dto;

public record CellDto(
        Long id,
        Integer boxNumber,
        String size,
        String cellType,
        Integer rowIndex,
        Integer colIndex,
        String status,
        String faultReason) {
}
