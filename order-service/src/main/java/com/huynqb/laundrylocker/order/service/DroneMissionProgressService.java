package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.DeliveryStatusNotificationRequest;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Đẩy một nhiệm vụ drone sang chặng bay kế tiếp. Dùng chung cho hai nguồn:
 * bộ giả lập (đơn DEMO, tự chạy theo thời gian) và điều phối viên xác nhận tay
 * (đơn STANDARD, khi chưa có telemetry tự báo chặng). Một chỗ duy nhất để hai
 * nguồn không lệch nhau về trạng thái ô, PIN, thông báo và nhật ký hành trình.
 */
@Service
@RequiredArgsConstructor
public class DroneMissionProgressService {

    /// Các chặng drone đang ở trên không — còn chặng kế tiếp để đẩy.
    public static final List<String> IN_FLIGHT_STAGES =
            List.of("LAUNCHING", "DEPARTED", "EN_ROUTE", "APPROACHING", "ARRIVED");

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final DroneMissionRepository missionRepository;
    private final LockerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final NotificationClient notificationClient;
    private final LockerDroneClient lockerDroneClient;
    private final LockerClient lockerClient;
    private final OrderRules rules;

    /// Chặng kế tiếp của một mission đang bay; null nếu mission không ở chặng bay.
    public static String nextStage(String missionStatus) {
        return switch (missionStatus == null ? "" : missionStatus) {
            case "LAUNCHING" -> "DEPARTED";
            case "DEPARTED" -> "EN_ROUTE";
            case "EN_ROUTE" -> "APPROACHING";
            case "APPROACHING" -> "ARRIVED";
            case "ARRIVED" -> "READY_FOR_PICKUP";
            default -> null;
        };
    }

