package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.dto.DroneJourneyEventResponse;
import com.huynqb.laundrylocker.order.dto.DroneLockerPointResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-model duy nhất cho màn theo dõi của khách, điều phối viên và admin. */
@Service
@RequiredArgsConstructor
public class DroneDeliveryQueryService {

    private static final List<String> TERMINAL_ORDER_STATUSES = List.of("COMPLETED", "CANCELED", "EXPIRED");

    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final AdminReferenceResolver references;

    @Transactional(readOnly = true)
    public DroneDeliveryOrderResponse get(Long orderId, Long userId) {
        LockerOrder order = requireDroneOrder(orderId);
        if (userId == null || (!userId.equals(order.getUserId()) && !userId.equals(order.getReceiverUserId()))) {
            throw new BusinessException("ORDER_FORBIDDEN", "Order does not belong to user");
        }
        return build(order);
    }

    @Transactional(readOnly = true)
    public DroneDeliveryOrderResponse getForTechnician(Long orderId, Long userId) {
        LockerOrder order = requireDroneOrder(orderId);
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        if (mission != null && !Objects.equals(userId, mission.getAssignedByUserId())) {
            throw new BusinessException("DRONE_MISSION_NOT_ASSIGNED_TO_USER",
                    "Only the assigned drone technician may view this mission detail");
        }
        return buildAll(List.of(new Row(order, mission))).getFirst();
    }

    @Transactional(readOnly = true)
    public DroneDeliveryOrderResponse getForAdmin(Long orderId) {
        return build(requireDroneOrder(orderId));
    }

    /** Nhiệm vụ chưa kết thúc, mới nhất trước. KTV chỉ thấy hàng chờ và nhiệm vụ của mình. */
    @Transactional(readOnly = true)
    public List<DroneDeliveryOrderResponse> operations(String deliveryStage, Long userId, boolean admin) {
        return operations(deliveryStage, userId, admin, false);
    }

    /**
     * Như trên; {@code includeFinished} trả thêm đơn đã hoàn tất/huỷ/quá hạn để admin
     * xem lại hành trình. Luôn sắp theo lần cập nhật gần nhất — đơn vừa đổi chặng lên đầu.
     */
    @Transactional(readOnly = true)
    public List<DroneDeliveryOrderResponse> operations(
            String deliveryStage, Long userId, boolean admin, boolean includeFinished) {
        List<LockerOrder> orders = includeFinished
                ? orderRepository.findByTypeOrderByUpdatedAtDesc("DRONE_DELIVERY")
                : orderRepository.findByTypeAndStatusNotInOrderByUpdatedAtDesc("DRONE_DELIVERY", TERMINAL_ORDER_STATUSES);
        List<Row> rows = orders.stream()
                .filter(order -> !StringUtils.hasText(deliveryStage)
                        || deliveryStage.equalsIgnoreCase(order.getDeliveryStage()))
                .map(order -> new Row(order, missionRepository.findByOrderId(order.getId()).orElse(null)))
                .filter(row -> admin || row.mission() == null
                        || Objects.equals(userId, row.mission().getAssignedByUserId()))
                .toList();
        return buildAll(rows);
    }

