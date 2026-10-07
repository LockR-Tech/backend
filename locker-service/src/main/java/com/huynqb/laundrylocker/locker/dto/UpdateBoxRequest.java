package com.huynqb.laundrylocker.locker.dto;

public record UpdateBoxRequest(
        Integer boxNumber,
        String size,
        String cellType,
        Integer rowIndex,
        Integer colIndex,
        String description,
        String status
) {
}