    /**
     * Điều phối viên đã tiếp nhận nhiệm vụ xác nhận drone sang chặng kế tiếp. Chỉ cho
     * đơn không chạy DEMO: đơn DEMO do bộ giả lập đẩy, cho đẩy tay nữa thì hai nguồn
     * giành nhau và nhảy cóc chặng.
     */
    @Transactional
    public String advanceByOperator(Long orderId, Long userId) {
        LockerOrder order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!"DRONE_DELIVERY".equals(order.getType())) {
            throw new BusinessException("DRONE_ORDER_REQUIRED", "Order is not a drone delivery order");
        }
        DroneMission mission = missionRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("DroneMission", orderId));
        if (!Objects.equals(mission.getAssignedByUserId(), userId)) {
            throw new BusinessException(
                    "DRONE_MISSION_NOT_ASSIGNED_TO_USER",
                    "Only the drone technician who accepted this mission may operate it");
        }
        if ("DEMO".equalsIgnoreCase(order.getFulfillmentMode())) {
            throw new BusinessException(
                    "DRONE_STAGE_AUTOMATED", "Demo missions advance automatically and cannot be advanced by hand");
        }
        String next = nextStage(mission.getStatus());
        if (next == null) {
            throw new BusinessException("DRONE_MISSION_STATUS_INVALID", "Drone mission is not in flight");
        }
        advance(order, mission, userId, "Điều phối viên xác nhận: " + stageLabel(next));
        return next;
    }

    /**
     * Chuyển mission sang chặng kế tiếp và ghi nhật ký. Ở chặng cuối (hàng vào ô):
     * đơn sang STORING, ô nhận sang OCCUPIED, cấp PIN + hạn nhận, trả drone về IDLE và
     * gửi mã cho người nhận.
     */
    @Transactional
    public void advance(LockerOrder order, DroneMission mission, Long actorUserId, String note) {
        String previousStage = order.getDeliveryStage();
        String nextStage = nextStage(mission.getStatus());
        if (nextStage == null) {
            return;
        }

        boolean deposited = "READY_FOR_PICKUP".equals(nextStage);
        if (deposited) {
            mission.setStatus("DEPOSITED");
            order.setStatus("STORING");
            order.setDeliveryStage(nextStage);
            order.setPinCode(String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
            order.setPinCodeIssuedAt(LocalDateTime.now());
            order.setPickupDeadline(LocalDateTime.now().plusHours(rules.dronePickupHours()));
        } else {
            mission.setStatus(nextStage);
            order.setDeliveryStage(nextStage);
        }
        missionRepository.save(mission);
        orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(previousStage);
        history.setNewStatus(nextStage);
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);

        if (deposited) {
            occupyDestinationBoxQuietly(order);
            releaseDroneQuietly(mission);
        }
        if (List.of("DEPARTED", "APPROACHING", "ARRIVED", "READY_FOR_PICKUP").contains(nextStage)) {
            notifyStageQuietly(order, nextStage);
        }
        if (deposited) {
            sendPickupCodeQuietly(order);
        }
    }

    public static String stageLabel(String stage) {
        return switch (stage == null ? "" : stage) {
            case "DEPARTED" -> "drone đã rời trạm";
            case "EN_ROUTE" -> "drone đang trên đường";
            case "APPROACHING" -> "drone sắp tới tủ nhận";
            case "ARRIVED" -> "drone đã tới tủ nhận";
            case "READY_FOR_PICKUP" -> "hàng đã vào ô tủ nhận";
            default -> stage;
        };
    }

    /// Hàng đã nằm trong ô ⇒ ô phải là OCCUPIED chứ không còn là "đang giữ chỗ".
    /// Trạng thái ô chỉ là bản sao của đơn: lỗi đồng bộ không được chặn việc giao hàng.
    private void occupyDestinationBoxQuietly(LockerOrder order) {
        Long boxId = order.getReservedBoxId() != null ? order.getReservedBoxId() : order.getSendBoxId();
        if (boxId == null) {
            return;
        }
        try {
            lockerClient.occupyBox(boxId);
        } catch (Exception ignored) {
            // Job đối soát ô sẽ sửa sau.
        }
    }

    private void releaseDroneQuietly(DroneMission mission) {
        if (mission.getDroneUnitId() == null) {
            return;
        }
        try {
            lockerDroneClient.transitionDroneStatus(
                    mission.getDroneUnitId(), new DroneStatusTransitionRequest("IN_FLIGHT", "IDLE", null));
        } catch (Exception ignored) {
            // A delivered parcel stays authoritative even if fleet status sync is temporarily unavailable.
        }
    }

    /// Báo chặng cho người nhận có tài khoản, và cho người gửi nếu là người khác.
    private void notifyStageQuietly(LockerOrder order, String stage) {
        Long receiver = order.getReceiverUserId();
        Long sender = order.getUserId();
        if (receiver != null) {
            notifyStage(order, receiver, stage);
        }
        if (sender != null && !sender.equals(receiver)) {
            notifyStage(order, sender, stage);
        }
    }

    private void notifyStage(LockerOrder order, Long userId, String stage) {
        try {
            notificationClient.notifyDeliveryStatus(new DeliveryStatusNotificationRequest(
                    order.getId(), userId, stage.toLowerCase(), etaFor(stage)));
        } catch (Exception ignored) {
            // Stage progression remains authoritative if notification-service is down.
        }
    }

    /**
     * Người nhận khác người đặt không thấy đơn trong app của mình nên phải được gửi
     * mã mở ô: thông báo trong app nếu có tài khoản, SMS tới số ghi trên đơn nếu không.
     * Người đặt tự nhận thì xem mã ngay trong đơn hàng — không gửi thêm.
     */
    private void sendPickupCodeQuietly(LockerOrder order) {
        Long receiver = order.getReceiverUserId();
        boolean receiverIsSender = receiver != null && receiver.equals(order.getUserId());
        if (receiverIsSender) {
            return;
        }
        String deadline = order.getPickupDeadline() == null
                ? "chưa xác định"
                : DEADLINE_FORMAT.format(
                        order.getPickupDeadline().atZone(ZoneOffset.UTC).withZoneSameInstant(DISPLAY_ZONE));
        try {
            if (receiver != null) {
                notificationClient.requestNotification(new NotificationRequest(
                        receiver,
                        "Mã nhận hàng giao bằng drone",
                        "Kiện hàng " + order.getOrderCode() + " đã vào tủ nhận. Mã mở ô: " + order.getPinCode()
                                + ". Hạn nhận: " + deadline + ".",
                        "DRONE_DELIVERY_STATUS_CHANGED",
                        order.getId(),
                        "ORDER"));
            } else if (StringUtils.hasText(order.getReceiverPhone())) {
                notificationClient.notifyGuest(new GuestNotification.Request(
                        order.getReceiverPhone(),
                        null,
                        "Mã mở tủ Lock.R cho đơn " + order.getOrderCode(),
                        "Lock.R: Kien hang " + order.getOrderCode() + " giao bang drone da vao tu. Ma mo tu: "
                                + order.getPinCode() + ". Han lay: " + deadline + ".",
                        null));
            }
        } catch (Exception ignored) {
            // Người gửi vẫn xem được mã trong đơn và tự chuyển cho người nhận.
        }
    }

    private static String etaFor(String stage) {
        return switch (stage) {
            case "DEPARTED" -> "8 phút";
            case "EN_ROUTE" -> "6 phút";
            case "APPROACHING" -> "2 phút";
            case "ARRIVED", "READY_FOR_PICKUP" -> "0 phút";
            default -> null;
        };
    }
}
