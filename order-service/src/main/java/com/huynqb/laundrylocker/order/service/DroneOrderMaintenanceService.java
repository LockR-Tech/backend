package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DroneOrderMaintenanceService {

    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final LockerDroneClient lockerDroneClient;
    private final LockerClient lockerClient;
    private final OrderStatusHistoryRepository historyRepository;
    private final NotificationClient notificationClient;
    private final OrderRules rules;
    private final DronePickupRepository pickupRepository;
    private final DroneDeliveryMailer mailer;
    /// Trả lượt mã giảm giá + hoàn điểm thưởng khi đơn bị huỷ.
    private final OrderBenefitService benefits;

    private static final java.security.SecureRandom SEAL_RANDOM = new java.security.SecureRandom();
    private static final java.time.ZoneId FLIGHT_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    @Transactional(readOnly = true)
    public List<DroneMissionResponse> queue(String deliveryStage) {
        return orderRepository.findByTypeAndStatusOrderByCreatedAtAsc("DRONE_DELIVERY", "AWAITING_DISPATCH").stream()
                .filter(order -> !StringUtils.hasText(deliveryStage) || deliveryStage.equalsIgnoreCase(order.getDeliveryStage()))
                .map(order -> toResponse(order, missionRepository.findByOrderId(order.getId()).orElse(null), null))
                .toList();
    }

    @Transactional
    public DroneMissionResponse accept(
            Long orderId, Long userId, String idempotencyKey, AcceptDroneOrderRequest request) {
        LockerOrder order = findDroneOrder(orderId);
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new BusinessException("DRONE_ORDER_UNPAID", "Drone order must be paid before acceptance");
        }
        if (!"AWAITING_DISPATCH".equals(order.getStatus())) {
            throw new BusinessException("DRONE_ORDER_STATUS_INVALID", "Drone order is not awaiting dispatch");
        }
        assertParcelInHand(order, userId);
        assertFlightsNotSuspended();

        DroneMission existingMission = missionRepository.findByOrderId(orderId).orElse(null);
        if (existingMission != null
                && idempotencyKey != null
                && idempotencyKey.equals(existingMission.getLastAcceptIdempotencyKey())) {
            DroneUnitDto currentDrone = fetchDrone(existingMission.getDroneUnitId());
            return toResponse(order, existingMission, currentDrone);
        }
        if (existingMission != null) {
            throw new BusinessException(
                    "DRONE_MISSION_ALREADY_EXISTS", "Order already has a drone mission");
        }

        DroneUnitDto drone = fetchDrone(request.droneUnitId());
        validateDronePreflight(drone);
        if (order.getSourceLockerId() != null && !Objects.equals(order.getSourceLockerId(), drone.lockerId())) {
            throw new BusinessException(
                    "DRONE_WRONG_SOURCE_LOCKER", "Selected drone is not stationed at the order source locker");
        }
        if (order.getSourceLockerId() == null) {
            order.setSourceLockerId(drone.lockerId());
        }
        validateLockerPreflight(fetchLockerLayout(order.getSourceLockerId()), "Source");
        LockerLayoutDto layout = fetchLockerLayout(requireDestinationLockerId(order));
        validateLockerPreflight(layout, "Destination");

        DroneUnitDto reservedDrone = transitionDroneStatus(
                drone.id(), "IDLE", "RESERVED", "Reserved for order " + order.getOrderCode());

        try {
            DroneMission mission = new DroneMission();
            mission.setOrderId(order.getId());
            mission.setDroneUnitId(reservedDrone.id());
            mission.setDroneCode(reservedDrone.code());
            mission.setSourceLockerId(reservedDrone.lockerId());
            mission.setDestinationLockerId(requireDestinationLockerId(order));
            mission.setAssignedByUserId(userId);
            mission.setStatus("AWAITING_LOADING");
            mission.setLastAcceptIdempotencyKey(idempotencyKey);
            mission = missionRepository.save(mission);

            String previousStage = order.getDeliveryStage();
            order.setDeliveryStage("ACCEPTED");
            order.setStaffId(userId);
            orderRepository.save(order);
            addJourneyEvent(order.getId(), previousStage, "ACCEPTED", userId,
                    "Kỹ thuật viên mang kiện về tủ gửi và gán drone " + reservedDrone.code());
            notifyDeliveryStageQuietly(order, "accepted", "10 phút");
            return toResponse(order, mission, reservedDrone);
        } catch (RuntimeException failure) {
            releaseReservationQuietly(reservedDrone.id());
            throw failure;
        }
    }

    @Transactional
    public DroneMissionResponse confirmLoading(
            Long orderId, Long userId, String idempotencyKey, ConfirmDroneLoadingRequest request) {
        LockerOrder order = findDroneOrder(orderId);
        DroneMission mission =
                missionRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("DroneMission", orderId));
        validateAssignedOperator(mission, userId);
        if (idempotencyKey != null
                && idempotencyKey.equals(mission.getLastLoadingIdempotencyKey())
                && "READY_TO_LAUNCH".equals(mission.getStatus())) {
            return toResponse(order, mission, fetchDrone(mission.getDroneUnitId()));
        }
        if (!"ACCEPTED".equals(order.getDeliveryStage()) || !"AWAITING_LOADING".equals(mission.getStatus())) {
            throw new BusinessException(
                    "DRONE_MISSION_STATUS_INVALID", "Drone mission is not awaiting loading confirmation");
        }
        if (request.payloadWeightGrams() > rules.droneMaxPayloadWeightGrams()) {
            throw new BusinessException(
                    "DRONE_PAYLOAD_TOO_HEAVY",
                    "Payload exceeds the configured drone limit of "
                            + rules.droneMaxPayloadWeightGrams() + " grams");
        }

        DroneUnitDto drone = fetchDrone(mission.getDroneUnitId());
        validateDroneReservation(drone);
        String sealCode = StringUtils.hasText(request.sealCode())
                ? request.sealCode().trim()
                : generateSealCode();
        BigDecimal surcharge = applyWeightSurcharge(order, request.payloadWeightGrams(), mission.getWeightSurcharge());
        mission.setPayloadWeightGrams(request.payloadWeightGrams());
        mission.setSealCode(sealCode);
        BigDecimal earlier = mission.getWeightSurcharge();
        mission.setWeightSurcharge(
                earlier == null ? surcharge : surcharge == null ? earlier : earlier.add(surcharge));
        mission.setParcelMatched(request.parcelMatched());
        mission.setPayloadSecured(request.payloadSecured());
        mission.setCompartmentLocked(request.compartmentLocked());
        mission.setLoadingNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        mission.setLoadedByUserId(userId);
        mission.setLoadedAt(LocalDateTime.now());
        mission.setReadyToLaunchAt(mission.getLoadedAt());
        mission.setLastLoadingIdempotencyKey(idempotencyKey);
        mission.setStatus("READY_TO_LAUNCH");
        missionRepository.save(mission);

        // Kiện đã rời ô gửi lên drone ⇒ trả ô cho người gửi kế tiếp.
        releaseSourceBoxQuietly(order);
        order.setStaffId(userId);
        orderRepository.save(order);
        addJourneyEvent(order.getId(), "ACCEPTED", "ACCEPTED", userId,
                "Đã nạp kiện " + request.payloadWeightGrams() + " g, niêm phong " + sealCode
                        + (surcharge == null
                                ? ""
                                : ". Nặng hơn khai báo " + order.getParcelWeightGrams() + " g — thu thêm "
                                        + surcharge.toBigInteger() + " đ trước khi phóng"));
        notifyDeliveryStageQuietly(order, "loading_confirmed", "10 phút");
        if (surcharge != null) {
            notifyWeightSurchargeQuietly(order, request.payloadWeightGrams(), surcharge);
        }
        return toResponse(order, mission, drone);
    }

    @Transactional
    public DroneMissionResponse launch(Long orderId, Long userId, String idempotencyKey) {
        LockerOrder order = findDroneOrder(orderId);
        DroneMission mission =
                missionRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("DroneMission", orderId));
        validateAssignedOperator(mission, userId);
        if (idempotencyKey != null
                && idempotencyKey.equals(mission.getLastLaunchIdempotencyKey())
                && "LAUNCHING".equals(mission.getStatus())) {
            return toResponse(order, mission, fetchDrone(mission.getDroneUnitId()));
        }
        if (!"READY_TO_LAUNCH".equals(mission.getStatus())) {
            throw new BusinessException("DRONE_MISSION_STATUS_INVALID", "Drone mission is not ready to launch");
        }
        if (!isLoadingConfirmed(mission)) {
            throw new BusinessException(
                    "DRONE_LOADING_NOT_CONFIRMED", "Loading checklist must be completed before launch");
        }
        // Kiện nặng hơn khai báo làm đơn nợ phần chênh: chưa trả đủ thì chưa bay.
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new BusinessException(
                    "DRONE_SURCHARGE_UNPAID", "Customer must pay the weight surcharge before launch");
        }
        assertFlightsNotSuspended();
        int hourNow = java.time.ZonedDateTime.now(FLIGHT_ZONE).getHour();
        if (!rules.droneFlightAllowedAt(hourNow)) {
            throw new BusinessException(
                    "DRONE_OUTSIDE_FLIGHT_HOURS",
                    "Drones may only launch between " + rules.droneFlightWindowLabel());
        }

        DroneUnitDto drone = fetchDrone(mission.getDroneUnitId());
        validateReservedDroneForLaunch(drone);
        LockerLayoutDto layout = fetchLockerLayout(requireDestinationLockerId(order));
        validateLockerPreflight(layout, "Destination");
        DroneUnitDto updatedDrone = transitionDroneStatus(
                mission.getDroneUnitId(), "RESERVED", "IN_FLIGHT", null);
        mission.setStatus("LAUNCHING");
        mission.setBatteryPercentAtLaunch(updatedDrone.batteryPercent() != null
                ? updatedDrone.batteryPercent()
                : drone.batteryPercent());
        mission.setLastLaunchIdempotencyKey(idempotencyKey);
        mission.setLaunchingAt(LocalDateTime.now());
        missionRepository.save(mission);

        order.setDeliveryStage("LAUNCHING");
        order.setStaffId(userId);
        orderRepository.save(order);
        addJourneyEvent(order.getId(), "ACCEPTED", "LAUNCHING", userId,
                "Đã phát lệnh khởi phóng drone " + updatedDrone.code());
        // Drone đã cất cánh: người gửi biết đơn đang tới ai, người nhận biết có đơn từ ai.
        String mailed = mailer.launched(order, mission).describe(order);
        addJourneyEvent(order.getId(), "LAUNCHING", "LAUNCHING", null,
                mailed == null
                        ? "Chưa gửi được email báo đơn đã phóng cho người gửi và người nhận."
                        : "Đã gửi email báo đơn đã phóng cho " + mailed + ".");
        notifyDeliveryStageQuietly(order, "launching", "9 phút");
        return toResponse(order, mission, updatedDrone);
    }

    @Transactional
    public DroneMissionResponse cancel(Long orderId, Long userId, CancelDroneOrderRequest request) {
        LockerOrder order = findDroneOrder(orderId);
        if (!"ACCEPTED".equals(order.getDeliveryStage())) {
            throw new BusinessException(
                    "DRONE_ORDER_STATUS_INVALID", "Drone order can only be canceled before launch");
        }

        DroneMission mission =
                missionRepository
                        .findByOrderId(orderId)
                        .orElseThrow(() -> new NotFoundException("DroneMission", orderId));
        validateAssignedOperator(mission, userId);
        if (!("AWAITING_LOADING".equals(mission.getStatus()) || "READY_TO_LAUNCH".equals(mission.getStatus()))) {
            throw new BusinessException(
                    "DRONE_MISSION_STATUS_INVALID", "Drone mission can only be canceled before launch");
        }

        String note = requireReasonNote(request);

        String oldStatus = order.getStatus();
        releaseReservationIfHeld(mission.getDroneUnitId());
        if (order.getReservedBoxId() != null) {
            lockerClient.releaseBox(order.getReservedBoxId());
        }
        // Kiện đã bỏ vào ô gửi mà chưa nạp lên drone thì vẫn nằm trong ô: giữ ô tới khi trả kiện
        // (DroneParcelService.confirmReturn). Đã nạp thì ô đã được nhả lúc nạp.
        if (!DroneParcelCustody.inSourceBox(order, mission)) {
            releaseSourceBoxQuietly(order);
        }

        order.setCancelReason(request.reasonCode());
        order.setStaffNote(note);
        order.setStaffId(userId);
        order.setStatus("CANCELED");
        order.setDeliveryStage("CANCELED");
        benefits.releaseBenefits(order);
        orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(oldStatus);
        history.setNewStatus("CANCELED");
        history.setChangedByUserId(userId);
        history.setNote(cancelReasonLabel(request.reasonCode()) + (note == null ? "" : " · " + note));
        historyRepository.save(history);

        // Giữ hồ sơ nhiệm vụ (cân nặng, niêm phong, người nạp) để đối chứng sau khi huỷ.
        closeMission(mission, "CANCELED", userId, request.reasonCode(), note);

        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            order.getUserId(),
                            "Nhiệm vụ drone đã bị hủy",
                            "Đội bay đã hủy nhiệm vụ trước khi phóng.",
                            "DRONE_DELIVERY_STATUS_CHANGED",
                            order.getId(),
                            "ORDER"));
        } catch (RuntimeException ignored) {
            // Best-effort notification; cancellation itself must still succeed.
        }

        DroneUnitDto drone = mission.getDroneUnitId() == null ? null : fetchDrone(mission.getDroneUnitId());
        return new DroneMissionResponse(
                order.getId(),
                mission.getId(),
                "CANCELED",
                "CANCELED",
                mission.getDroneUnitId(),
                drone == null ? null : drone.code(),
                mission.getSourceLockerId(),
                mission.getDestinationLockerId(),
                order.getReservedBoxId(),
                order.getDescription(),
                mission.getAssignedByUserId(),
                order.getParcelWeightGrams(),
                mission.getPayloadWeightGrams(),
                mission.getSealCode(),
                mission.getLoadedByUserId(),
                mission.getLoadedAt(),
                mission.getReadyToLaunchAt(),
                mission.getWeightSurcharge(),
                order.getPaymentStatus());
    }

    /**
     * Đóng đơn đang nợ phụ thu cân lệch mà không được trả: khách chủ động từ chối
     * ({@code customerUserId} khác null), hoặc quá hạn ({@code cutoff} khác null, do
     * {@link DroneOrderTimeoutSweeper} gọi). Trước đây khách không tự thoát được và drone bị giữ
     * `RESERVED` vô hạn. Kiện đã nạp lên drone nên chờ đội bay trả lại; nơi gọi tạo yêu cầu
     * hoàn phần đã trả sau khi giao dịch này commit.
     *
     * @return false khi đơn không (còn) nợ phụ thu quá hạn — chỉ với lượt quét tự động;
     *     khách gọi mà đơn không nợ phụ thu thì ném lỗi.
     */
    @Transactional
    public boolean cancelUnpaidSurcharge(Long orderId, Long customerUserId, LocalDateTime cutoff) {
        LockerOrder order = findDroneOrder(orderId);
        boolean byCustomer = customerUserId != null;
        if (byCustomer && !customerUserId.equals(order.getUserId())) {
            throw new BusinessException("ORDER_FORBIDDEN", "Order does not belong to user");
        }
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        boolean owesSurcharge = mission != null
                && "ACCEPTED".equals(order.getDeliveryStage())
                && "READY_TO_LAUNCH".equals(mission.getStatus())
                && mission.getWeightSurcharge() != null
                && "UNPAID".equalsIgnoreCase(order.getPaymentStatus());
        if (!owesSurcharge) {
            if (byCustomer) {
                throw new BusinessException(
                        "DRONE_SURCHARGE_NOT_OWED", "This drone order has no unpaid weight surcharge to decline");
            }
            return false;
        }
        if (!byCustomer && (mission.getLoadedAt() == null || mission.getLoadedAt().isAfter(cutoff))) {
            return false;
        }

        String note = byCustomer
                ? "Khách từ chối trả phụ thu cân lệch"
                : "Quá hạn trả phụ thu cân lệch";
        releaseReservationIfHeld(mission.getDroneUnitId());
        releaseDestinationBoxQuietly(order);
        order.setStaffNote(note);
        order.setPinCode(null);
        order.setStatus("CANCELED");
        order.setDeliveryStage("CANCELED");
        benefits.releaseBenefits(order);
        orderRepository.save(order);
        addJourneyEvent(order.getId(), "ACCEPTED", "CANCELED", customerUserId,
                note + " — đội bay dỡ kiện khỏi drone và trả lại người gửi");
        closeMission(mission, "CANCELED", customerUserId, null, note);

        notifyQuietly(
                mission.getAssignedByUserId(),
                "Đơn drone đã huỷ — cần trả kiện",
                "Đơn " + order.getOrderCode() + ": " + note.toLowerCase()
                        + ". Dỡ kiện khỏi drone và trả lại người gửi.",
                order);
        if (!byCustomer) {
            notifyQuietly(
                    order.getUserId(),
                    "Đơn drone đã tự huỷ",
                    "Đơn " + order.getOrderCode() + " chưa trả phụ thu cân lệch đúng hạn nên đã huỷ. "
                            + "Phần đã trả sẽ được hoàn và đội bay sẽ trả lại kiện cho bạn.",
                    order);
        }
        return true;
    }

    private void notifyQuietly(Long userId, String title, String message, LockerOrder order) {
        if (userId == null) {
            return;
        }
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            userId, title, message, "DRONE_DELIVERY_STATUS_CHANGED", order.getId(), "ORDER"));
        } catch (RuntimeException ignored) {
            // Trạng thái đơn là nguồn sự thật; thông báo chỉ là kênh báo nhanh.
        }
    }

    /**
     * Drone đã gán gặp sự cố trước khi phóng: đổi sang drone rảnh khác tại tủ gửi, thay vì phải đóng
     * đơn. Kiện phải được nạp và kiểm lại trên drone mới nên nhiệm vụ quay về "chờ nạp hàng"; cân
     * nặng và phụ thu đã ghi được giữ.
     */
    @Transactional
    public DroneMissionResponse changeDrone(Long orderId, Long userId, AcceptDroneOrderRequest request) {
        LockerOrder order = findDroneOrder(orderId);
        DroneMission mission =
                missionRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("DroneMission", orderId));
        validateAssignedOperator(mission, userId);
        if (!"ACCEPTED".equals(order.getDeliveryStage())
                || !("AWAITING_LOADING".equals(mission.getStatus()) || "READY_TO_LAUNCH".equals(mission.getStatus()))) {
            throw new BusinessException(
                    "DRONE_MISSION_STATUS_INVALID", "The drone can only be changed before launch");
        }
        if (Objects.equals(mission.getDroneUnitId(), request.droneUnitId())) {
            throw new BusinessException("DRONE_UNCHANGED", "This drone is already assigned to the mission");
        }
        DroneUnitDto next = fetchDrone(request.droneUnitId());
        validateDronePreflight(next);
        if (!Objects.equals(order.getSourceLockerId(), next.lockerId())) {
            throw new BusinessException(
                    "DRONE_WRONG_SOURCE_LOCKER", "Selected drone is not stationed at the order source locker");
        }
        DroneUnitDto reserved = transitionDroneStatus(
                next.id(), "IDLE", "RESERVED", "Reserved for order " + order.getOrderCode());
        String previousCode = mission.getDroneCode();
        // Drone cũ: còn RESERVED thì trả về rảnh; đã FAULT/MAINTENANCE thì giữ nguyên trạng thái mới.
        releaseReservationQuietly(mission.getDroneUnitId());

        mission.setDroneUnitId(reserved.id());
        mission.setDroneCode(reserved.code());
        mission.setStatus("AWAITING_LOADING");
        mission.setLoadedAt(null);
        mission.setLoadedByUserId(null);
        mission.setReadyToLaunchAt(null);
        mission.setParcelMatched(false);
        mission.setPayloadSecured(false);
        mission.setCompartmentLocked(false);
        mission.setLastLoadingIdempotencyKey(null);
        missionRepository.save(mission);
        addJourneyEvent(order.getId(), "ACCEPTED", "ACCEPTED", userId,
                "Đổi drone " + previousCode + " → " + reserved.code() + "; cần nạp và kiểm lại kiện");
        return toResponse(order, mission, reserved);
    }

    /**
     * Chỉ KTV đã lấy kiện ở nhà người gửi mới gán drone, và chỉ khi kiện đã trên tay (chặng
     * `PICKED_UP`). Đơn tạo trước khi có lấy hàng tận nhà (không có điểm lấy hàng) giữ quy tắc cũ:
     * người gửi đã xác nhận bỏ kiện vào ô gửi.
     */
    private void assertParcelInHand(LockerOrder order, Long userId) {
        if (order.getPickupLatitude() == null) {
            if (order.getParcelDroppedAt() == null) {
                throw new BusinessException(
                        "DRONE_PARCEL_NOT_DROPPED",
                        "Sender has not confirmed dropping the parcel at the source locker");
            }
            return;
        }
        DronePickup pickup = pickupRepository.findByOrderId(order.getId()).orElse(null);
        if (!"PICKED_UP".equals(order.getDeliveryStage())
                || pickup == null
                || !DronePickup.PICKED_UP.equals(pickup.getStatus())) {
            throw new BusinessException(
                    "DRONE_PARCEL_NOT_PICKED_UP", "The parcel has not been picked up from the sender yet");
        }
        if (!Objects.equals(pickup.getTechnicianId(), userId)) {
            throw new BusinessException(
                    "DRONE_PICKUP_NOT_ASSIGNED_TO_USER",
                    "Only the drone technician who picked up this parcel may assign a drone");
        }
    }

    private String requireReasonNote(CancelDroneOrderRequest request) {
        String note = StringUtils.hasText(request.note()) ? request.note().trim() : null;
        if (Integer.valueOf(5).equals(request.reasonCode()) && note == null) {
            throw new BusinessException(
                    "DRONE_CANCEL_NOTE_REQUIRED", "A note is required when reason is OTHER");
        }
        return note;
    }

    private void closeMission(DroneMission mission, String status, Long userId, Integer reasonCode, String note) {
        mission.setStatus(status);
        mission.setEndedAt(LocalDateTime.now());
        mission.setEndedByUserId(userId);
        mission.setEndReason(reasonCode);
        mission.setEndNote(note);
        missionRepository.save(mission);
    }

    /// Ô nhận chưa có hàng. Lỗi nhả ô không được chặn việc đóng đơn; job đối soát nhả ô mồ côi sau.
    private void releaseDestinationBoxQuietly(LockerOrder order) {
        if (order.getReservedBoxId() == null) {
            return;
        }
        try {
            lockerClient.releaseBox(order.getReservedBoxId());
        } catch (RuntimeException ignored) {
            // Xem chú thích trên.
        }
    }

    /// Cân thực tế vượt khối lượng khai báo quá sai số cho phép thì thu thêm phần chênh PHÍ GỐC
    /// fee(thực tế) − fee(khai báo), trừ phần phụ thu đã tính ở lần nạp trước (`alreadyCharged`, khi đổi
    /// drone phải nạp lại). Mã giảm giá/điểm thưởng chỉ giảm phí ban đầu, không giảm phụ thu — nên không
    /// so với `totalPrice` đã giảm. Phần chênh cộng vào tổng đơn và đơn về UNPAID để khách trả thêm (cùng
    /// cơ chế gia hạn thuê: `paidAmount` giữ nguyên, checkout chỉ thu phần còn thiếu). Kiện nhẹ hơn khai
    /// báo không hoàn lại. Trả về phần thu thêm, null khi không thu.
    private BigDecimal applyWeightSurcharge(LockerOrder order, int actualWeightGrams, BigDecimal alreadyCharged) {
        int declared = order.getParcelWeightGrams() == null ? 0 : order.getParcelWeightGrams();
        if (actualWeightGrams <= declared + rules.droneWeightToleranceGrams()) {
            return null;
        }
        BigDecimal current = order.getTotalPrice() == null ? BigDecimal.ZERO : order.getTotalPrice();
        BigDecimal surcharge = rules.droneDeliveryFee(actualWeightGrams)
                .subtract(rules.droneDeliveryFee(declared))
                .subtract(alreadyCharged == null ? BigDecimal.ZERO : alreadyCharged);
        if (surcharge.signum() <= 0) {
            return null;
        }
        order.setTotalPrice(current.add(surcharge));
        if (order.getOriginalPrice() != null) {
            order.setOriginalPrice(order.getOriginalPrice().add(surcharge));
        }
        order.setPaymentStatus("UNPAID");
        order.setPaidAt(null);
        return surcharge;
    }

    /// Mã niêm phong do hệ thống cấp: `NP-<yyMMdd>-<6 ký tự>`, bỏ các ký tự dễ đọc nhầm.
    private String generateSealCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            suffix.append(alphabet.charAt(SEAL_RANDOM.nextInt(alphabet.length())));
        }
        return "NP-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"))
                + "-" + suffix;
    }

    private void notifyWeightSurchargeQuietly(LockerOrder order, int actualWeightGrams, BigDecimal surcharge) {
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            order.getUserId(),
                            "Đơn drone cần trả thêm phí",
                            "Kiện đơn " + order.getOrderCode() + " cân thực tế " + actualWeightGrams
                                    + " g, nặng hơn khai báo. Vui lòng trả thêm " + surcharge.toBigInteger()
                                    + " đ để drone cất cánh.",
                            "DRONE_DELIVERY_STATUS_CHANGED",
                            order.getId(),
                            "ORDER"));
        } catch (RuntimeException ignored) {
            // Trạng thái đơn là nguồn sự thật; màn theo dõi tự làm mới sẽ hiện khoản cần trả.
        }
    }

    /// Nhả ô DRONE ở tủ gửi và xoá liên kết khỏi đơn để không nhả lần hai. Trạng thái ô
    /// chỉ là bản sao của đơn: lỗi gọi locker-service không được làm hỏng thao tác chính,
    /// job đối soát sẽ nhả ô mồ côi sau.
    private void releaseSourceBoxQuietly(LockerOrder order) {
        Long sourceBoxId = order.getSourceBoxId();
        if (sourceBoxId == null) {
            return;
        }
        try {
            lockerClient.releaseBox(sourceBoxId);
        } catch (RuntimeException ignored) {
            // Xem chú thích trên.
        }
        order.setSourceBoxId(null);
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

    private Long requireDestinationLockerId(LockerOrder order) {
        Long lockerId = order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        if (lockerId == null) {
            throw new BusinessException("DRONE_DESTINATION_REQUIRED", "Drone order is missing destination locker");
        }
        return lockerId;
    }

    private DroneUnitDto fetchDrone(Long droneUnitId) {
        if (droneUnitId == null) {
            throw new BusinessException("DRONE_REQUIRED", "Drone unit is required");
        }
        return requireData(lockerDroneClient.getDroneUnit(droneUnitId), "DRONE_LOOKUP_FAILED");
    }

    private LockerLayoutDto fetchLockerLayout(Long lockerId) {
        return requireData(lockerDroneClient.getLockerLayout(lockerId), "LOCKER_LAYOUT_LOOKUP_FAILED");
    }

    private void validateDronePreflight(DroneUnitDto drone) {
        if (!Boolean.TRUE.equals(drone.active())) {
            throw new BusinessException("DRONE_INACTIVE", "Drone is inactive");
        }
        if (!"IDLE".equals(drone.status())) {
            throw new BusinessException("DRONE_NOT_IDLE", "Drone must be IDLE before acceptance");
        }
        requireFlightBattery(drone);
    }

    /// Không biết mức pin thì không được coi là đủ pin.
    private void requireFlightBattery(DroneUnitDto drone) {
        if (drone.batteryPercent() == null) {
            throw new BusinessException(
                    "DRONE_BATTERY_UNKNOWN", "Drone battery level is unknown; update it before the flight");
        }
        if (drone.batteryPercent() <= rules.droneMinPreflightBatteryPercent()) {
            throw new BusinessException("DRONE_BATTERY_TOO_LOW", "Drone battery is too low for launch");
        }
    }

    private void assertFlightsNotSuspended() {
        if (rules.droneFlightsSuspended()) {
            throw new BusinessException("DRONE_FLIGHTS_SUSPENDED", "Drone flights are temporarily suspended");
        }
    }

    private void validateReservedDroneForLaunch(DroneUnitDto drone) {
        validateDroneReservation(drone);
        requireFlightBattery(drone);
    }

    private void validateDroneReservation(DroneUnitDto drone) {
        if (!Boolean.TRUE.equals(drone.active())) {
            throw new BusinessException("DRONE_INACTIVE", "Drone is inactive");
        }
        if (!"RESERVED".equals(drone.status())) {
            throw new BusinessException(
                    "DRONE_RESERVATION_LOST", "Drone is no longer reserved for this mission");
        }
    }

    private boolean isLoadingConfirmed(DroneMission mission) {
        return mission.getLoadedAt() != null
                && mission.getLoadedByUserId() != null
                && mission.getPayloadWeightGrams() != null
                && mission.getPayloadWeightGrams() > 0
                && StringUtils.hasText(mission.getSealCode())
                && mission.isParcelMatched()
                && mission.isPayloadSecured()
                && mission.isCompartmentLocked();
    }

    private void addJourneyEvent(
            Long orderId, String fromStage, String toStage, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setOldStatus(fromStage);
        history.setNewStatus(toStage);
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);
    }

    private void notifyDeliveryStageQuietly(LockerOrder order, String status, String eta) {
        try {
            Long receiver = order.getReceiverUserId() != null ? order.getReceiverUserId() : order.getUserId();
            notificationClient.notifyDeliveryStatus(
                    new DeliveryStatusNotificationRequest(order.getId(), receiver, status, eta));
        } catch (RuntimeException ignored) {
            // Trạng thái DB là nguồn sự thật; push/STOMP chỉ là kênh cập nhật nhanh.
        }
    }

    private void validateAssignedOperator(DroneMission mission, Long userId) {
        if (!Objects.equals(mission.getAssignedByUserId(), userId)) {
            throw new BusinessException(
                    "DRONE_MISSION_NOT_ASSIGNED_TO_USER",
                    "Only the drone technician who accepted this mission may operate it");
        }
    }

    private DroneUnitDto transitionDroneStatus(
            Long droneUnitId, String expectedStatus, String status, String reason) {
        return requireData(
                lockerDroneClient.transitionDroneStatus(
                        droneUnitId, new DroneStatusTransitionRequest(expectedStatus, status, reason)),
                "DRONE_STATUS_SYNC_FAILED");
    }

    private void releaseReservationIfHeld(Long droneUnitId) {
        DroneUnitDto drone = fetchDrone(droneUnitId);
        if ("RESERVED".equals(drone.status())) {
            transitionDroneStatus(droneUnitId, "RESERVED", "IDLE", null);
        } else if ("IN_FLIGHT".equals(drone.status())) {
            throw new BusinessException(
                    "DRONE_ALREADY_IN_FLIGHT", "An in-flight drone cannot be released by pre-launch cancellation");
        }
    }

    private void releaseReservationQuietly(Long droneUnitId) {
        try {
            transitionDroneStatus(droneUnitId, "RESERVED", "IDLE", null);
        } catch (RuntimeException ignored) {
            // Preserve a newer fleet state (for example FAULT) instead of overwriting it.
        }
    }

    private void validateLockerPreflight(LockerLayoutDto layout, String role) {
        if (!"ACTIVE".equals(layout.status())) {
            throw new BusinessException("LOCKER_INACTIVE", role + " locker is not active");
        }
        if (!Boolean.TRUE.equals(layout.landingPad())) {
            throw new BusinessException("LANDING_PAD_ABSENT", role + " locker has no landing pad");
        }
        if (!"OK".equals(layout.landingPadStatus())) {
            throw new BusinessException("LANDING_PAD_UNAVAILABLE", "Landing pad is not ready");
        }
    }

    private <T> T requireData(ApiResponse<T> response, String fallbackCode) {
        if (response == null || response.data() == null) {
            throw new BusinessException(fallbackCode, "Required downstream data is missing");
        }
        return response.data();
    }

    private DroneMissionResponse toResponse(LockerOrder order, DroneMission mission, DroneUnitDto drone) {
        return new DroneMissionResponse(
                order.getId(),
                mission == null ? null : mission.getId(),
                mission == null ? null : mission.getStatus(),
                order.getDeliveryStage(),
                mission == null ? null : mission.getDroneUnitId(),
                drone == null ? null : drone.code(),
                mission == null ? null : mission.getSourceLockerId(),
                mission == null ? requireDestinationLockerId(order) : mission.getDestinationLockerId(),
                order.getReservedBoxId(),
                order.getDescription(),
                mission == null ? null : mission.getAssignedByUserId(),
                order.getParcelWeightGrams(),
                mission == null ? null : mission.getPayloadWeightGrams(),
                mission == null ? null : mission.getSealCode(),
                mission == null ? null : mission.getLoadedByUserId(),
                mission == null ? null : mission.getLoadedAt(),
                mission == null ? null : mission.getReadyToLaunchAt(),
                mission == null ? null : mission.getWeightSurcharge(),
                order.getPaymentStatus());
    }

    private String cancelReasonLabel(Integer reasonCode) {
        return switch (reasonCode == null ? -1 : reasonCode) {
            case 1 -> "Weather";
            case 2 -> "Drone fault";
            case 3 -> "Landing pad unavailable";
            case 4 -> "Operational reason";
            case 5 -> "Other";
            default -> "Unknown";
        };
    }
}
