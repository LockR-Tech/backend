package com.huynqb.laundrylocker.order.dto;

/** Thông tin tủ nguồn/đích đủ để hiển thị và mở chỉ đường. */
public record DroneLockerPointResponse(
        Long lockerId,
        String code,
        String name,
        String address,
        Double latitude,
        Double longitude) {
}
