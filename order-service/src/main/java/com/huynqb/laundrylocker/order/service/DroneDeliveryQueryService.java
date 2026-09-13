package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DroneDeliveryQueryService {

    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;

    @Transactional(readOnly = true)
    public DroneDeliveryOrderResponse get(Long orderId, Long userId) {
        LockerOrder order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!"DRONE_DELIVERY".equals(order.getType())) {
            throw new BusinessException("DRONE_ORDER_REQUIRED", "Order is not a drone delivery order");
        }
        if (userId != null && !userId.equals(order.getUserId()) && !userId.equals(order.getReceiverUserId())) {
            throw new BusinessException("ORDER_FORBIDDEN", "Order does not belong to user");
        }
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        return new DroneDeliveryOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getUserId(),
                order.getReceiverUserId() != null ? order.getReceiverUserId() : order.getReceiverId(),
                order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId(),
                order.getReservedBoxId() != null ? order.getReservedBoxId() : order.getSendBoxId(),
                order.getType(),
                order.getStatus(),
                order.getDeliveryStage(),
                order.getPaymentStatus(),
                order.getParcelWeightGrams(),
                order.getDescription(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getFulfillmentMode(),
                mission == null ? null : mission.getId(),
                mission == null ? null : mission.getStatus(),
                mission == null ? null : mission.getDroneUnitId(),
                mission == null ? null : mission.getDroneCode(),
                mission == null ? null : mission.getSourceLockerId(),
                etaMinutes(order.getDeliveryStage()));
    }

    private Integer etaMinutes(String stage) {
        return switch (stage == null ? "" : stage.toUpperCase()) {
            case "AWAITING_DISPATCH", "ACCEPTED" -> 10;
            case "LAUNCHING" -> 9;
            case "DEPARTED" -> 8;
            case "EN_ROUTE" -> 6;
            case "APPROACHING" -> 2;
            case "ARRIVED", "READY_FOR_PICKUP" -> 0;
            default -> null;
        };
    }
}
