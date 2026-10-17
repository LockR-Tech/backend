package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.DronePickupOffer;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupOfferRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Đóng một đơn drone là "giao không hoàn tất" — một lối ra duy nhất cho mọi trường hợp đơn đã vào
 * guồng mà không tới được tay người nhận: thời tiết/tạm dừng bay, drone hoặc tủ có phiếu sự cố, không
 * có KTV nhận lấy hàng, KTV tới nơi mà không lấy được kiện, sự cố lúc bay.
 *
 * <p>Đơn sang `CANCELED` (để danh sách/thống kê dùng chung với luồng tủ không phải đổi) với chặng
 * `FAILED`, kèm lý do và phiếu sự cố liên quan. Tài nguyên được nhả: lượt lấy hàng của KTV, drone, ô
 * nhận. Kiện đã rời tay người gửi thì chuyển sang "chờ trả" ({@link DroneParcelCustody}). Người gửi
 * (và người nhận, nếu đã được báo có đơn) nhận thông báo và email.
 *
 * <p>Nơi gọi tạo yêu cầu hoàn tiền SAU khi giao dịch này commit
 * ({@link DroneRefundService#refundCanceledOrder}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneDeliveryFailureService {

    /// Ai đóng đơn — quyết định ai được phép và ghi vào nhật ký.
    public enum Actor { SYSTEM, TECHNICIAN, ADMIN }

    /// Các chặng mà đơn còn có thể kết thúc "giao không hoàn tất" (chưa vào ô nhận).
    public static final Set<String> OPEN_STAGES = Set.of(
            "AWAITING_DISPATCH", "PICKUP_ASSIGNED", "PICKED_UP", "ACCEPTED",
            "LAUNCHING", "DEPARTED", "EN_ROUTE", "APPROACHING", "ARRIVED");

    private static final Set<String> PRE_LAUNCH_MISSION = Set.of("AWAITING_LOADING", "READY_TO_LAUNCH");

    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final DronePickupRepository pickupRepository;
    private final DronePickupOfferRepository offerRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final LockerClient lockerClient;
    private final LockerDroneClient lockerDroneClient;
    private final NotificationClient notificationClient;
    private final DroneDeliveryMailer mailer;
    /// Trả lượt mã giảm giá + hoàn điểm thưởng khi đơn đóng.
    private final OrderBenefitService benefits;

    /**
     * @param reason   mã trong {@link DroneFailureReasons}
     * @param ticketId phiếu sự cố liên quan; null thì lấy phiếu đang chặn đơn (nếu có)
     * @return đơn đã đóng
     */
    @Transactional
    public LockerOrder fail(Long orderId, Long actorUserId, Actor actor, String reason, String note, Long ticketId) {
        LockerOrder order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!"DRONE_DELIVERY".equals(order.getType())) {
            throw new BusinessException("DRONE_ORDER_REQUIRED", "Order is not a drone delivery order");
        }
        if (reason == null) {
            // Điều phối/admin chỉ bấm "xác nhận không hoàn tất" trên đơn đang bị chặn.
            reason = order.getDeliveryBlockReason();
            if (note == null) {
                note = order.getDeliveryBlockNote();
            }
        }
        if (!DroneFailureReasons.known(reason)) {
            throw new BusinessException("DRONE_FAILURE_REASON_INVALID", "Unknown drone delivery failure reason: " + reason);
        }
        String trimmedNote = StringUtils.hasText(note) ? note.trim() : null;
        if (DroneFailureReasons.OTHER.equals(reason) && trimmedNote == null) {
            throw new BusinessException("DRONE_CANCEL_NOTE_REQUIRED", "A note is required when reason is OTHER");
        }
        String stage = order.getDeliveryStage();
        if (!"AWAITING_DISPATCH".equals(order.getStatus()) || stage == null || !OPEN_STAGES.contains(stage)) {
            throw new BusinessException(
                    "DRONE_ORDER_STATUS_INVALID", "Drone order is no longer in a stage that can fail delivery");
        }
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        DronePickup pickup = pickupRepository.findByOrderId(orderId).orElse(null);
        if (actor == Actor.TECHNICIAN && !isResponsibleTechnician(actorUserId, mission, pickup)) {
            throw new BusinessException(
                    "DRONE_MISSION_NOT_ASSIGNED_TO_USER",
                    "Only the drone technician handling this order may close it");
        }

        boolean refundExpected = hasCollectedMoney(order);
        boolean launched = mission != null && mission.getLaunchingAt() != null;
        String label = DroneFailureReasons.label(reason);
        Long ticket = ticketId != null ? ticketId : order.getDeliveryBlockTicketId();

        releaseDroneQuietly(mission, order, label);
        releaseBoxQuietly(order.getReservedBoxId());
        closePickup(pickup, DronePickup.CANCELED);

        order.setFailureReason(reason);
        order.setFailureTicketId(ticket);
        order.setFailedAt(LocalDateTime.now());
        order.setCancelReason(DroneFailureReasons.legacyCancelCode(reason));
        order.setStaffNote(trimmedNote);
        order.setDeliveryBlockReason(null);
        order.setDeliveryBlockedAt(null);
        order.setDeliveryBlockTicketId(null);
        order.setDeliveryBlockNote(null);
        order.setPinCode(null);
        order.setStatus("CANCELED");
        order.setDeliveryStage("FAILED");
        benefits.releaseBenefits(order);
        orderRepository.save(order);

        if (mission != null && mission.getEndedAt() == null) {
            mission.setFailedStage(mission.getStatus());
            mission.setStatus("FAILED");
            mission.setEndedAt(LocalDateTime.now());
            mission.setEndedByUserId(actorUserId);
            mission.setEndReason(DroneFailureReasons.legacyCancelCode(reason));
            mission.setEndNote(trimmedNote);
            missionRepository.save(mission);
        }

        boolean parcelCollected = DroneParcelCustody.inSystem(order, mission);
        addHistory(order, stage, actorUserId,
                "Giao không hoàn tất: " + label
                        + (trimmedNote == null ? "" : " · " + trimmedNote)
                        + (ticket == null ? "" : " · phiếu sự cố #" + ticket)
                        + (actor == Actor.SYSTEM ? " · hệ thống tự đóng" : "")
                        + (parcelCollected ? " · kiện chờ trả cho người gửi" : ""));

        notifyParties(order, mission, pickup, actorUserId, label, refundExpected, parcelCollected);
        DroneDeliveryMailer.Outcome mail = mailer.failed(
                order, label, trimmedNote, ticket, refundExpected, parcelCollected, launched);
        String mailed = mail.describe(order);
        addHistory(order, "FAILED", null,
                mailed == null
                        ? "Chưa gửi được email báo giao không hoàn tất."
                        : "Đã gửi email báo giao không hoàn tất cho " + mailed + ".");
        return order;
    }

    /// Nhả lượt lấy hàng đang giữ KTV khi đơn đóng (khách huỷ, đội bay huỷ, tự huỷ, giao không hoàn tất).
    @Transactional
    public void closePickupForOrder(Long orderId) {
        closePickup(pickupRepository.findByOrderId(orderId).orElse(null), DronePickup.CANCELED);
    }

    private void closePickup(DronePickup pickup, String status) {
        if (pickup == null || !pickup.holdsTechnician() && !DronePickup.SEARCHING.equals(pickup.getStatus())) {
            return;
        }
        Long technicianId = pickup.getTechnicianId();
        if (technicianId != null) {
            for (DronePickupOffer offer : offerRepository.findByPickupId(pickup.getId())) {
                if (Objects.equals(offer.getTechnicianId(), technicianId)
                        && (DronePickupOffer.OFFERED.equals(offer.getStatus())
                                || DronePickupOffer.ACCEPTED.equals(offer.getStatus()))) {
                    offer.setStatus(DronePickupOffer.CANCELED);
                    offer.setRespondedAt(LocalDateTime.now());
                    offerRepository.save(offer);
                }
            }
        }
        pickup.setStatus(status);
        pickup.setOfferExpiresAt(null);
        pickupRepository.save(pickup);
    }

    private static boolean isResponsibleTechnician(Long userId, DroneMission mission, DronePickup pickup) {
        if (userId == null) {
            return false;
        }
        if (mission != null && Objects.equals(mission.getAssignedByUserId(), userId)) {
            return true;
        }
        return pickup != null
                && Objects.equals(pickup.getTechnicianId(), userId)
                && (DronePickup.ACCEPTED.equals(pickup.getStatus()) || DronePickup.PICKED_UP.equals(pickup.getStatus()));
    }

    /// Đơn đã PAID, hoặc đang nợ phụ thu cân lệch nhưng đã trả phần phí ban đầu.
    /// Đơn 0đ nhờ mã giảm giá/điểm thưởng (PAID mà chưa thu đồng nào) không có gì để hoàn.
    private static boolean hasCollectedMoney(LockerOrder order) {
        return OrderBenefitService.collectedMoney(order);
    }

    /// Chưa phóng: trả drone đang giữ riêng về rảnh. Đã phóng: drone phải được kiểm tra (và lấy
    /// kiện ra) trước khi bay lại nên chuyển FAULT. Trạng thái đội bay có thể đã bị đổi hoặc
    /// locker-service lỗi — không được chặn việc đóng đơn và hoàn tiền cho khách.
    private void releaseDroneQuietly(DroneMission mission, LockerOrder order, String label) {
        if (mission == null || mission.getDroneUnitId() == null || mission.getEndedAt() != null) {
            return;
        }
        try {
            if (PRE_LAUNCH_MISSION.contains(mission.getStatus())) {
                lockerDroneClient.transitionDroneStatus(
                        mission.getDroneUnitId(), new DroneStatusTransitionRequest("RESERVED", "IDLE", null));
            } else if (DroneMissionProgressService.IN_FLIGHT_STAGES.contains(mission.getStatus())) {
                lockerDroneClient.transitionDroneStatus(
                        mission.getDroneUnitId(),
                        new DroneStatusTransitionRequest(
                                "IN_FLIGHT", "FAULT", "Delivery of order " + order.getOrderCode() + " failed: " + label));
            }
        } catch (RuntimeException ex) {
            log.info("Drone {} not released after failed delivery of order {}: {}",
                    mission.getDroneUnitId(), order.getId(), ex.getMessage());
        }
    }

    /// Trạng thái ô chỉ là bản sao của đơn: lỗi nhả ô không được chặn việc đóng đơn; job đối soát
    /// nhả ô mồ côi sau.
    private void releaseBoxQuietly(Long boxId) {
        if (boxId == null) {
            return;
        }
        try {
            lockerClient.releaseBox(boxId);
        } catch (RuntimeException ex) {
            log.info("Box {} not released after failed drone delivery: {}", boxId, ex.getMessage());
        }
    }

    private void notifyParties(LockerOrder order, DroneMission mission, DronePickup pickup, Long actorUserId,
            String label, boolean refundExpected, boolean parcelCollected) {
        String forCustomer = "Đơn " + order.getOrderCode() + " giao không hoàn tất: " + label + "."
                + (refundExpected ? " Tiền đã trả sẽ được hoàn." : "")
                + (parcelCollected ? " Kỹ thuật viên sẽ mang kiện trả lại cho bạn." : "");
        notifyQuietly(order.getUserId(), "Đơn drone giao không hoàn tất", forCustomer, order);
        if (order.getReceiverUserId() != null && !order.getReceiverUserId().equals(order.getUserId())) {
            notifyQuietly(order.getReceiverUserId(), "Đơn drone giao không hoàn tất",
                    "Đơn " + order.getOrderCode() + " gửi cho bạn không hoàn tất: " + label + ".", order);
        }
        // KTV đang giữ việc/kiện của đơn này phải biết để dừng và mang kiện trả.
        List<Long> technicians = new java.util.ArrayList<>();
        if (mission != null && mission.getAssignedByUserId() != null) {
            technicians.add(mission.getAssignedByUserId());
        }
        if (pickup != null && pickup.getTechnicianId() != null && !technicians.contains(pickup.getTechnicianId())) {
            technicians.add(pickup.getTechnicianId());
        }
        for (Long technician : technicians) {
            if (technician.equals(actorUserId)) {
                continue;
            }
            notifyQuietly(technician, "Đơn drone đã đóng — giao không hoàn tất",
                    "Đơn " + order.getOrderCode() + ": " + label + "."
                            + (parcelCollected ? " Mang kiện trả lại người gửi rồi xác nhận đã trả." : " Dừng lấy hàng."),
                    order);
        }
    }

    private void notifyQuietly(Long userId, String title, String message, LockerOrder order) {
        if (userId == null) {
            return;
        }
        try {
            notificationClient.requestNotification(new NotificationRequest(
                    userId, title, message, "DRONE_DELIVERY_STATUS_CHANGED", order.getId(), "ORDER"));
        } catch (RuntimeException ignored) {
            // Trạng thái đơn là nguồn sự thật; thông báo chỉ là kênh báo nhanh.
        }
    }

    private void addHistory(LockerOrder order, String fromStage, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(fromStage);
        history.setNewStatus("FAILED");
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);
    }
}
