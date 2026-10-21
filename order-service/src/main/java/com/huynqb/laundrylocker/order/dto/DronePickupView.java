package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Việc lấy hàng tận nhà của một đơn drone trong read model.
public record DronePickupView(
        /// SEARCHING | OFFERED | ACCEPTED | PICKED_UP | FAILED | CANCELED; null khi đơn chưa thanh toán.
        String status,
        String address,
        Double latitude,
        Double longitude,
        /// Chỉ dẫn của người gửi cho KTV.
        String note,
        /// Điểm lấy hàng → tủ gửi, mét.
        Integer distanceToSourceLockerMeters,
        Long technicianId,
        String technicianName,
        String technicianPhone,
        /// KTV → điểm lấy hàng lúc được mời, mét.
        Integer technicianDistanceMeters,
        LocalDateTime offeredAt,
        /// Hạn KTV trả lời lời mời; null khi không còn là lời mời.
        LocalDateTime offerExpiresAt,
        LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt,
        /// Mã người gửi đọc cho KTV khi giao kiện. CHỈ trả cho chính người gửi.
        String pickupCode,
        String failureReason,
        String failureNote,
        /// Ghi chú của KTV lúc lấy hàng.
        String technicianNote,
        String pickupPhotoUrl,
        LocalDateTime pickupPhotoCapturedAt) {
}
