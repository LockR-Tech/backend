package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

/**
 * Lưới ô theo vị trí vật lý — màn hình cảm ứng tủ và dashboard cùng dùng API này.
 * `acceptingOrders` / `orderPauseReason`: như {@link LockerResponse} (tủ phải có KTV phụ trách mới nhận đơn — F3-G16).
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
        List<CellResponse> cells,
        Boolean online,
        Boolean acceptingOrders,
        String orderPauseReason) {

    public LockerLayoutResponse(
            Long lockerId, String code, String name, String status, Boolean landingPad, String landingMarkerId,
            String landingPadStatus, int totalCells, long availableCells, long faultCells, List<CellResponse> cells,
            Boolean online) {
        this(lockerId, code, name, status, landingPad, landingMarkerId, landingPadStatus, totalCells,
                availableCells, faultCells, cells, online, null, null);
    }

    public LockerLayoutResponse(
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
        this(
                lockerId,
                code,
                name,
                status,
                landingPad,
                landingMarkerId,
                landingPadStatus,
                totalCells,
                availableCells,
                faultCells,
                cells,
                null);
    }
}
