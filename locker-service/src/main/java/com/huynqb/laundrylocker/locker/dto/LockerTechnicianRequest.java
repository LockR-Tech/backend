package com.huynqb.laundrylocker.locker.dto;

/// Admin gán KTV tủ phụ trách một tủ; `technicianId = null` để bỏ gán (phiếu mới báo cho mọi KTV tủ).
public record LockerTechnicianRequest(Long technicianId) {
}
