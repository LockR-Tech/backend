package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.dto.LockerLayoutDto;
import com.huynqb.laundrylocker.order.dto.OpenReportDto;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Nối phiếu sự cố với đơn drone: đơn đã thanh toán có còn phục vụ được không.
 *
 * <p>Một đơn "đang gặp sự cố" khi: admin tạm dừng bay (thời tiết), tủ nhận hoặc ô nhận hỏng, bãi đáp
 * không sẵn sàng, tủ gửi hỏng hoặc hết drone dùng được, drone của nhiệm vụ lỗi, hoặc đã lấy hàng mà quá
 * hạn chưa phóng. Trạng thái tài sản là nguồn sự thật (drone `FAULT`, tủ `MAINTENANCE`, bãi đáp khác
 * `OK` — chính các phiếu sự cố của locker-service đặt chúng), nên luồng này hỏi locker-service thay vì
 * chờ sự kiện: không lỡ phiếu nào, và phiếu đóng thì đơn tự hết chặn.
 *
 * <p>Trước khi coi đơn bị chặn vì tủ gửi, hệ thống thử đổi sang tủ gửi khác gần người gửi. Đơn bị
 * chặn được báo cho người gửi, KTV đang giữ việc và admin; quá hạn mà không ai xử lý thì
 * {@link DroneDeliveryFailureService} đóng đơn "giao không hoàn tất" và hoàn tiền.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneDeliveryBlockService {

    private static final Set<String> BEFORE_MISSION = Set.of("AWAITING_DISPATCH", "PICKUP_ASSIGNED", "PICKED_UP");
    private static final Set<String> USABLE_BOX_STATUSES = Set.of("AVAILABLE", "RESERVED", "OCCUPIED");

    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final DronePickupRepository pickupRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final LockerClient lockerClient;
    private final LockerDroneClient lockerDroneClient;
    private final NotificationClient notificationClient;
    private final UserClient userClient;
    private final DroneSourceLockerSelector sourceLockerSelector;
    private final OrderRules rules;

    public enum Verdict {
        /// Không đổi gì (vẫn bình thường, hoặc vẫn bị chặn và chưa quá hạn).
        UNCHANGED,
        /// Vừa bị chặn hoặc lý do chặn vừa đổi.
        BLOCKED,
        /// Sự cố đã hết, đơn chạy tiếp.
        CLEARED,
        /// Bị chặn quá hạn mà chưa ai xử lý — nơi gọi đóng đơn "giao không hoàn tất".
        EXPIRED
    }

    public record Review(Verdict verdict, String reason, Long ticketId, String note) {
        static final Review UNCHANGED = new Review(Verdict.UNCHANGED, null, null, null);
    }

    /// Kết quả tra locker-service dùng chung cho một lượt quét (không hỏi lại theo từng đơn).
    public static final class Snapshot {
        private final Map<Long, LockerLayoutDto> layouts = new HashMap<>();
        private final Map<Long, List<DroneUnitDto>> dronesByLocker = new HashMap<>();
        private final Map<Long, DroneUnitDto> drones = new HashMap<>();
        private List<OpenReportDto> reports;
    }

    private record Cause(String reason, Long ticketId, String note) {
    }

    /// Không tra được locker-service: không kết luận gì, giữ nguyên trạng thái chặn hiện có.
    private static final class Unknown extends RuntimeException {
        Unknown(Throwable cause) {
            super(cause);
        }
    }

    @Transactional
    public Review review(Long orderId, LocalDateTime now, Snapshot snapshot) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null
                || !"DRONE_DELIVERY".equals(order.getType())
                || !"AWAITING_DISPATCH".equals(order.getStatus())
                || order.getDeliveryStage() == null
                || !DroneDeliveryFailureService.OPEN_STAGES.contains(order.getDeliveryStage())) {
            return Review.UNCHANGED;
        }
        String stage = order.getDeliveryStage();
        // Đơn chưa trả tiền chưa vào guồng: hạn "chưa thanh toán" lo, không phải việc ở đây.
        if ("AWAITING_DISPATCH".equals(stage) && !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return Review.UNCHANGED;
        }
        DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
        DronePickup pickup = pickupRepository.findByOrderId(orderId).orElse(null);
        boolean inFlight = DroneMissionProgressService.IN_FLIGHT_STAGES.contains(stage);

        Cause cause;
        try {
            cause = detect(order, mission, pickup, now, snapshot);
        } catch (Unknown unknown) {
            log.debug("Drone order {} block review skipped: {}", orderId, unknown.getCause().getMessage());
            return Review.UNCHANGED;
        }

        if (cause == null) {
            if (order.getDeliveryBlockReason() == null) {
                return Review.UNCHANGED;
            }
            String cleared = DroneFailureReasons.label(order.getDeliveryBlockReason());
            order.setDeliveryBlockReason(null);
            order.setDeliveryBlockedAt(null);
            order.setDeliveryBlockTicketId(null);
            order.setDeliveryBlockNote(null);
            orderRepository.save(order);
            addHistory(order, null, "Đã hết sự cố (" + cleared + ") — đơn tiếp tục được xử lý");
            notifyQuietly(order.getUserId(), "Đơn drone tiếp tục được xử lý",
                    "Sự cố ảnh hưởng tới đơn " + order.getOrderCode() + " đã được khắc phục.", order);
            return new Review(Verdict.CLEARED, null, null, null);
        }

        boolean changed = !cause.reason().equals(order.getDeliveryBlockReason())
                || !Objects.equals(cause.ticketId(), order.getDeliveryBlockTicketId());
        if (changed) {
            boolean newlyBlocked = order.getDeliveryBlockReason() == null;
            order.setDeliveryBlockReason(cause.reason());
            order.setDeliveryBlockTicketId(cause.ticketId());
            order.setDeliveryBlockNote(cause.note());
            if (newlyBlocked || order.getDeliveryBlockedAt() == null) {
                order.setDeliveryBlockedAt(now);
            }
            orderRepository.save(order);
            String label = DroneFailureReasons.label(cause.reason());
            addHistory(order, null,
                    "Đơn đang gặp sự cố: " + label
                            + (cause.note() == null ? "" : " · " + cause.note())
                            + (cause.ticketId() == null ? "" : " · phiếu sự cố #" + cause.ticketId()));
            if (newlyBlocked) {
                announce(order, mission, pickup, label, cause, inFlight);
            }
            return new Review(Verdict.BLOCKED, cause.reason(), cause.ticketId(), cause.note());
        }

        int timeout = rules.droneBlockedTimeoutMinutes();
        // Drone đang bay thì không tự đóng đơn: kiện đang ở đâu là việc đội bay phải xác nhận.
        if (!inFlight && timeout > 0 && order.getDeliveryBlockedAt() != null
                && !order.getDeliveryBlockedAt().plusMinutes(timeout).isAfter(now)) {
            return new Review(Verdict.EXPIRED, cause.reason(), cause.ticketId(), cause.note());
        }
        return Review.UNCHANGED;
    }

    // ------------------------------------------------------------------ detection

    private Cause detectInFlight(DroneMission mission, Snapshot snapshot) {
        if (mission == null || mission.getDroneUnitId() == null) {
            return null;
        }
        DroneUnitDto drone = drone(mission.getDroneUnitId(), snapshot);
        // Lúc bay drone ở IN_FLIGHT; FAULT/MAINTENANCE/ngừng hoạt động giữa chuyến là sự cố.
        if (!DroneSourceLockerSelector.usable(drone)) {
            return new Cause(DroneFailureReasons.FLIGHT_INCIDENT,
                    ticket(snapshot, report -> Objects.equals(report.droneUnitId(), mission.getDroneUnitId())),
                    "Drone " + mission.getDroneCode() + " báo lỗi khi đang bay");
        }
        return null;
    }

    private Cause detect(LockerOrder order, DroneMission mission, DronePickup pickup, LocalDateTime now,
            Snapshot snapshot) {
        if (rules.droneFlightsSuspended()) {
            return new Cause(DroneFailureReasons.WEATHER, null, "Quản trị viên đang tạm dừng bay");
        }

        Long destinationId = order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        LockerLayoutDto destination = layout(destinationId, snapshot);
        if (!"ACTIVE".equalsIgnoreCase(destination.status())) {
            return new Cause(DroneFailureReasons.DESTINATION_LOCKER_FAULT,
                    ticket(snapshot, lockerWide(destinationId)), "Tủ nhận đang ngừng hoạt động hoặc bảo trì");
        }
        if (!padReady(destination)) {
            return new Cause(DroneFailureReasons.LANDING_PAD_FAULT,
                    ticket(snapshot, landingPad(destinationId)), "Bãi đáp ở tủ nhận không sẵn sàng");
        }
        if (order.getReservedBoxId() != null && !boxUsable(order.getReservedBoxId())) {
            Long boxId = order.getReservedBoxId();
            return new Cause(DroneFailureReasons.DESTINATION_LOCKER_FAULT,
                    ticket(snapshot, report -> Objects.equals(report.boxId(), boxId)), "Ô nhận đã giữ đang hỏng");
        }

        Long sourceId = order.getSourceLockerId();
        if (mission != null) {
            // Đã gán drone: drone đó phải còn bay được; bãi đáp/tủ gửi phải còn dùng được để cất cánh.
            DroneUnitDto drone = drone(mission.getDroneUnitId(), snapshot);
            if (!DroneSourceLockerSelector.usable(drone)) {
                return new Cause(DroneMissionProgressService.IN_FLIGHT_STAGES.contains(order.getDeliveryStage())
                                ? DroneFailureReasons.FLIGHT_INCIDENT : DroneFailureReasons.DRONE_FAULT,
                        ticket(snapshot, report -> Objects.equals(report.droneUnitId(), mission.getDroneUnitId())),
                        "Drone " + mission.getDroneCode() + " đang lỗi hoặc bảo trì");
            }
            Cause source = sourceCause(sourceId, snapshot, false);
            if (source != null) {
                return source;
            }
        } else if (sourceId != null) {
            Cause source = sourceCause(sourceId, snapshot, true);
            if (source != null) {
                if (!reselectSource(order, pickup, snapshot)) {
                    return source;
                }
            }
        }

        int launchTimeout = rules.droneLaunchTimeoutMinutes();
        if (launchTimeout > 0 && !DroneMissionProgressService.IN_FLIGHT_STAGES.contains(order.getDeliveryStage())
                && pickup != null && pickup.getPickedUpAt() != null
                && !pickup.getPickedUpAt().plusMinutes(launchTimeout).isAfter(now)) {
            return new Cause(DroneFailureReasons.LAUNCH_OVERDUE, null,
                    "Đã lấy hàng quá " + launchTimeout + " phút mà drone chưa cất cánh");
        }
        return null;
    }

    /// Tủ gửi có còn là nơi cất cánh được không; `needDrone` khi đơn chưa được gán drone cụ thể.
    private Cause sourceCause(Long sourceId, Snapshot snapshot, boolean needDrone) {
        if (sourceId == null) {
            return null;
        }
        LockerLayoutDto source = layout(sourceId, snapshot);
        if (!"ACTIVE".equalsIgnoreCase(source.status())) {
            return new Cause(DroneFailureReasons.SOURCE_LOCKER_FAULT,
                    ticket(snapshot, lockerWide(sourceId)), "Tủ gửi đang ngừng hoạt động hoặc bảo trì");
        }
        if (!padReady(source)) {
            return new Cause(DroneFailureReasons.LANDING_PAD_FAULT,
                    ticket(snapshot, landingPad(sourceId)), "Bãi đáp ở tủ gửi không sẵn sàng");
        }
        if (needDrone) {
            // Chỉ drone hỏng/bảo trì/ngừng hoạt động mới là sự cố. Drone đang giữ hoặc đang bay cho đơn
            // khác (RESERVED/IN_FLIGHT), hay đang sạc pin, sẽ rảnh lại — chặn vì nó thì đơn đã trả tiền
            // không mời được KTV nào đi lấy hàng và tự đóng sau hạn chặn. Kiện lấy về mà drone chưa kịp
            // rảnh thì hạn "chưa phóng" (LAUNCH_OVERDUE) lo.
            List<DroneUnitDto> stationed = dronesAt(sourceId, snapshot);
            if (stationed.stream().noneMatch(DroneSourceLockerSelector::usable)) {
                Set<Long> droneIds = new java.util.HashSet<>();
                stationed.forEach(drone -> droneIds.add(drone.id()));
                return new Cause(DroneFailureReasons.NO_DRONE_AVAILABLE,
                        ticket(snapshot, report -> report.droneUnitId() != null && droneIds.contains(report.droneUnitId())),
                        "Tủ gửi không còn drone nào dùng được");
            }
        }
        return null;
    }

    /// Tủ gửi hỏng trước khi gán drone: đổi sang tủ khác gần điểm lấy hàng nếu có. Kiện chưa/đang trên
    /// tay KTV nên chỉ là đổi điểm tới.
    private boolean reselectSource(LockerOrder order, DronePickup pickup, Snapshot snapshot) {
        if (!BEFORE_MISSION.contains(order.getDeliveryStage())
                || order.getPickupLatitude() == null || order.getPickupLongitude() == null) {
            return false;
        }
        Long previous = order.getSourceLockerId();
        DroneSourceLockerSelector.Selection selection;
        try {
            selection = sourceLockerSelector.select(
                    order.getPickupLatitude(), order.getPickupLongitude(), order.getDestinationLockerId(), previous);
        } catch (BusinessException noAlternative) {
            return false;
        } catch (RuntimeException lookupFailed) {
            throw new Unknown(lookupFailed);
        }
        order.setSourceLockerId(selection.source().id());
        order.setPickupDistanceMeters(selection.pickupDistanceMeters());
        order.setRouteDistanceMeters(selection.routeDistanceMeters());
        orderRepository.save(order);
        String name = selection.source().name() == null ? "#" + selection.source().id() : selection.source().name();
        addHistory(order, null, "Tủ gửi #" + previous + " gặp sự cố — đổi sang tủ gửi " + name);
        if (pickup != null && pickup.getTechnicianId() != null
                && (pickup.holdsTechnician() || DronePickup.PICKED_UP.equals(pickup.getStatus()))) {
            notifyQuietly(pickup.getTechnicianId(), "Đổi tủ gửi của đơn drone",
                    "Đơn " + order.getOrderCode() + ": tủ gửi cũ gặp sự cố, mang kiện tới " + name + " để phóng.", order);
        }
        return true;
    }

    private static boolean padReady(LockerLayoutDto layout) {
        return Boolean.TRUE.equals(layout.landingPad()) && "OK".equalsIgnoreCase(layout.landingPadStatus());
    }

    private static Predicate<OpenReportDto> lockerWide(Long lockerId) {
        return report -> Objects.equals(report.lockerId(), lockerId)
                && (Boolean.TRUE.equals(report.blocksLocker()) || "LOCKER".equalsIgnoreCase(report.category()));
    }

    private static Predicate<OpenReportDto> landingPad(Long lockerId) {
        return report -> Objects.equals(report.lockerId(), lockerId) && "LANDING_PAD".equalsIgnoreCase(report.category());
    }

    // ------------------------------------------------------------------ lookups

    private LockerLayoutDto layout(Long lockerId, Snapshot snapshot) {
        return snapshot.layouts.computeIfAbsent(lockerId, id -> required(() -> lockerDroneClient.getLockerLayout(id)));
    }

    private DroneUnitDto drone(Long droneUnitId, Snapshot snapshot) {
        return snapshot.drones.computeIfAbsent(droneUnitId, id -> required(() -> lockerDroneClient.getDroneUnit(id)));
    }

    private List<DroneUnitDto> dronesAt(Long lockerId, Snapshot snapshot) {
        return snapshot.dronesByLocker.computeIfAbsent(
                lockerId, id -> required(() -> lockerDroneClient.listDroneUnits(id)));
    }

    private boolean boxUsable(Long boxId) {
        LockerBoxSummary box = required(() -> lockerClient.getBox(boxId));
        return box.status() != null && USABLE_BOX_STATUSES.contains(box.status().toUpperCase());
    }

    /// Phiếu còn mở khớp điều kiện, mới nhất trước; không tra được danh sách phiếu thì bỏ qua liên kết
    /// (đơn vẫn bị chặn đúng lý do, chỉ thiếu mã phiếu).
    private Long ticket(Snapshot snapshot, Predicate<OpenReportDto> matches) {
        if (snapshot.reports == null) {
            try {
                ApiResponse<List<OpenReportDto>> response = lockerDroneClient.openReports();
                snapshot.reports = response == null || response.data() == null ? List.of() : response.data();
            } catch (RuntimeException ex) {
                log.debug("Open report lookup failed: {}", ex.getMessage());
                snapshot.reports = List.of();
            }
        }
        return snapshot.reports.stream().filter(matches).map(OpenReportDto::id).findFirst().orElse(null);
    }

    private static <T> T required(java.util.function.Supplier<ApiResponse<T>> call) {
        try {
            ApiResponse<T> response = call.get();
            if (response == null || response.data() == null) {
                throw new IllegalStateException("empty response from locker-service");
            }
            return response.data();
        } catch (Unknown unknown) {
            throw unknown;
        } catch (RuntimeException ex) {
            throw new Unknown(ex);
        }
    }

    // ------------------------------------------------------------------ side effects

    private void announce(LockerOrder order, DroneMission mission, DronePickup pickup, String label, Cause cause,
            boolean inFlight) {
        int timeout = rules.droneBlockedTimeoutMinutes();
        notifyQuietly(order.getUserId(), "Đơn drone đang gặp sự cố",
                "Đơn " + order.getOrderCode() + ": " + label + "."
                        + (inFlight || timeout <= 0
                                ? " Đội bay đang xử lý."
                                : " Nếu chưa khắc phục được sau " + timeout
                                        + " phút, đơn sẽ kết thúc và bạn được hoàn tiền."),
                order);
        String forStaff = "Đơn " + order.getOrderCode() + ": " + label
                + (cause.note() == null ? "" : " — " + cause.note())
                + (cause.ticketId() == null ? "" : " (phiếu #" + cause.ticketId() + ")")
                + ". Đổi phương án hoặc xác nhận giao không hoàn tất.";
        Set<Long> told = new java.util.HashSet<>();
        if (mission != null && mission.getAssignedByUserId() != null && told.add(mission.getAssignedByUserId())) {
            notifyQuietly(mission.getAssignedByUserId(), "Đơn drone đang gặp sự cố", forStaff, order);
        }
        if (pickup != null && pickup.getTechnicianId() != null && told.add(pickup.getTechnicianId())) {
            notifyQuietly(pickup.getTechnicianId(), "Đơn drone đang gặp sự cố", forStaff, order);
        }
        try {
            ApiResponse<List<UserSummary>> admins = userClient.getUsersByRole("ADMIN");
            if (admins != null && admins.data() != null) {
                for (UserSummary admin : admins.data()) {
                    if (told.add(admin.id())) {
                        notifyQuietly(admin.id(), "Đơn drone đang gặp sự cố", forStaff, order);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.debug("Could not notify admins about blocked drone order {}: {}", order.getId(), ex.getMessage());
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

    /// Sự cố không đổi chặng: ghi thành dòng nhật ký chặng → chính chặng đó.
    private void addHistory(LockerOrder order, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(order.getDeliveryStage());
        history.setNewStatus(order.getDeliveryStage());
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);
    }
}
