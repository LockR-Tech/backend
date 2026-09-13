package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotNull;

public record BoxRequest(
        @NotNull Long lockerId,
        @NotNull Integer boxNumber,
        String size,
        String status,
        String cellType,
        Integer rowIndex,
        Integer colIndex) {
}
