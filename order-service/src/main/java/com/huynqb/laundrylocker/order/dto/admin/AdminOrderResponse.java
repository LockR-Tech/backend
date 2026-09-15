package com.huynqb.laundrylocker.order.dto.admin;

import com.huynqb.laundrylocker.order.dto.OrderDetailResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Đơn hàng cho trang admin: toàn bộ field của OrderResponse (cùng tên, cùng nghĩa)
 * + các cột entity OrderResponse bỏ sót + dữ liệu ghép từ user/locker/store/payment-service.
 *
 * <p>Object ghép (customer, locker, store, payment…) là null khi không có dữ liệu hoặc
 * service nguồn lỗi — danh sách vẫn trả về. Thời gian là LocalDateTime theo múi giờ JVM
 * (UTC trên container), web tự đổi sang Asia/Ho_Chi_Minh.
 */
public record AdminOrderResponse(
        // ---- giống OrderResponse ----
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
        // ---- cột entity OrderResponse chưa có ----
        Long receiverUserId,
        String receiverPhone,
        String receiverName,
        Long destinationLockerId,
        Long reservedBoxId,
        String fulfillmentMode,
        LocalDateTime paidAt,
        Integer parcelWeightGrams,
        BigDecimal reservationFee,
        BigDecimal storagePrice,
        BigDecimal shippingFee,
        LocalDateTime pinCodeIssuedAt,
        LocalDateTime receiveAt,
        LocalDateTime intendedReceiveAt,
        Integer rentalDurationHours,
        LocalDateTime lastReminderAt,
        String description,
        String customerNote,
        String staffNote,
        Integer cancelReason,
        String deliveryAddress,
        // ---- dữ liệu ghép ----
        Person customer,
        Receiver receiver,
        LockerRef locker,
        LockerRef destinationLocker,
        StoreRef store,
        Integer sendBoxNumber,
        Integer receiveBoxNumber,
        Integer reservedBoxNumber,
        Fees fees,
        PaymentInfo payment,
        DroneInfo drone,
        /// Chỉ có ở API chi tiết; null ở danh sách.
        List<TimelineEvent> timeline) {

    public record Person(Long id, String fullName, String phoneNumber, String email, String status) {
    }

    /// name/phone: người nhận ghi trên đơn (SEND/ủy quyền). account*: tài khoản người nhận nếu có.
    public record Receiver(
            Long userId, String name, String phone, String accountFullName, String accountPhoneNumber,
            String accountEmail) {
    }

    public record LockerRef(Long id, String code, String name, String address, Long storeId, String status) {
    }

    public record StoreRef(Long id, String name, String address, String contactPhone) {
    }

    /// overtimeFee = extraFee (phí quá hạn là khoản duy nhất cộng vào extraFee);
    /// basePrice = totalPrice − extraFee.
    public record Fees(
            BigDecimal originalPrice,
            BigDecimal reservationFee,
            BigDecimal storagePrice,
            BigDecimal shippingFee,
            BigDecimal extraFee,
            BigDecimal overtimeFee,
            BigDecimal discount,
            BigDecimal basePrice,
            BigDecimal totalPrice) {
    }

    /// Từ payment-service. paymentCount=0 ⇒ chưa có giao dịch; cả object null ⇒ không tra được.
    public record PaymentInfo(
            int paymentCount,
            Long latestPaymentId,
            String latestMethod,
            String latestStatus,
            BigDecimal latestAmount,
            LocalDateTime latestCreatedAt,
            BigDecimal paidAmount,
            String lastPaidMethod,
            LocalDateTime lastPaidAt,
            BigDecimal refundedAmount,
            BigDecimal outstandingAmount) {
    }

    public record DroneInfo(
            Long missionId,
            String missionStatus,
            String deliveryStage,
            Long droneUnitId,
            String droneCode,
            Long sourceLockerId,
            LockerRef sourceLocker,
            Long destinationLockerId,
            Long assignedByUserId,
            LocalDateTime readyToLaunchAt,
            LocalDateTime launchingAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record TimelineEvent(
            String oldStatus, String newStatus, Long changedByUserId, String changedByName, String note,
            LocalDateTime createdAt) {
    }
}
