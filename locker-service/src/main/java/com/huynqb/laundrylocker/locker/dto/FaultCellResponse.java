package com.huynqb.laundrylocker.locker.dto;

public record FaultCellResponse(
        Long lockerId,
        String lockerCode,
        String lockerName,
        String lockerAddress,
        Double lockerLatitude,
        Double lockerLongitude,
        Long boxId,
        Integer boxNumber,
        String cellType,
        Integer rowIndex,
        Integer colIndex,
        String faultReason,
        Long openReportId) {
}
