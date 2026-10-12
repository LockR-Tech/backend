package com.huynqb.laundrylocker.order.dto;

/// Ai được theo dõi một đơn (nội bộ, cho notification-service): chủ đơn và tài khoản người nhận
/// (null khi người nhận chưa có tài khoản Lock.R).
public record OrderParticipantsResponse(Long orderId, Long userId, Long receiverUserId) {
}
