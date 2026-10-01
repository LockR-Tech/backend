package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DroneDeliveryOrderResponse(
        Long orderId,
        String orderCode,
        Long userId,
        Long receiverUserId,
        Long destinationLockerId,
        Long reservedBoxId,
        String type,
        String status,
        String deliveryStage,
        String paymentStatus,
        Integer parcelWeightGrams,
        String description,
        BigDecimal totalPrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String fulfillmentMode,
        Long missionId,
        String missionStatus,
        Long droneUnitId,
        String droneCode,
        Long sourceLockerId,
        Integer etaMinutes,
        Long assignedByUserId,
        Integer payloadWeightGrams,
        String sealCode,
        Long loadedByUserId,
        LocalDateTime loadedAt,
        LocalDateTime readyToLaunchAt,
        LocalDateTime launchingAt,
        LocalDateTime missionCreatedAt,
        LocalDateTime missionUpdatedAt,
        LocalDateTime paidAt,
        LocalDateTime pickupDeadline,
        LocalDateTime completedAt,
        DroneLockerPointResponse sourceLocker,
        DroneLockerPointResponse destinationLocker,
        List<DroneJourneyEventResponse> journeyEvents,
        /// Số ô in trên tủ đích — người dùng không đọc được id nội bộ của ô.
        Integer reservedBoxNumber,
        String customerName,
        String customerPhone,
        String receiverName,
        String receiverPhone,
        String assignedByName,
        String loadedByName,
        String loadingNote,
        Boolean parcelMatched,
        Boolean payloadSecured,
        Boolean compartmentLocked,
        Integer cancelReason,
        String cancelNote,
        /// Ô DRONE tại tủ gửi đang giữ cho kiện này; null sau khi đã nạp lên drone.
        Long sourceBoxId,
        Integer sourceBoxNumber) {

    /** Constructor tương thích cho response ngay sau khi tạo đơn/chưa có mission. */
    public DroneDeliveryOrderResponse(
            Long orderId, String orderCode, Long userId, Long receiverUserId,
            Long destinationLockerId, Long reservedBoxId, String type, String status,
            String deliveryStage, String paymentStatus, Integer parcelWeightGrams,
            String description, BigDecimal totalPrice, LocalDateTime createdAt,
            LocalDateTime updatedAt, String fulfillmentMode, Long missionId,
            String missionStatus, Long droneUnitId, String droneCode,
            Long sourceLockerId, Integer etaMinutes) {
        this(orderId, orderCode, userId, receiverUserId, destinationLockerId,
                reservedBoxId, type, status, deliveryStage, paymentStatus,
                parcelWeightGrams, description, totalPrice, createdAt, updatedAt,
                fulfillmentMode, missionId, missionStatus, droneUnitId, droneCode,
                sourceLockerId, etaMinutes, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
