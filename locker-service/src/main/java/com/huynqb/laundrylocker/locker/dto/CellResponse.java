package com.huynqb.laundrylocker.locker.dto;

import com.huynqb.laundrylocker.locker.model.CellType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin một ô tủ trong lưới vật lý — dùng cho API layout và màn hình mobile.
 */
public record CellResponse(
        Long id,
        Integer boxNumber,
        String size,
        String cellType,
        Integer rowIndex,
        Integer colIndex,
        String status,
        String faultReason,
        Boolean doorOpen,
        String hwState,
        Long lockerId,
        String arucoDictionary,
        Integer arucoMarkerId,
        BigDecimal arucoMarkerSizeCm,
        LocalDateTime arucoAssignedAt,
        String arucoAssignedBy) {

    public CellResponse(
            Long id,
            Integer boxNumber,
            String size,
            String cellType,
            Integer rowIndex,
            Integer colIndex,
            String status,
            String faultReason,
            Boolean doorOpen,
            String hwState,
            Long lockerId) {
        this(id, boxNumber, size, cellType, rowIndex, colIndex, status, faultReason, doorOpen, hwState, lockerId,
                null, null, null, null, null);
    }

    public CellResponse(
            Long id,
            Integer boxNumber,
            String size,
            String cellType,
            Integer rowIndex,
            Integer colIndex,
            String status,
            String faultReason,
            Boolean doorOpen,
            String hwState) {
        this(id, boxNumber, size, cellType, rowIndex, colIndex, status, faultReason, doorOpen, hwState, null,
                null, null, null, null, null);
    }

    public CellResponse(
            Long id,
            Integer boxNumber,
            String size,
            String cellType,
            Integer rowIndex,
            Integer colIndex,
            String status,
            String faultReason) {
        this(id, boxNumber, size, cellType, rowIndex, colIndex, status, faultReason, null, null, null,
                null, null, null, null, null);
    }
}
