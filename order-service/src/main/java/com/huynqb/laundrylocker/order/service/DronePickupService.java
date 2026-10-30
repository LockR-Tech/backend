package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.MediaUpload;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.DeliveryStatusNotificationRequest;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.DronePickupOffer;
import com.huynqb.laundrylocker.order.model.DroneTechnicianPresence;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupOfferRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.DroneTechnicianPresenceRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lấy hàng tận nhà cho đơn drone.
 *
 * <p>Đơn được thanh toán ⇒ hệ thống mời KTV drone ĐANG RẢNH, GẦN ĐIỂM LẤY HÀNG NHẤT. Người đó nhận
 * thì tới nhà người gửi, đối chiếu kiện và nhập mã lấy hàng người gửi đọc cho, rồi mang kiện về tủ
 * gửi để nạp lên drone. Không nhận trong hạn (hoặc từ chối) thì mời người rảnh gần kế tiếp. Admin có
 * thể gán tay.
 *
 * <p>"Rảnh" = đang trong ca, app còn gửi vị trí, không đang được mời/đang đi lấy một kiện khác, không
 * đang cầm một kiện chưa tạo nhiệm vụ bay, và không có nhiệm vụ đang chờ nạp/chờ phóng/đang bay.
 *
 * <p>Thứ tự khoá ở mọi thao tác: đơn trước, lượt lấy hàng sau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DronePickupService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> HOLDING = List.of(DronePickup.OFFERED, DronePickup.ACCEPTED);
    /// Không gồm DROP_REPORTED: báo rơi kiện đã đóng nhiệm vụ (endedAt) và chuyển drone sang FAULT; việc thu hồi
    /// giao cho người khác qua sự cố. Tính cả nó thì KTV bị khoá tới khi sự cố đóng mà tab Điều phối không hiện lý do.
    private static final List<String> ACTIVE_MISSION = List.of(
            "AWAITING_LOADING", "READY_TO_LAUNCH", "LAUNCHING", "DEPARTED", "EN_ROUTE", "APPROACHING", "ARRIVED");
    private static final Set<String> SKIP_AFTER =
            Set.of(DronePickupOffer.DECLINED, DronePickupOffer.EXPIRED, DronePickupOffer.RELEASED);

    private final LockerOrderRepository orderRepository;
    private final DronePickupRepository pickupRepository;
    private final DronePickupOfferRepository offerRepository;
    private final DroneTechnicianPresenceRepository presenceRepository;
    private final DroneMissionRepository missionRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final NotificationClient notificationClient;
    private final DroneDeliveryFailureService failureService;
    private final com.huynqb.laundrylocker.order.client.UserClient userClient;
    private final OrderRules rules;
    private final CloudinaryMediaStorage mediaStorage;

    // ------------------------------------------------------------------ presence

    /// Trạng thái ca và vị trí KTV drone, kèm việc họ có đang rảnh không.
    public record Presence(boolean onDuty, Double latitude, Double longitude, LocalDateTime locationAt, boolean busy) {
    }

    @Transactional(readOnly = true)
    public Presence presence(Long technicianId) {
        DroneTechnicianPresence presence = presenceRepository.findById(technicianId).orElse(null);
        return toPresence(presence, technicianId);
    }

    /**
     * KTV vào/ra ca và gửi vị trí. Vào ca phải kèm vị trí. Ra ca khi đang được mời thì coi như từ
     * chối; đã nhận việc lấy hàng thì phải làm xong (hoặc báo không lấy được) mới ra ca được.
     */
    @Transactional
    public Presence updatePresence(Long technicianId, Boolean onDuty, Double latitude, Double longitude) {
        DroneTechnicianPresence presence = presenceRepository.findById(technicianId).orElseGet(() -> {
            DroneTechnicianPresence created = new DroneTechnicianPresence();
            created.setUserId(technicianId);
            return created;
        });
        LocalDateTime now = LocalDateTime.now();
        if (latitude != null && longitude != null) {
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new BusinessException("DRONE_TECHNICIAN_LOCATION_INVALID", "Latitude/longitude out of range");
            }
            presence.setLatitude(latitude);
            presence.setLongitude(longitude);
            presence.setLocationAt(now);
        }
        if (onDuty != null && onDuty != presence.isOnDuty()) {
            if (onDuty) {
                if (presence.getLatitude() == null || presence.getLongitude() == null) {
                    throw new BusinessException(
                            "DRONE_TECHNICIAN_LOCATION_REQUIRED", "Location is required to go on duty");
                }
            } else {
                if (pickupRepository.existsByTechnicianIdAndStatusIn(technicianId, List.of(DronePickup.ACCEPTED))) {
                    throw new BusinessException(
                            "DRONE_TECHNICIAN_HAS_ACTIVE_PICKUP",
                            "Finish or report the pickup you accepted before going off duty");
                }
                // Ra ca trước rồi mới trả lời mời: lượt mời kế tiếp không được coi người vừa ra ca là còn trong ca
                // (nếu không, người đó — vị trí mới, vừa "bỏ" lời mời — chặn nhánh dự phòng mời người vị trí cũ).
                presence.setOnDuty(false);
                presence.setDutyChangedAt(now);
                presenceRepository.saveAndFlush(presence);
                for (DronePickup offered : pickupRepository
                        .findByTechnicianIdAndStatusInOrderByCreatedAtDesc(technicianId, List.of(DronePickup.OFFERED))) {
                    declineLocked(lockOrder(offered.getOrderId()), technicianId, "ra ca");
                }
            }
            presence.setOnDuty(onDuty);
            presence.setDutyChangedAt(now);
        }
        presenceRepository.save(presence);
        if (presence.isOnDuty()) {
            // Có người vừa vào ca/vừa báo vị trí: các kiện đang chờ có thể mời được ngay.
            offerPending(now);
        }
        return toPresence(presence, technicianId);
    }

    private Presence toPresence(DroneTechnicianPresence presence, Long technicianId) {
        if (presence == null) {
            return new Presence(false, null, null, null, busy(technicianId));
        }
        return new Presence(presence.isOnDuty(), presence.getLatitude(), presence.getLongitude(),
                presence.getLocationAt(), busy(technicianId));
    }

    /// Xem định nghĩa "rảnh" ở đầu lớp.
    public boolean busy(Long technicianId) {
        // Do not trust a child row alone: the parent order may already be closed while cleanup was interrupted.
        // A terminal order must never keep a technician busy indefinitely.
        for (DronePickup holding : pickupRepository
                .findByTechnicianIdAndStatusInOrderByCreatedAtDesc(technicianId, HOLDING)) {
            LockerOrder order = orderRepository.findById(holding.getOrderId()).orElse(null);
            if (activeOrder(order)) {
                return true;
            }
        }
        for (var mission : missionRepository.findByAssignedByUserIdAndStatusIn(technicianId, ACTIVE_MISSION)) {
            LockerOrder order = orderRepository.findById(mission.getOrderId()).orElse(null);
            if (activeOrder(order)) {
                return true;
            }
        }
        // Đang cầm kiện đã lấy nhưng chưa tạo nhiệm vụ bay.
        for (DronePickup carrying : pickupRepository
                .findByTechnicianIdAndStatusInOrderByCreatedAtDesc(technicianId, List.of(DronePickup.PICKED_UP))) {
            LockerOrder order = orderRepository.findById(carrying.getOrderId()).orElse(null);
            if (order != null && "PICKED_UP".equals(order.getDeliveryStage())) {
                return true;
            }
        }
        return false;
    }

    public Set<Long> getBusyDroneTechnicianIds() {
        Set<Long> busyIds = new HashSet<>();
        for (DronePickup holding : pickupRepository.findByStatusInOrderByCreatedAtAsc(HOLDING)) {
            if (holding.getTechnicianId() != null && busy(holding.getTechnicianId())) {
                busyIds.add(holding.getTechnicianId());
            }
        }
        for (DroneMission mission : missionRepository.findByStatusIn(ACTIVE_MISSION)) {
            if (mission.getAssignedByUserId() != null && busy(mission.getAssignedByUserId())) {
                busyIds.add(mission.getAssignedByUserId());
            }
        }
        for (DronePickup carrying : pickupRepository.findByStatusInOrderByCreatedAtAsc(List.of(DronePickup.PICKED_UP))) {
            if (carrying.getTechnicianId() != null && busy(carrying.getTechnicianId())) {
                busyIds.add(carrying.getTechnicianId());
            }
        }
        return busyIds;
    }

    private static boolean activeOrder(LockerOrder order) {
        if (order == null) {
            return false;
        }
        String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase();
        String stage = order.getDeliveryStage() == null ? "" : order.getDeliveryStage().toUpperCase();
        return !Set.of("COMPLETED", "CANCELED", "EXPIRED").contains(status)
                && !Set.of("READY_FOR_PICKUP", "CANCELED", "FAILED").contains(stage);
    }

    // ------------------------------------------------------------------ dispatch

    /// Đơn vừa được thanh toán đủ: mở lượt lấy hàng và mời KTV rảnh gần nhất. Gọi lại không tạo trùng.
    ///
    /// Giao dịch RIÊNG: hàm này được gọi trong `afterCommit` của giao dịch ghi nhận thanh toán. Lúc
    /// đó giao dịch cũ đã commit nhưng còn gắn vào luồng — tham gia vào nó thì mọi thứ ghi ở đây
    /// không bao giờ được commit (lượt lấy hàng biến mất, không ai được mời).
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void startForPaidOrder(Long orderId) {
        LockerOrder order = lockOrder(orderId);
        if (!awaitingPickup(order) || !"PAID".equalsIgnoreCase(order.getPaymentStatus())
                || order.getPickupLatitude() == null || order.getPickupLongitude() == null
                || pickupRepository.findByOrderId(orderId).isPresent()) {
            return;
        }
        DronePickup pickup = new DronePickup();
        pickup.setOrderId(orderId);
        pickup.setStatus(DronePickup.SEARCHING);
        pickup.setPickupCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        pickup = pickupRepository.saveAndFlush(pickup);
        addHistory(order, "AWAITING_DISPATCH", "AWAITING_DISPATCH", null,
                "Đã thanh toán — đang tìm kỹ thuật viên drone gần điểm lấy hàng nhất");
        if (!offerNext(order, pickup, LocalDateTime.now())) {
            notifyQuietly(order.getUserId(), "Đang tìm kỹ thuật viên",
                    "Đơn " + order.getOrderCode() + " đã thanh toán. Hiện chưa có kỹ thuật viên rảnh gần bạn, "
                            + "hệ thống sẽ tiếp tục tìm.",
                    order, "DRONE_DELIVERY_STATUS_CHANGED");
        }
    }

    /// Mời lại cho mọi lượt đang chờ (job định kỳ, hoặc khi có KTV vừa vào ca).
    @Transactional
    public int offerPending(LocalDateTime now) {
        int offered = 0;
        for (DronePickup waiting : pickupRepository.findByStatusInOrderByCreatedAtAsc(List.of(DronePickup.SEARCHING))) {
            LockerOrder order = lockOrder(waiting.getOrderId());
            DronePickup pickup = pickupRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
            if (pickup == null || !DronePickup.SEARCHING.equals(pickup.getStatus())) {
                continue;
            }
            if (!awaitingPickup(order)) {
                continue;
            }
            if (offerNext(order, pickup, now)) {
                offered++;
            }
        }
        return offered;
    }

    /**
     * Job định kỳ: lời mời quá hạn ⇒ mời người kế tiếp; KTV đã nhận mà quá hạn chưa lấy ⇒ thu hồi và
     * mời người khác; lượt đang chờ ⇒ thử mời lại.
     */
    @Transactional
    public void sweep(LocalDateTime now) {
        for (DronePickup stale : pickupRepository.findByStatusInOrderByCreatedAtAsc(HOLDING)) {
            LockerOrder order = lockOrder(stale.getOrderId());
            DronePickup pickup = pickupRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
            if (pickup == null) {
                continue;
            }
            if (DronePickup.OFFERED.equals(pickup.getStatus())
                    && pickup.getOfferExpiresAt() != null
                    && !pickup.getOfferExpiresAt().isAfter(now)) {
                Long technicianId = pickup.getTechnicianId();
                closeOffer(pickup, technicianId, DronePickupOffer.EXPIRED, now);
                backToSearching(pickup);
                addHistory(order, order.getDeliveryStage(), order.getDeliveryStage(), null,
                        "Kỹ thuật viên được mời không trả lời trong hạn — mời người kế tiếp");
                escalateIfNobodyTakesIt(order, pickup);
                offerNext(order, pickup, now);
            } else if (DronePickup.ACCEPTED.equals(pickup.getStatus())
                    && rules.dronePickupTimeoutMinutes() > 0
                    && pickup.getAcceptedAt() != null
                    && !pickup.getAcceptedAt().plusMinutes(rules.dronePickupTimeoutMinutes()).isAfter(now)) {
                Long technicianId = pickup.getTechnicianId();
                closeOffer(pickup, technicianId, DronePickupOffer.RELEASED, now);
                backToSearching(pickup);
                String previousStage = order.getDeliveryStage();
                order.setDeliveryStage("AWAITING_DISPATCH");
                order.setStaffId(null);
                orderRepository.save(order);
                addHistory(order, previousStage, "AWAITING_DISPATCH", null,
                        "Kỹ thuật viên đã nhận nhưng quá " + rules.dronePickupTimeoutMinutes()
                                + " phút chưa tới lấy hàng — chuyển cho người khác");
                notifyQuietly(technicianId, "Việc lấy hàng đã được thu hồi",
                        "Đơn " + order.getOrderCode() + " quá hạn lấy hàng nên đã chuyển cho kỹ thuật viên khác.",
                        order, "DRONE_ORDER_CREATED");
                offerNext(order, pickup, now);
            }
        }
        offerPending(now);
    }

    /// Mời KTV rảnh gần điểm lấy hàng nhất; false khi lúc này không mời được ai.
    private boolean offerNext(LockerOrder order, DronePickup pickup, LocalDateTime now) {
        // Đơn đang gặp sự cố (tạm dừng bay, tủ/bãi đáp hỏng…) thì chưa cho ai đi lấy: lấy về cũng
        // không phóng được. Hết sự cố, lượt quét kế tiếp mời lại.
        if (order.getDeliveryBlockReason() != null) {
            return false;
        }
        LocalDateTime freshAfter = now.minusMinutes(rules.droneTechnicianLocationMaxAgeMinutes());
        // Người vừa từ chối hoặc để lời mời quá hạn không bị mời lại cùng lần lấy hàng trong khoảng này.
        LocalDateTime cooldownAfter = now.minusMinutes(rules.dronePickupReofferCooldownMinutes());
        Set<Long> recentlyPassed = new HashSet<>();
        for (DronePickupOffer offer : offerRepository.findByPickupId(pickup.getId())) {
            if (SKIP_AFTER.contains(offer.getStatus())
                    && offer.getRespondedAt() != null
                    && offer.getRespondedAt().isAfter(cooldownAfter)) {
                recentlyPassed.add(offer.getTechnicianId());
            }
        }
        double[] pickupPoint = {order.getPickupLatitude(), order.getPickupLongitude()};
        // KTV ở xa hơn bán kính lấy hàng tận nhà thì không mời (test 2026-10-29: mời người cách 15,9 km).
        int maxDistanceMeters = rules.droneMaxPickupMeters();
        List<Candidate> candidates = presenceRepository.findByOnDutyTrue().stream()
                .filter(presence -> presence.getLatitude() != null && presence.getLongitude() != null)
                .filter(presence -> presence.getLocationAt() != null && presence.getLocationAt().isAfter(freshAfter))
                .filter(presence -> !recentlyPassed.contains(presence.getUserId()))
                .map(presence -> new Candidate(presence.getUserId(), (int) Math.round(DroneTelemetryService.distanceM(
                        pickupPoint, presence.getLatitude(), presence.getLongitude()))))
                .filter(candidate -> maxDistanceMeters <= 0 || candidate.distanceMeters() <= maxDistanceMeters)
                .sorted(Comparator.comparingInt(Candidate::distanceMeters).thenComparing(Candidate::technicianId))
                .toList();
        boolean freshPassedNearby = presenceRepository.findByOnDutyTrue().stream()
                .anyMatch(presence -> recentlyPassed.contains(presence.getUserId())
                        && presence.getLocationAt() != null && presence.getLocationAt().isAfter(freshAfter));
        if (candidates.isEmpty() && !freshPassedNearby) {
            // Khi KTV dang trong ca truc nhung chua co toa do moi trong 10 phut (KTV cho lenh tai tram,
            // thiet bi o che do cho), uu tien dieu phoi cho KTV on-duty san co thay vi de don ket o SEARCHING.
            // Vẫn giữ trong bán kính; có KTV vị trí mới vừa bỏ lời mời thì chờ hết thời gian chờ mời lại
            // người đó thay vì mời người vị trí cũ (có thể đã ở rất xa).
            candidates = presenceRepository.findByOnDutyTrue().stream()
                    .filter(presence -> presence.getLatitude() != null && presence.getLongitude() != null)
                    .filter(presence -> !recentlyPassed.contains(presence.getUserId()))
                    .map(presence -> new Candidate(presence.getUserId(), (int) Math.round(DroneTelemetryService.distanceM(
                            pickupPoint, presence.getLatitude(), presence.getLongitude()))))
                    .filter(candidate -> maxDistanceMeters <= 0 || candidate.distanceMeters() <= maxDistanceMeters)
                    .sorted(Comparator.comparingInt(Candidate::distanceMeters).thenComparing(Candidate::technicianId))
                    .toList();
        }
        for (Candidate candidate : candidates) {
            // Khoá dòng KTV: hai đơn được trả tiền cùng lúc không mời trùng một người.
            DroneTechnicianPresence locked =
                    presenceRepository.findByUserIdForUpdate(candidate.technicianId()).orElse(null);
            if (locked == null || !locked.isOnDuty() || busy(candidate.technicianId())) {
                continue;
            }
            pickup.setStatus(DronePickup.OFFERED);
            pickup.setTechnicianId(candidate.technicianId());
            pickup.setTechnicianDistanceMeters(candidate.distanceMeters());
            pickup.setOfferedAt(now);
            pickup.setOfferExpiresAt(now.plusSeconds(rules.dronePickupOfferSeconds()));
            pickup.setAcceptedAt(null);
            pickup.setAssignedByUserId(null);
            pickupRepository.saveAndFlush(pickup);

            DronePickupOffer offer = new DronePickupOffer();
            offer.setPickupId(pickup.getId());
            offer.setTechnicianId(candidate.technicianId());
            offer.setStatus(DronePickupOffer.OFFERED);
            offer.setDistanceMeters(candidate.distanceMeters());
            offer.setOfferedAt(now);
            offerRepository.save(offer);

            notifyQuietly(candidate.technicianId(), "Có đơn drone cần lấy hàng",
                    "Đơn " + order.getOrderCode() + " cách bạn " + distanceLabel(candidate.distanceMeters())
                            + " tại " + order.getPickupAddress() + ". Nhận trong "
                            + rules.dronePickupOfferSeconds() + " giây.",
                    order, "DRONE_ORDER_CREATED");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ technician actions

    @Transactional
    public void accept(Long orderId, Long technicianId) {
        LockerOrder order = lockOrder(orderId);
        DronePickup pickup = lockPickup(orderId);
        requireOffered(pickup, technicianId);
        LocalDateTime now = LocalDateTime.now();
        if (pickup.getOfferExpiresAt() != null && !pickup.getOfferExpiresAt().isAfter(now)) {
            throw new BusinessException("DRONE_PICKUP_OFFER_EXPIRED", "This pickup offer has expired");
        }
        if (!awaitingPickup(order)) {
            throw new BusinessException("DRONE_ORDER_STATUS_INVALID", "Drone order is no longer waiting for pickup");
        }
        if (order.getDeliveryBlockReason() != null) {
            throw new BusinessException(
                    "DRONE_ORDER_BLOCKED", "This drone order is on hold because of an incident; do not pick it up yet");
        }
        closeOffer(pickup, technicianId, DronePickupOffer.ACCEPTED, now);
        pickup.setStatus(DronePickup.ACCEPTED);
        pickup.setAcceptedAt(now);
        pickup.setOfferExpiresAt(null);
        pickupRepository.save(pickup);
        assign(order, technicianId, "Kỹ thuật viên nhận lấy hàng, cách điểm lấy "
                + distanceLabel(pickup.getTechnicianDistanceMeters()));
    }

    @Transactional
    public void decline(Long orderId, Long technicianId) {
        declineLocked(lockOrder(orderId), technicianId, "từ chối");
    }

    private void declineLocked(LockerOrder order, Long technicianId, String why) {
        DronePickup pickup = lockPickup(order.getId());
        requireOffered(pickup, technicianId);
        LocalDateTime now = LocalDateTime.now();
        closeOffer(pickup, technicianId, DronePickupOffer.DECLINED, now);
        backToSearching(pickup);
        addHistory(order, order.getDeliveryStage(), order.getDeliveryStage(), technicianId,
                "Kỹ thuật viên được mời " + why + " — mời người kế tiếp");
        escalateIfNobodyTakesIt(order, pickup);
        offerNext(order, pickup, now);
    }

    /// KTV tới nhà người gửi và đã cầm kiện: đúng mã lấy hàng người gửi đọc, đúng kiện đã khai báo.
    @Transactional
    public void confirmPickedUp(Long orderId, Long technicianId, String pickupCode, boolean parcelMatched, String note) {
        confirmPickedUp(orderId, technicianId, pickupCode, parcelMatched, note, null);
    }

    @Transactional
    public void confirmPickedUp(
            Long orderId, Long technicianId, String pickupCode, boolean parcelMatched, String note, MediaUpload pickupPhoto) {
        LockerOrder order = lockOrder(orderId);
        DronePickup pickup = lockPickup(orderId);
        requireAccepted(pickup, technicianId);
        if (!"PICKUP_ASSIGNED".equals(order.getDeliveryStage()) || !"AWAITING_DISPATCH".equals(order.getStatus())) {
            throw new BusinessException("DRONE_ORDER_STATUS_INVALID", "Drone order is not waiting for pickup");
        }
        if (!StringUtils.hasText(pickupCode) || !pickup.getPickupCode().equals(pickupCode.trim())) {
            throw new BusinessException("DRONE_PICKUP_CODE_INVALID", "Pickup code does not match this order");
        }
        if (!parcelMatched) {
            throw new BusinessException(
                    "DRONE_PICKUP_PARCEL_NOT_VERIFIED",
                    "Confirm the parcel matches the declaration, or report the pickup as failed");
        }
        assertNearPickupPoint(order, technicianId);
        var verifiedPhoto = pickupPhoto == null
                ? null
                : mediaStorage.verify(pickupPhoto, MediaPurpose.DRONE_PICKUP, technicianId);
        LocalDateTime now = LocalDateTime.now();
        pickup.setStatus(DronePickup.PICKED_UP);
        pickup.setPickedUpAt(now);
        pickup.setPickupNote(StringUtils.hasText(note) ? note.trim() : null);
        if (verifiedPhoto != null) {
            pickup.setPickupPhotoPublicId(verifiedPhoto.publicId());
            pickup.setPickupPhotoUrl(verifiedPhoto.secureUrl());
            pickup.setPickupPhotoCapturedAt(now);
        }
        pickupRepository.save(pickup);
        // Kiện rời tay người gửi: từ đây đơn đóng thì kiện phải được mang trả.
        order.setParcelDroppedAt(now);
        order.setDeliveryStage("PICKED_UP");
        order.setStaffId(technicianId);
        orderRepository.save(order);
        addHistory(order, "PICKUP_ASSIGNED", "PICKED_UP", technicianId,
                "Đã lấy hàng tại " + order.getPickupAddress() + " — mang tới drone để nạp trực tiếp, không bỏ vào ô tủ bay"
                        + (pickup.getPickupNote() == null ? "" : " · " + pickup.getPickupNote()));
        notifyStage(order, order.getUserId(), "picked_up");
        notifyQuietly(order.getUserId(), "Đã lấy hàng",
                "Kỹ thuật viên đã lấy kiện của đơn " + order.getOrderCode()
                        + " và đang mang tới drone để nạp trực tiếp; kiện không cần bỏ vào ô tủ bay.",
                order, "DRONE_DELIVERY_STATUS_CHANGED");
    }

    /// "Đã lấy hàng" chỉ hợp lệ khi KTV đang đứng ở điểm lấy hàng: GPS ca trực còn hiệu lực và không xa quá mức admin
    /// đặt. Không có GPS mới thì yêu cầu cập nhật lại, thay vì tin lời xác nhận từ xa.
    private void assertNearPickupPoint(LockerOrder order, Long technicianId) {
        int maxDistance = rules.dronePickupConfirmMaxDistanceMeters();
        if (maxDistance <= 0 || order.getPickupLatitude() == null || order.getPickupLongitude() == null) {
            return;
        }
        DroneTechnicianPresence presence = presenceRepository.findById(technicianId).orElse(null);
        LocalDateTime freshAfter = LocalDateTime.now().minusMinutes(rules.droneTechnicianLocationMaxAgeMinutes());
        if (presence == null || presence.getLatitude() == null || presence.getLongitude() == null
                || presence.getLocationAt() == null || !presence.getLocationAt().isAfter(freshAfter)) {
            throw new BusinessException("DRONE_PICKUP_LOCATION_STALE",
                    "Chưa có vị trí GPS mới của bạn — bấm \"Cập nhật lại GPS\" tại điểm lấy hàng rồi xác nhận lại");
        }
        int distance = (int) Math.round(DroneTelemetryService.distanceM(
                new double[] {order.getPickupLatitude(), order.getPickupLongitude()},
                presence.getLatitude(), presence.getLongitude()));
        if (distance > maxDistance) {
            throw new BusinessException("DRONE_PICKUP_TOO_FAR",
                    "Bạn đang cách điểm lấy hàng " + distanceLabel(distance) + " (tối đa " + maxDistance
                            + " m) — tới đúng điểm lấy hàng rồi xác nhận");
        }
    }

    /**
     * KTV tới nơi nhưng không lấy được kiện (khách vắng, kiện sai khai báo, không tìm thấy địa chỉ).
     * Đơn kết thúc "giao không hoàn tất"; nơi gọi tạo yêu cầu hoàn tiền sau khi commit.
     */
    @Transactional
    public void failPickup(Long orderId, Long technicianId, String reason, String note) {
        LockerOrder order = lockOrder(orderId);
        DronePickup pickup = lockPickup(orderId);
        requireAccepted(pickup, technicianId);
        if (!DroneFailureReasons.PICKUP_FAILURES.contains(reason)) {
            throw new BusinessException("DRONE_FAILURE_REASON_INVALID", "Not a pickup failure reason: " + reason);
        }
        String trimmed = StringUtils.hasText(note) ? note.trim() : null;
        if (DroneFailureReasons.OTHER.equals(reason) && trimmed == null) {
            throw new BusinessException("DRONE_CANCEL_NOTE_REQUIRED", "A note is required when reason is OTHER");
        }
        // Đóng đơn trước (lúc này KTV còn là người đang giữ việc nên được phép), rồi mới ghi lượt
        // lấy hàng là FAILED thay cho CANCELED mà bước đóng đơn đặt.
        failureService.fail(
                order.getId(), technicianId, DroneDeliveryFailureService.Actor.TECHNICIAN, reason, trimmed, null);
        pickup.setStatus(DronePickup.FAILED);
        pickup.setFailedAt(LocalDateTime.now());
        pickup.setFailureReason(reason);
        pickup.setFailureNote(trimmed);
        pickupRepository.save(pickup);
    }

    /// Admin gán tay một KTV đang trong ca và rảnh, thay cho việc hệ thống tự mời.
    @Transactional
    public void assignByAdmin(Long orderId, Long technicianId, Long adminUserId) {
        LockerOrder order = lockOrder(orderId);
        DronePickup pickup = lockPickup(orderId);
        if (DronePickup.PICKED_UP.equals(pickup.getStatus()) || !awaitingPickup(order)) {
            throw new BusinessException(
                    "DRONE_ORDER_STATUS_INVALID", "A technician can only be assigned before the parcel is picked up");
        }
        if (Objects.equals(pickup.getTechnicianId(), technicianId) && DronePickup.ACCEPTED.equals(pickup.getStatus())) {
            return;
        }
        DroneTechnicianPresence presence = presenceRepository.findByUserIdForUpdate(technicianId).orElse(null);
        if (presence == null || !presence.isOnDuty()) {
            throw new BusinessException("DRONE_TECHNICIAN_OFF_DUTY", "The selected drone technician is not on duty");
        }
        LocalDateTime now = LocalDateTime.now();
        Long previous = pickup.holdsTechnician() ? pickup.getTechnicianId() : null;
        if (previous != null) {
            closeOffer(pickup, previous, DronePickupOffer.RELEASED, now);
            backToSearching(pickup);
            if (!previous.equals(technicianId)) {
                notifyQuietly(previous, "Việc lấy hàng đã được chuyển",
                        "Quản trị viên đã chuyển đơn " + order.getOrderCode() + " cho kỹ thuật viên khác.",
                        order, "DRONE_ORDER_CREATED");
            }
        }
        if (busy(technicianId)) {
            throw new BusinessException("DRONE_TECHNICIAN_BUSY", "The selected drone technician is busy with another job");
        }
        Integer distance = presence.getLatitude() == null || presence.getLongitude() == null
                ? null
                : (int) Math.round(DroneTelemetryService.distanceM(
                        new double[] {order.getPickupLatitude(), order.getPickupLongitude()},
                        presence.getLatitude(), presence.getLongitude()));
        pickup.setStatus(DronePickup.ACCEPTED);
        pickup.setTechnicianId(technicianId);
        pickup.setTechnicianDistanceMeters(distance);
        pickup.setOfferedAt(now);
        pickup.setAcceptedAt(now);
        pickup.setOfferExpiresAt(null);
        pickup.setAssignedByUserId(adminUserId);
        pickupRepository.save(pickup);

        DronePickupOffer offer = new DronePickupOffer();
        offer.setPickupId(pickup.getId());
        offer.setTechnicianId(technicianId);
        offer.setStatus(DronePickupOffer.ACCEPTED);
        offer.setDistanceMeters(distance);
        offer.setOfferedAt(now);
        offer.setRespondedAt(now);
        offerRepository.save(offer);

        assign(order, technicianId, "Quản trị viên gán kỹ thuật viên lấy hàng");
        notifyQuietly(technicianId, "Bạn được gán lấy hàng",
                "Quản trị viên gán bạn lấy kiện của đơn " + order.getOrderCode() + " tại " + order.getPickupAddress() + ".",
                order, "DRONE_ORDER_CREATED");
    }

    /// Mọi KTV drone cho admin chọn gán tay: đang trong ca, rảnh và gần điểm lấy hàng nhất lên đầu.
    @Transactional(readOnly = true)
    public List<com.huynqb.laundrylocker.order.dto.DroneTechnicianCandidate> candidates(Long orderId) {
        LockerOrder order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        java.util.Map<Long, DroneTechnicianPresence> presences = new java.util.HashMap<>();
        presenceRepository.findAll().forEach(presence -> presences.put(presence.getUserId(), presence));
        java.util.Map<Long, com.huynqb.laundrylocker.common.dto.UserSummary> users = new java.util.LinkedHashMap<>();
        try {
            var response = userClient.getUsersByRole("DRONE_TECHNICIAN");
            if (response != null && response.data() != null) {
                response.data().forEach(user -> users.put(user.id(), user));
            }
        } catch (RuntimeException ex) {
            log.debug("Drone technician lookup failed: {}", ex.getMessage());
        }
        Set<Long> ids = new java.util.LinkedHashSet<>(users.keySet());
        ids.addAll(presences.keySet());
        LocalDateTime freshAfter = LocalDateTime.now().minusMinutes(rules.droneTechnicianLocationMaxAgeMinutes());
        List<com.huynqb.laundrylocker.order.dto.DroneTechnicianCandidate> result = new java.util.ArrayList<>();
        for (Long id : ids) {
            DroneTechnicianPresence presence = presences.get(id);
            var user = users.get(id);
            boolean hasLocation = presence != null && presence.getLatitude() != null && presence.getLongitude() != null;
            Integer distance = hasLocation && order.getPickupLatitude() != null && order.getPickupLongitude() != null
                    ? (int) Math.round(DroneTelemetryService.distanceM(
                            new double[] {order.getPickupLatitude(), order.getPickupLongitude()},
                            presence.getLatitude(), presence.getLongitude()))
                    : null;
            result.add(new com.huynqb.laundrylocker.order.dto.DroneTechnicianCandidate(
                    id,
                    user == null ? null : user.fullName(),
                    user == null ? null : user.phoneNumber(),
                    presence != null && presence.isOnDuty(),
                    busy(id),
                    presence != null && presence.getLocationAt() != null && presence.getLocationAt().isAfter(freshAfter),
                    distance,
                    presence == null ? null : presence.getLocationAt()));
        }
        result.sort(Comparator
                .comparing((com.huynqb.laundrylocker.order.dto.DroneTechnicianCandidate c) -> !(c.onDuty() && !c.busy()))
                .thenComparing(c -> c.distanceMeters() == null ? Integer.MAX_VALUE : c.distanceMeters()));
        return result;
    }

    /// Đơn đóng trước khi lấy hàng (khách huỷ, tự huỷ…): nhả KTV đang được mời/đã nhận.
    @Transactional
    public void closeForOrder(Long orderId) {
        DronePickup pickup = pickupRepository.findByOrderId(orderId).orElse(null);
        Long technicianId = pickup != null && pickup.holdsTechnician() ? pickup.getTechnicianId() : null;
        failureService.closePickupForOrder(orderId);
        if (technicianId != null) {
            LockerOrder order = orderRepository.findById(orderId).orElse(null);
            if (order != null) {
                notifyQuietly(technicianId, "Đơn drone đã huỷ",
                        "Đơn " + order.getOrderCode() + " đã huỷ, không cần tới lấy hàng nữa.",
                        order, "DRONE_ORDER_CREATED");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void assign(LockerOrder order, Long technicianId, String note) {
        String previousStage = order.getDeliveryStage();
        order.setDeliveryStage("PICKUP_ASSIGNED");
        order.setStaffId(technicianId);
        orderRepository.save(order);
        addHistory(order, previousStage, "PICKUP_ASSIGNED", technicianId, note);
        notifyStage(order, order.getUserId(), "pickup_assigned");
        notifyQuietly(order.getUserId(), "Kỹ thuật viên đang tới lấy hàng",
                "Đơn " + order.getOrderCode() + ": kỹ thuật viên đang tới " + order.getPickupAddress()
                        + ". Đọc mã lấy hàng trong đơn cho kỹ thuật viên khi giao kiện.",
                order, "DRONE_DELIVERY_STATUS_CHANGED");
    }

    /// Đơn đã trả tiền, chưa có KTV nhận (hoặc người nhận vừa bị thu hồi).
    private static boolean awaitingPickup(LockerOrder order) {
        return "DRONE_DELIVERY".equals(order.getType())
                && "AWAITING_DISPATCH".equals(order.getStatus())
                && ("AWAITING_DISPATCH".equals(order.getDeliveryStage())
                        || "PICKUP_ASSIGNED".equals(order.getDeliveryStage()));
    }

    private void backToSearching(DronePickup pickup) {
        pickup.setStatus(DronePickup.SEARCHING);
        pickup.setTechnicianId(null);
        pickup.setTechnicianDistanceMeters(null);
        pickup.setOfferedAt(null);
        pickup.setOfferExpiresAt(null);
        pickup.setAcceptedAt(null);
        pickup.setAssignedByUserId(null);
        pickupRepository.saveAndFlush(pickup);
    }

    private void closeOffer(DronePickup pickup, Long technicianId, String status, LocalDateTime now) {
        if (technicianId == null) {
            return;
        }
        for (String open : List.of(DronePickupOffer.OFFERED, DronePickupOffer.ACCEPTED)) {
            offerRepository
                    .findFirstByPickupIdAndTechnicianIdAndStatusOrderByIdDesc(pickup.getId(), technicianId, open)
                    .ifPresent(offer -> {
                        if (!status.equals(offer.getStatus())) {
                            offer.setStatus(status);
                            offer.setRespondedAt(now);
                            offerRepository.save(offer);
                        }
                    });
            if (DronePickupOffer.ACCEPTED.equals(status)) {
                break; // chỉ đổi lời mời đang OFFERED sang ACCEPTED
            }
        }
    }

    private static void requireOffered(DronePickup pickup, Long technicianId) {
        if (!DronePickup.OFFERED.equals(pickup.getStatus()) || !Objects.equals(pickup.getTechnicianId(), technicianId)) {
            throw new BusinessException("DRONE_PICKUP_NOT_OFFERED", "This pickup is not offered to you");
        }
    }

    private static void requireAccepted(DronePickup pickup, Long technicianId) {
        if (!DronePickup.ACCEPTED.equals(pickup.getStatus()) || !Objects.equals(pickup.getTechnicianId(), technicianId)) {
            throw new BusinessException(
                    "DRONE_PICKUP_NOT_ASSIGNED_TO_USER", "This pickup is not assigned to you");
        }
    }

    private LockerOrder lockOrder(Long orderId) {
        LockerOrder order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!"DRONE_DELIVERY".equals(order.getType())) {
            throw new BusinessException("DRONE_ORDER_REQUIRED", "Order is not a drone delivery order");
        }
        return order;
    }

    private DronePickup lockPickup(Long orderId) {
        return pickupRepository
                .findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        "DRONE_PICKUP_NOT_FOUND", "This drone order has no pickup yet (it may be unpaid)"));
    }

    private void addHistory(LockerOrder order, String fromStage, String toStage, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(fromStage);
        history.setNewStatus(toStage);
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);
    }

    private void notifyStage(LockerOrder order, Long userId, String status) {
        if (userId == null) {
            return;
        }
        try {
            notificationClient.notifyDeliveryStatus(
                    new DeliveryStatusNotificationRequest(order.getId(), userId, status, null));
        } catch (RuntimeException ignored) {
            // Trạng thái DB là nguồn sự thật; push chỉ là kênh cập nhật nhanh.
        }
    }

    /// Lượt mời thứ {@value #ESCALATE_AFTER_MISSED_OFFERS} hết hạn / bị từ chối mà chưa ai nhận ⇒ báo admin một lần
    /// để giao tay. Trước đây đơn xoay vòng mời → hết hạn → mời lại mãi, admin chỉ biết khi tự mở trang đơn
    /// (test 2026-10-29, #143 mời lại 2 lần trong 30 phút).
    static final int ESCALATE_AFTER_MISSED_OFFERS = 2;

    private void escalateIfNobodyTakesIt(LockerOrder order, DronePickup pickup) {
        long missed = offerRepository.findByPickupId(pickup.getId()).stream()
                .filter(offer -> DronePickupOffer.EXPIRED.equals(offer.getStatus())
                        || DronePickupOffer.DECLINED.equals(offer.getStatus()))
                .count();
        if (missed != ESCALATE_AFTER_MISSED_OFFERS) {
            return;
        }
        addHistory(order, order.getDeliveryStage(), order.getDeliveryStage(), null,
                "Đã mời " + missed + " lượt chưa kỹ thuật viên nào nhận — đã báo quản trị viên giao tay");
        try {
            var admins = userClient.getUsersByRole("ADMIN");
            if (admins == null || admins.data() == null) {
                return;
            }
            for (var admin : admins.data()) {
                notifyQuietly(admin.id(), "Đơn drone chưa có KTV lấy hàng",
                        "Đơn " + order.getOrderCode() + " đã mời " + missed
                                + " lượt mà chưa kỹ thuật viên drone nào nhận. Mở Hành trình drone để gán KTV.",
                        order, "DRONE_DELIVERY_STATUS_CHANGED");
            }
        } catch (RuntimeException ex) {
            log.debug("Could not notify admins about unclaimed pickup of order {}: {}", order.getId(), ex.getMessage());
        }
    }

    private void notifyQuietly(Long userId, String title, String message, LockerOrder order, String type) {
        if (userId == null) {
            return;
        }
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(userId, title, message, type, order.getId(), "ORDER"));
        } catch (RuntimeException ignored) {
            // Trạng thái DB là nguồn sự thật; thông báo chỉ là kênh báo nhanh.
        }
    }

    static String distanceLabel(Integer meters) {
        if (meters == null) {
            return "không rõ khoảng cách";
        }
        return meters < 1000 ? meters + " m" : String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000.0);
    }

    private record Candidate(Long technicianId, int distanceMeters) {
    }
}
