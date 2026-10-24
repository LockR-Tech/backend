package com.huynqb.laundrylocker.iot.dto;

import java.util.List;

/// Phần của `LockerLayoutResponse` (locker-service `/internal/lockers/{id}/layout`) mà
/// iot-service cần để đổi ô ↔ vị trí trên bộ điều khiển tủ (ADR-0008).
public record LockerLayoutView(Long lockerId, String code, String name, List<Cell> cells) {

    /// `status` (AVAILABLE/RESERVED/OCCUPIED/FAULT…) dùng để không mở thử ô đang giữ hàng của đơn thật.
    public record Cell(Long id, Integer boxNumber, Integer rowIndex, Integer colIndex, String status) {

        public Cell(Long id, Integer boxNumber, Integer rowIndex, Integer colIndex) {
            this(id, boxNumber, rowIndex, colIndex, null);
        }

        /// Vị trí ô trên bộ điều khiển, đếm từ 0: luôn là `boxNumber − 1`.
        public Integer slotIndex() {
            return boxNumber == null ? null : boxNumber - 1;
        }
    }
}
