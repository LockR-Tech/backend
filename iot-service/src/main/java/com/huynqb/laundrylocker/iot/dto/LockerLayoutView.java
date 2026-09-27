package com.huynqb.laundrylocker.iot.dto;

import java.util.List;

/// Phần của `LockerLayoutResponse` (locker-service `/internal/lockers/{id}/layout`) mà
/// iot-service cần để đổi ô ↔ vị trí trên bộ điều khiển tủ (ADR-0008).
public record LockerLayoutView(Long lockerId, String code, String name, List<Cell> cells) {

    public record Cell(Long id, Integer boxNumber, Integer rowIndex, Integer colIndex) {

        /// Vị trí ô trên bộ điều khiển, đếm từ 0: luôn là `boxNumber − 1`.
        public Integer slotIndex() {
            return boxNumber == null ? null : boxNumber - 1;
        }
    }
}
