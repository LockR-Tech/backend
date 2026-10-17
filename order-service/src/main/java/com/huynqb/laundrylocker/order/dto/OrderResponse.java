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
        String receiverEmail,
        String customerNote,
        String deliveryAddress,
        Integer rentalDurationHours,
        // Mã đúng nhưng tạm chưa mở được ô (null = mở được) — xem OrderAccessPolicy.
        String accessBlockReason,
        BigDecimal pickupOvertimeFee,
        /// Đã trả bao nhiêu và còn phải trả bao nhiêu.
        ///
        /// Gia hạn thuê tủ và phí quá hạn cộng thêm vào `totalPrice` rồi đặt lại
        /// `paymentStatus = UNPAID`. Client phải thu `amountDue`, không phải
        /// `totalPrice`, nếu không khách trả lại cả phần đã thanh toán trước đó.
        BigDecimal paidAmount,
        BigDecimal amountDue,
        // Phần chi tiết cho màn "Đơn của tôi" — cùng nguồn với chi tiết đơn phía người nhận
        // (OrderDetailViews) để hai màn hiện giống nhau.
        /// Các mốc đã xảy ra, cũ → mới.
        List<OrderMilestone> milestones,
        /// Khai báo kiện (mô tả, cân nặng, kích thước, loại hàng, giá trị, dễ vỡ); null khi không khai.
        OrderParcelView parcel,
        LocalDateTime paidAt,
        LocalDateTime dropOpenedAt,
        /// Thời điểm người gửi dự kiến người nhận tới lấy (nếu khai).
        LocalDateTime intendedReceiveAt,
        /// Người được ủy quyền lấy hộ; receiverName/receiverPhone cũng đã đổi sang người này.
        String delegateName,
        String delegatePhone,
        String delegateNote,
        LocalDateTime delegatedAt,
        /// Tủ gửi / tủ nhận của đơn drone (lockerId của đơn drone là tủ nhận).
        Long sourceLockerId,
        Long destinationLockerId,
        Integer routeDistanceMeters,
        LocalDateTime parcelDroppedAt,
        LocalDateTime parcelReturnedAt,
        /// Đơn drone đang tạm dừng / đã giao không hoàn tất: mã + nhãn trong DroneFailureReasons.
        String deliveryBlockReason,
        String deliveryBlockReasonLabel,
        LocalDateTime deliveryBlockedAt,
        String failureReason,
        String failureReasonLabel,
        LocalDateTime failedAt,
        /// Điểm thưởng đã dùng cho đơn và số tiền được giảm nhờ điểm (sau mã giảm giá).
        Integer pointsUsed,
        BigDecimal pointsDiscount) {
}
