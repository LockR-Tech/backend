package com.huynqb.laundrylocker.order.dto;

public record CellDto(
        Long id,
        Integer boxNumber,
        String size,
        String cellType,
        Integer rowIndex,
        Integer colIndex,
        String status,
        String faultReason,
        Long lockerId,
        Boolean active) {

    public CellDto(
            Long id,
            Integer boxNumber,
            String size,
            String cellType,
            Integer rowIndex,
            Integer colIndex,
            String status,
            String faultReason,
            Long lockerId) {
        this(id, boxNumber, size, cellType, rowIndex, colIndex, status, faultReason, lockerId, true);
    }

    public CellDto(
            Long id,
            Integer boxNumber,
            String size,
            String cellType,
            Integer rowIndex,
            Integer colIndex,
            String status,
            String faultReason) {
        this(id, boxNumber, size, cellType, rowIndex, colIndex, status, faultReason, null, true);
    }
}
