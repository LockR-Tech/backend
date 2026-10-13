package com.huynqb.laundrylocker.assistant.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Bản đọc rút gọn của DTO order-service/locker-service. Chỉ khai báo trường trợ lý cần: mã PIN/QR,
/// mã ủy quyền, email, ghi chú tự do… không có ở đây nên Jackson bỏ qua ngay khi đọc JSON — các giá
/// trị đó không bao giờ vào bộ nhớ của assistant-service, càng không tới mô hình.
public final class OrderDtos {

    private OrderDtos() {
    }

    /// Một phần `OrderResponse` (`GET /api/orders/my-orders`) — đơn người hỏi đã tạo. Thời gian là giờ
    /// "trần" theo múi giờ lưu trữ của order-service (UTC trên production).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MyOrder(
            Long id,
            String orderCode,
            String type,
            String status,
            String deliveryStage,
            Long lockerId,
            Long sendBoxId,
            Long receiveBoxId,
            String paymentStatus,
            BigDecimal totalPrice,
            BigDecimal paidAmount,
            BigDecimal amountDue,
            BigDecimal pickupOvertimeFee,
            Boolean overtime,
            LocalDateTime pickupDeadline,
            LocalDateTime completedAt,
            LocalDateTime createdAt,
            String receiverName,
            /// Chỉ dùng để hiện 3 số cuối.
            String receiverPhone,
            Integer rentalDurationHours,
            String nextAction,
            String accessBlockReason) {
    }

    /// Một phần `ReceiverOrderResponse` (`GET /api/orders/incoming`) — đơn người khác gửi cho người hỏi.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncomingOrder(
            Long id,
            String orderCode,
            String type,
            String status,
            String deliveryStage,
            String senderName,
            String lockerName,
            String lockerAddress,
            Integer boxNumber,
            LocalDateTime pickupDeadline,
            boolean overdue,
            boolean readyForPickup,
            String paymentStatus,
            boolean delegated,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {
    }

    /// Một phần `LockerResponse` (`GET /internal/lockers/batch`).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LockerInfo(Long id, String name, String address) {
    }

    /// Một phần `BoxLookupResponse` (`GET /internal/boxes/batch`).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoxInfo(Long id, Integer boxNumber) {
    }
}
