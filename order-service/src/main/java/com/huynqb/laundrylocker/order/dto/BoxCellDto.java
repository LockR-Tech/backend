package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;

public record BoxCellDto(
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
        BigDecimal arucoMarkerSizeCm) {
}

