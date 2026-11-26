package com.huynqb.laundrylocker.notification.dto;

import java.util.List;

/// Thẻ số liệu + danh sách loại của trang Thông báo admin (đã bỏ thông báo ví cũ).
///
/// @param averageReadMinutes phút trung bình từ lúc tạo tới lúc đọc; null khi chưa có thông báo nào được đọc
public record AdminNotificationStats(
        long total,
        long unread,
        long read,
        Long averageReadMinutes,
        List<TypeCount> types) {

    public record TypeCount(String type, long count) {
    }
}
