package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderCode,
        Long userId,
        Long receiverId,
        Long lockerId,
        Long sendBoxId,
        Long receiveBoxId,
        Long storeId,
        Long staffId,
        String type,
        String serviceCategory,
        String status,
        String deliveryStage,
        String pinCode,
        String qrToken,
        BigDecimal actualWeight,
        String weightUnit,
        BigDecimal extraFee,
        BigDecimal discount,
        BigDecimal totalPrice,
        BigDecimal originalPrice,
        String promotionCode,
        String appliedPromotionCodes,
        String nextAction,
        String nextActionMessage,
        Boolean paymentRequired,
        String paymentStatus,
        Boolean overtime,
        LocalDateTime pickupDeadline,
        LocalDateTime returnedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderDetailResponse> orderDetails,
        // Có sẵn trên entity + được ghi từ OrderCreateRequest/DelegateRequest nhưng trước
        // đây không trả về cho khách hàng — AdminOrderResponse có, OrderResponse thì không.
        // Khách gửi hàng (SEND) không thấy lại tên/SĐT người nhận mình vừa nhập; khách thuê
        // tủ (RENTAL) không thấy lại số giờ đã đặt; ghi chú lúc tạo đơn cũng mất tăm.
        String receiverName,
        String receiverPhone,
        String customerNote,
        String deliveryAddress,
        Integer rentalDurationHours,
        // Mã đúng nhưng tạm chưa mở được ô (null = mở được) — xem OrderAccessPolicy.
        String accessBlockReason) {
}
