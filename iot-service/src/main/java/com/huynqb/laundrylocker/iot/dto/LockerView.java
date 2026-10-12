package com.huynqb.laundrylocker.iot.dto;

/// Phần của `LockerResponse` (locker-service `/internal/lockers/batch`) mà iot-service cần:
/// tên/mã tủ cho danh sách thiết bị và KTV phụ trách để lọc thiết bị theo người xem.
public record LockerView(Long id, String code, String name, Long assignedTechnicianId) {
}
