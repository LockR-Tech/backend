package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Hai mốc bàn giao kiện của đơn drone mà trước đây hệ thống không biết:
 *
 * <ul>
 *   <li>người gửi xác nhận đã bỏ kiện vào ô DRONE ở tủ gửi — đội bay chỉ tiếp nhận được đơn
 *       đã có kiện, không còn nhận đơn mà ô còn rỗng;</li>
 *   <li>đội bay/admin xác nhận đã trả kiện cho người gửi khi đơn kết thúc mà không giao được
 *       (huỷ sau khi bỏ kiện, huỷ sau khi nạp hàng, chuyến bay thất bại).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DroneParcelService {

    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final LockerClient lockerClient;
    private final NotificationClient notificationClient;
    private final UserClient userClient;

    /// Người đặt xác nhận đã bỏ kiện vào ô gửi. Gọi lại nhiều lần không đổi mốc đã ghi.
    @Transactional
    public void confirmDrop(Long orderId, Long userId) {
        LockerOrder order = findDroneOrder(orderId);
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("ORDER_FORBIDDEN", "Order does not belong to user");
        }
        if (order.getParcelDroppedAt() != null) {
            return;
        }
        if (!"AWAITING_DISPATCH".equals(order.getStatus())
                || !"AWAITING_DISPATCH".equals(order.getDeliveryStage())) {
            throw new BusinessException(
                    "DRONE_ORDER_STATUS_INVALID", "Parcel drop-off can only be confirmed while awaiting dispatch");
        }
        // Cùng quy tắc với đơn tủ: trả tiền rồi mới bỏ hàng.
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new BusinessException("DRONE_ORDER_UNPAID", "Drone order must be paid before dropping the parcel");
        }
        order.setParcelDroppedAt(LocalDateTime.now());
        orderRepository.save(order);
        // Ô gửi đã có kiện ⇒ OCCUPIED chứ không còn là "đang giữ chỗ". Bản sao trạng thái ô lỗi
        // không được chặn người gửi; job đối soát sửa sau.
        if (order.getSourceBoxId() != null) {
            try {
                lockerClient.occupyBox(order.getSourceBoxId());
            } catch (RuntimeException ignored) {
                // Xem chú thích trên.
            }
        }
        addHistory(order, userId, "Người gửi xác nhận đã bỏ kiện vào ô gửi — chờ đội bay tiếp nhận");
        notifyTechniciansQuietly(order);
    }

    /// Điều phối viên đã nhận nhiệm vụ (đơn chưa có nhiệm vụ thì điều phối viên bất kỳ) hoặc
    /// admin xác nhận kiện đã về tay người gửi; ô gửi còn giữ thì nhả ở đây.
    @Transactional
    public void confirmReturn(Long orderId, Long userId, boolean admin, String note) {
        LockerOrder order = findDroneOrder(orderId);
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        if (!admin && mission != null && !Objects.equals(mission.getAssignedByUserId(), userId)) {
            throw new BusinessException(
                    "DRONE_MISSION_NOT_ASSIGNED_TO_USER",
                    "Only the drone technician who accepted this mission may return its parcel");
        }
        if (!DroneParcelCustody.returnPending(order, mission)) {
            throw new BusinessException(
                    "DRONE_PARCEL_RETURN_NOT_PENDING", "This drone order has no parcel waiting to be returned");
        }
        String trimmed = StringUtils.hasText(note) ? note.trim() : null;
        order.setParcelReturnedAt(LocalDateTime.now());
        order.setParcelReturnedByUserId(userId);
        order.setParcelReturnNote(trimmed);
        Long sourceBoxId = order.getSourceBoxId();
        order.setSourceBoxId(null);
        orderRepository.save(order);
        if (sourceBoxId != null) {
            try {
                lockerClient.releaseBox(sourceBoxId);
            } catch (RuntimeException ignored) {
                // Job đối soát nhả ô mồ côi sau; việc trả kiện đã được ghi nhận.
            }
        }
        addHistory(order, userId, "Đã trả kiện cho người gửi" + (trimmed == null ? "" : " · " + trimmed));
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            order.getUserId(),
                            "Đã trả lại kiện hàng",
                            "Kiện của đơn drone " + order.getOrderCode() + " đã được trả lại cho bạn.",
                            "DRONE_DELIVERY_STATUS_CHANGED",
                            order.getId(),
                            "ORDER"));
        } catch (RuntimeException ignored) {
            // Trạng thái đơn là nguồn sự thật; thông báo chỉ là kênh báo nhanh.
        }
    }

    private LockerOrder findDroneOrder(Long orderId) {
        LockerOrder order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!"DRONE_DELIVERY".equals(order.getType())) {
            throw new BusinessException("DRONE_ORDER_REQUIRED", "Order is not a drone delivery order");
        }
        return order;
    }

    /// Mốc bàn giao kiện không đổi chặng: ghi thành dòng nhật ký chặng → chính chặng đó.
    private void addHistory(LockerOrder order, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(order.getDeliveryStage());
        history.setNewStatus(order.getDeliveryStage());
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);
    }

    private void notifyTechniciansQuietly(LockerOrder order) {
        try {
            var response = userClient.getUsersByRole("DRONE_TECHNICIAN");
            List<UserSummary> technicians = response == null || response.data() == null ? List.of() : response.data();
            for (UserSummary technician : technicians) {
                notificationClient.requestNotification(
                        new NotificationRequest(
                                technician.id(),
                                "Đơn drone sẵn sàng tiếp nhận",
                                "Người gửi đã bỏ kiện của đơn " + order.getOrderCode() + " vào ô gửi.",
                                "DRONE_ORDER_CREATED",
                                order.getId(),
                                "ORDER"));
            }
        } catch (RuntimeException ignored) {
            // Hàng đợi điều phối tự làm mới vẫn hiện đơn; thông báo chỉ là kênh báo nhanh.
        }
    }
}
