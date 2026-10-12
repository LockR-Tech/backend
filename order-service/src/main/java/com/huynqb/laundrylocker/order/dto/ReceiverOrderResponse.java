package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Một đơn nhìn từ phía người nhận hàng — dùng cho `GET /api/orders/incoming` (đơn gửi tới tôi)
/// và `GET /api/orders/delegated-to-me` (đơn được ủy quyền cho tôi lấy hộ).
///
/// `pickupPin`/`qrToken` chỉ có khi `readyForPickup = true` và người xem đúng là người được mở ô:
/// đơn đã ủy quyền cho người khác thì người nhận ban đầu không còn thấy mã (mã đã đổi khi ủy quyền).
public record ReceiverOrderResponse(
        Long id,
        String orderCode,
        String type,
        String status,
        /// Chặng giao của đơn drone; null với đơn tủ thường.
        String deliveryStage,
        /// Chủ đơn: người gửi (Hàng đến) hoặc người ủy quyền (Được ủy quyền).
        Long senderUserId,
        String senderName,
        String receiverName,
        String receiverPhone,
        Long lockerId,
        String lockerCode,
        String lockerName,
        String lockerAddress,
        Double lockerLatitude,
        Double lockerLongitude,
        Long boxId,
        /// Số ô in trên tủ; null khi chưa có ô hoặc tra cứu tủ lỗi.
        Integer boxNumber,
        LocalDateTime pickupDeadline,
        boolean overdue,
        /// Hàng đã nằm trong ô, mở được bằng mã: STORING (đơn tủ) / READY_FOR_PICKUP (đơn drone).
        boolean readyForPickup,
        String pickupPin,
        String qrToken,
        String paymentStatus,
        /// Chủ đơn đã ủy quyền lấy hộ.
        boolean delegated,
        /// Chỉ có ở danh sách "Được ủy quyền" (người xem là người lấy hộ).
        String delegateName,
        String delegateNote,
        LocalDateTime delegatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {
}
