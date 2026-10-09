package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotNull;

public record BoxItemRequest(
        @NotNull Integer boxNumber,
        String size,
        String status,
        String cellType,
        Integer rowIndex,
        Integer colIndex) {
}
