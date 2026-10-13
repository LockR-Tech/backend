package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/// Chi tiết một đơn nhìn từ phía người nhận hoặc người lấy hộ — `GET /api/orders/incoming/{id}`
/// và `GET /api/orders/delegated-to-me/{id}`. Gồm mọi trường của `ReceiverOrderResponse` (cùng
/// tên, client dùng bản ghi trong danh sách làm dữ liệu ban đầu) cộng phần chi tiết: liên hệ
/// người gửi, kiện hàng, kích cỡ ô, tủ gửi của đơn drone, sự cố, hành trình và các mốc thời gian.
///
/// Không có ghi chú nội bộ, mã phiếu sự cố, mã thanh toán hay khuyến mãi. `pickupPin`/`qrToken`
/// theo đúng quy tắc của danh sách. Email người nhận chỉ trả cho chính người nhận; tên/SĐT/lời
/// nhắn ủy quyền chỉ trả cho người lấy hộ.
public record ReceiverOrderDetailResponse(
        Long id,
        String orderCode,
        String type,
        String status,
        /// Chặng giao của đơn drone; null với đơn tủ thường.
        String deliveryStage,
        /// RECEIVER (đơn gửi tới tôi) | DELEGATE (đơn được ủy quyền cho tôi lấy hộ).
        String viewerRole,
        /// Chủ đơn: người gửi (RECEIVER) hoặc người ủy quyền (DELEGATE).
        Long senderUserId,
        String senderName,
        /// SĐT liên hệ người gửi khai trên đơn, không có thì SĐT tài khoản người gửi.
        String senderPhone,
        /// Chỉ email liên hệ người gửi khai trên đơn (đơn drone), và chỉ trả cho người nhận.
        String senderEmail,
        String receiverName,
        String receiverPhone,
        String receiverEmail,
        Long lockerId,
        String lockerCode,
        String lockerName,
        String lockerAddress,
        Double lockerLatitude,
        Double lockerLongitude,
        Long boxId,
        Integer boxNumber,
        /// Kích cỡ ô (SMALL/MEDIUM/LARGE…) và loại ô (STANDARD/DRONE…) theo locker-service; null khi tra lỗi.
        String boxSize,
        String boxType,
        /// Tủ gửi của đơn drone (kiện bay từ đâu tới); null với đơn tủ thường.
        Long sourceLockerId,
        String sourceLockerCode,
        String sourceLockerName,
        String sourceLockerAddress,
        /// Khoảng cách đường chim bay tủ gửi → tủ nhận, mét.
        Integer routeDistanceMeters,
        LocalDateTime pickupDeadline,
        boolean overdue,
        boolean readyForPickup,
        String pickupPin,
        String qrToken,
        /// Thời điểm người gửi dự kiến người nhận tới lấy (nếu khai).
        LocalDateTime intendedReceiveAt,
        Integer rentalDurationHours,
        String deliveryAddress,
        String paymentStatus,
        boolean delegated,
        String delegateName,
        String delegatePhone,
        String delegateNote,
        LocalDateTime delegatedAt,
        /// Ghi chú người gửi nhập lúc tạo đơn.
        String customerNote,
        /// Khai báo kiện; null khi đơn không khai.
        OrderParcelView parcel,
        /// Đơn drone đang tạm dừng (mã + nhãn trong `DroneFailureReasons`); null khi không bị chặn.
        String deliveryBlockReason,
        String deliveryBlockReasonLabel,
        LocalDateTime deliveryBlockedAt,
        /// Đơn drone kết thúc "giao không hoàn tất"; null với đơn khác.
        String failureReason,
        String failureReasonLabel,
        LocalDateTime failedAt,
        /// Kiện của đơn không giao được đã được trả lại người gửi.
        LocalDateTime parcelReturnedAt,
        /// Phút ước tính drone tới tủ nhận theo chặng; null khi chưa bay hoặc không phải đơn drone.
        Integer etaMinutes,
        /// Người xem mở được màn theo dõi drone (`GET /api/orders/{id}/drone-delivery`).
        boolean droneTrackingAvailable,
        /// Các lần đổi chặng của đơn drone, mới nhất trước — không kèm người thao tác và ghi chú.
        List<DroneJourneyEventResponse> journey,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        /// Các mốc đã xảy ra, cũ → mới.
        List<OrderMilestone> timeline) {
}
