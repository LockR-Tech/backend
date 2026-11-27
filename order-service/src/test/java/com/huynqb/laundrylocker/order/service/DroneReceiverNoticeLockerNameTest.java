package com.huynqb.laundrylocker.order.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Thông báo người nhận đơn drone ghi "giao tới tủ <tên>" — tên tủ bắt đầu bằng "Tủ" không được thành "tủ Tủ …"
/// (thấy trên production 2026-11-27: "giao tới tủ Tủ demo capstone 3x3 + vali").
class DroneReceiverNoticeLockerNameTest {

    @Test
    void leadingTuIsDroppedOnce() {
        assertEquals("demo capstone 3x3 + vali", DroneOrderBookingService.lockerNameAfterTu("Tủ demo capstone 3x3 + vali"));
        assertEquals("Vincom Đồng Khởi – Sảnh B1", DroneOrderBookingService.lockerNameAfterTu("  tủ Vincom Đồng Khởi – Sảnh B1 "));
    }

    @Test
    void namesWithoutTuStayTheSame() {
        assertEquals("Locker2309", DroneOrderBookingService.lockerNameAfterTu("Locker2309"));
        assertEquals("Tủlạnh", DroneOrderBookingService.lockerNameAfterTu("Tủlạnh"));
        assertEquals("", DroneOrderBookingService.lockerNameAfterTu(null));
    }
}