    private LockerOrder requireDroneOrder(Long orderId) {
        LockerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!"DRONE_DELIVERY".equals(order.getType())) {
            throw new BusinessException("DRONE_ORDER_REQUIRED", "Order is not a drone delivery order");
        }
        return order;
    }

    private DroneDeliveryOrderResponse build(LockerOrder order) {
        return buildAll(List.of(new Row(order, missionRepository.findByOrderId(order.getId()).orElse(null))))
                .getFirst();
    }

    /// Tra tủ, ô và người dùng một lần cho cả danh sách (không gọi Feign theo từng đơn).
    /// Tra cứu lỗi thì các field tên để null, màn theo dõi vẫn hiện được chặng và mốc giờ.
    private List<DroneDeliveryOrderResponse> buildAll(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> lockerIds = new HashSet<>();
        Set<Long> boxIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        List<List<OrderStatusHistory>> histories = new ArrayList<>(rows.size());
        for (Row row : rows) {
            LockerOrder order = row.order();
            DroneMission mission = row.mission();
            lockerIds.add(sourceLockerId(order, mission));
            lockerIds.add(destinationLockerId(order));
            boxIds.add(boxId(order));
            boxIds.add(order.getSourceBoxId());
            userIds.add(order.getUserId());
            userIds.add(receiverUserId(order));
            if (mission != null) {
                userIds.add(mission.getAssignedByUserId());
                userIds.add(mission.getLoadedByUserId());
            }
            List<OrderStatusHistory> history =
                    historyRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId());
            history.forEach(h -> userIds.add(h.getChangedByUserId()));
            histories.add(history);
        }
        Lookup<LockerInfo> lockers = references.lockers(lockerIds);
        Lookup<BoxInfo> boxes = references.boxes(boxIds);
        Lookup<UserSummary> users = references.users(userIds);

        List<DroneDeliveryOrderResponse> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            result.add(toResponse(rows.get(i), histories.get(i), lockers, boxes, users));
        }
        return result;
    }

    private DroneDeliveryOrderResponse toResponse(
            Row row,
            List<OrderStatusHistory> history,
            Lookup<LockerInfo> lockers,
            Lookup<BoxInfo> boxes,
            Lookup<UserSummary> users) {
        LockerOrder order = row.order();
        DroneMission mission = row.mission();
        Long sourceId = sourceLockerId(order, mission);
        Long destinationId = destinationLockerId(order);
        Long boxId = boxId(order);
        Long receiverUserId = receiverUserId(order);
        BoxInfo box = boxes.get(boxId);
        BoxInfo sourceBox = boxes.get(order.getSourceBoxId());
        UserSummary customer = users.get(order.getUserId());
        UserSummary receiver = users.get(receiverUserId);
        List<DroneJourneyEventResponse> events = history.stream()
                .map(h -> new DroneJourneyEventResponse(
                        h.getId(), h.getOldStatus(), h.getNewStatus(), h.getChangedByUserId(),
                        name(users.get(h.getChangedByUserId())), h.getNote(), h.getCreatedAt()))
                .toList();

        return new DroneDeliveryOrderResponse(
                order.getId(), order.getOrderCode(), order.getUserId(), receiverUserId,
                destinationId, boxId,
                order.getType(), order.getStatus(), order.getDeliveryStage(), order.getPaymentStatus(),
                order.getParcelWeightGrams(), order.getDescription(), order.getTotalPrice(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getFulfillmentMode(),
                mission == null ? null : mission.getId(),
                mission == null ? null : mission.getStatus(),
                mission == null ? null : mission.getDroneUnitId(),
                mission == null ? null : mission.getDroneCode(),
                sourceId, etaMinutes(order.getDeliveryStage()),
                mission == null ? null : mission.getAssignedByUserId(),
                mission == null ? null : mission.getPayloadWeightGrams(),
                mission == null ? null : mission.getSealCode(),
                mission == null ? null : mission.getLoadedByUserId(),
                mission == null ? null : mission.getLoadedAt(),
                mission == null ? null : mission.getReadyToLaunchAt(),
                mission == null ? null : mission.getLaunchingAt(),
                mission == null ? null : mission.getCreatedAt(),
                mission == null ? null : mission.getUpdatedAt(),
                order.getPaidAt(), order.getPickupDeadline(), order.getCompletedAt(),
                point(lockers.get(sourceId)), point(lockers.get(destinationId)), events,
                box == null ? null : box.boxNumber(),
                name(customer),
                customer == null ? null : customer.phoneNumber(),
                StringUtils.hasText(order.getReceiverName()) ? order.getReceiverName() : name(receiver),
                StringUtils.hasText(order.getReceiverPhone())
                        ? order.getReceiverPhone()
                        : receiver == null ? null : receiver.phoneNumber(),
                mission == null ? null : name(users.get(mission.getAssignedByUserId())),
                mission == null ? null : name(users.get(mission.getLoadedByUserId())),
                mission == null ? null : mission.getLoadingNote(),
                // Checklist chỉ có nghĩa sau khi đã xác nhận nạp hàng.
                mission == null || mission.getLoadedAt() == null ? null : mission.isParcelMatched(),
                mission == null || mission.getLoadedAt() == null ? null : mission.isPayloadSecured(),
                mission == null || mission.getLoadedAt() == null ? null : mission.isCompartmentLocked(),
                order.getCancelReason(),
                "CANCELED".equals(order.getStatus()) ? order.getStaffNote() : null,
                order.getSourceBoxId(),
                sourceBox == null ? null : sourceBox.boxNumber());
    }

    private static Long sourceLockerId(LockerOrder order, DroneMission mission) {
        return mission != null && mission.getSourceLockerId() != null
                ? mission.getSourceLockerId() : order.getSourceLockerId();
    }

    private static Long destinationLockerId(LockerOrder order) {
        return order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
    }

    private static Long boxId(LockerOrder order) {
        return order.getReservedBoxId() != null ? order.getReservedBoxId() : order.getSendBoxId();
    }

    private static Long receiverUserId(LockerOrder order) {
        return order.getReceiverUserId() != null ? order.getReceiverUserId() : order.getReceiverId();
    }

    private static String name(UserSummary user) {
        return user == null || !StringUtils.hasText(user.fullName()) ? null : user.fullName();
    }

    private DroneLockerPointResponse point(LockerInfo locker) {
        return locker == null ? null : new DroneLockerPointResponse(
                locker.id(), locker.code(), locker.name(), locker.address(),
                locker.latitude(), locker.longitude());
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

    private record Row(LockerOrder order, DroneMission mission) {
    }
}
