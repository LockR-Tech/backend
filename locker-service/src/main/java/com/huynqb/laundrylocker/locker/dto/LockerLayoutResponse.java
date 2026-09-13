package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

/**
 * Lưới ô theo vị trí vật lý — màn hình cảm ứng tủ và dashboard cùng dùng API này.
 */
public record LockerLayoutResponse(
        Long lockerId,
        String code,
        String name,
        String status,
        Boolean landingPad,
        String landingMarkerId,
        String landingPadStatus,
        int totalCells,
        long availableCells,
        long faultCells,
        List<CellResponse> cells) {
}
