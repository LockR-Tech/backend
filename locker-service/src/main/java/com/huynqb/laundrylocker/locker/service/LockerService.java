package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.CatalogChangeEvents;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.common.util.DeleteReason;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.OrderClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.BoxIncidentNotice;
import com.huynqb.laundrylocker.locker.dto.*;
import com.huynqb.laundrylocker.locker.model.*;
import com.huynqb.laundrylocker.locker.repository.*;
import com.huynqb.laundrylocker.locker.settings.LockerRules;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockerService {

    private static final List<String> OPEN_REPORT_STATUSES = List.of("OPEN", "IN_PROGRESS");

    /// Khách báo ô ⇒ ô "chờ KTV kiểm tra": không cấp cho đơn mới, nhưng chủ đồ vẫn mở bằng mã và phí quá giờ
    /// của chủ đồ dừng từ lúc báo. KTV xác nhận (khoá ô) ⇒ FAULT; KTV từ chối ⇒ ô về trạng thái trước đó.
    static final String PENDING_CHECK = "PENDING_CHECK";
    /// Phiếu KTV kiểm tra không đúng như mô tả: đóng phiếu, phản hồi người báo, trả ô về hoạt động.
    static final String REPORT_REJECTED = "REJECTED";

    /// Ô đang bị giữ vì sự cố (đã xác nhận hoặc chờ kiểm tra): không cấp cho đơn mới, chỉ về hoạt động qua phiếu.
    static boolean isFaultHold(String status) {
        return "FAULT".equalsIgnoreCase(status) || PENDING_CHECK.equalsIgnoreCase(status);
    }

    private static boolean isClosedReport(String status) {
        return "RESOLVED".equalsIgnoreCase(status) || REPORT_REJECTED.equalsIgnoreCase(status);
    }
    private static final List<String> SIZE_ORDER = List.of("SMALL", "MEDIUM", "LARGE", "XL");
    private static final String LOCKER_TECHNICIAN = "LOCKER_TECHNICIAN";
    private static final String DRONE_TECHNICIAN = "DRONE_TECHNICIAN";
    /// Tủ ở các trạng thái này không nhận đơn/đặt ô mới.
    private static final Set<String> NON_BOOKABLE_LOCKER_STATUSES = Set.of("MAINTENANCE", "INACTIVE");
    private static final String MAINTENANCE_SOURCE_ADMIN = "ADMIN";
    private static final String MAINTENANCE_SOURCE_TICKET = "TICKET";
    /// Trạng thái ô được nhớ lại khi báo hỏng — đơn vẫn đang giữ ô.
    private static final Set<String> ORDER_HELD_BOX_STATUSES = Set.of("RESERVED", "OCCUPIED");
    /// Loại thông báo cho KTV drone (app mở màn phiếu/lịch drone theo `type`, `referenceId` = id phiếu/lịch).
    /// KTV tủ giữ các loại `locker.*`. notification-service hiện bind theo routing key `locker.*` nên các
    /// bản tin này đi bằng routing key tương ứng của tủ, còn `type` trong DomainEvent là `drone.*`.
    static final String DRONE_REPORT_ASSIGNED = "drone.report.assigned";
    static final String DRONE_REPORT_ROUTED = "drone.report.routed";
    static final String DRONE_SCHEDULE_DUE = "drone.schedule.due";
    private static final Map<String, String> DRONE_EVENT_ROUTING_KEYS = Map.of(
            DRONE_REPORT_ASSIGNED, DomainEventNames.LOCKER_REPORT_ASSIGNED,
            DRONE_REPORT_ROUTED, DomainEventNames.LOCKER_REPORT_ROUTED,
            DRONE_SCHEDULE_DUE, DomainEventNames.LOCKER_SCHEDULE_DUE);
    private static final String DRONE_REPORT_REFERENCE = "DRONE_REPORT";
    /// Trạng thái vận hành: rời FAULT về đây khi drone còn phiếu mở là bị chặn.
    private static final Set<String> DRONE_OPERATIONAL_STATUSES = Set.of(DroneStatus.IDLE, DroneStatus.CHARGING);

    private final LockerUnitRepository lockerRepository;
    private final LockerBoxRepository boxRepository;
    private final LockerReportRepository reportRepository;
    private final RepairLogRepository repairLogRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final MaintenanceInspectionLogRepository inspectionLogRepository;
    private final LockerReportRatingRepository ratingRepository;
    private final DroneUnitRepository droneUnitRepository;
    private final DroneDeviceRepository droneDeviceRepository;
    private final DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    private final DroneIotLogRepository droneIotLogRepository;
    private final IotClient iotClient;
    private final UserClient userClient;
    private final OrderClient orderClient;
    private final ReportAttachmentService attachmentService;
    private final RabbitTemplate rabbitTemplate;
    /// SLA, ngưỡng chế tài KTV, giới hạn ảnh, TTL ô RESERVED, pin drone… admin cấu hình trên web
    /// (ADR-0005), không còn @Value/hằng số cứng. TTL ô RESERVED nên ≥ `app.order.auto-cancel-hours`
    /// để không bao giờ nhả ô sớm hơn order-service.
    private final LockerRules rules;
    private final TechnicianPresenceRepository presenceRepository;

    /// #6 Trang thai hop le cua bai dap drone.
    private static final java.util.Set<String> LANDING_PAD_STATUSES =
            java.util.Set.of("OK", "FAULT", "MAINTENANCE");

    @Transactional
    public LockerResponse createLocker(LockerRequest request) {
        LockerUnit locker = new LockerUnit();
        locker.setStoreId(request.storeId());
        locker.setCode(request.code());
        locker.setName(request.name());
        applyAdminStatus(locker, StringUtils.hasText(request.status()) ? request.status() : "ACTIVE");
        locker.setAddress(request.address());
        locker.setLatitude(request.latitude());
        locker.setLongitude(request.longitude());
        locker.setLandingPad(true);
        locker.setLandingPadStatus("OK");
        LockerUnit saved = lockerRepository.save(locker);
        publishLockerCatalogChanged(saved.getId());
        return toStaffResponse(saved, new HashMap<>());
    }

    @Transactional
    public LockerBoxSummary createBox(BoxRequest request) {
        if (request.boxNumber() == null) {
            throw new BusinessException("INVALID_BOX_NUMBER", "Số ô không được để trống");
        }
        if (boxRepository.existsByLockerIdAndBoxNumber(request.lockerId(), request.boxNumber())) {
            throw new BusinessException("BOX_ALREADY_EXISTS", "Ô số " + request.boxNumber() + " đã tồn tại trong tủ này");
        }
        LockerBox box = new LockerBox();
        box.setLockerId(request.lockerId());
        box.setBoxNumber(request.boxNumber());
        box.setSize(StringUtils.hasText(request.size()) ? request.size().toUpperCase() : "MEDIUM");
        box.setStatus(StringUtils.hasText(request.status()) ? request.status().toUpperCase() : "AVAILABLE");
        box.setCellType(StringUtils.hasText(request.cellType()) ? request.cellType().toUpperCase() : CellType.STANDARD);
        box.setRowIndex(request.rowIndex());
        box.setColIndex(request.colIndex());
        LockerBox saved = boxRepository.save(box);
        publishLockerLayoutUpdated(
                saved.getLockerId(),
                saved.getId(),
                saved.getBoxNumber(),
                saved.getStatus(),
                "Thêm mới ô #" + saved.getBoxNumber() + " (" + saved.getCellType() + ")");
        return toSummary(saved);
    }

    @Transactional
    public List<CellResponse> createBoxesBatch(Long lockerId, BatchCreateBoxesRequest request) {
        if (!lockerRepository.existsById(lockerId)) {
            throw new NotFoundException("Locker", lockerId);
        }

        List<BoxItemRequest> items = new ArrayList<>();
        if (request.boxes() != null && !request.boxes().isEmpty()) {
            items.addAll(request.boxes());
        } else if (request.startBoxNumber() != null && request.count() != null && request.count() > 0) {
            int start = request.startBoxNumber();
            int count = request.count();
            int row = request.startRowIndex() != null ? request.startRowIndex() : 1;
            int col = request.startColIndex() != null ? request.startColIndex() : 0;
            String size = request.size();
            String cellType = request.cellType();
            String status = request.status();

            for (int i = 0; i < count; i++) {
                items.add(new BoxItemRequest(
                        start + i,
                        size,
                        status,
                        cellType,
                        row,
                        col + i));
            }
        } else {
            throw new BusinessException("INVALID_REQUEST", "Danh sách ô tủ hoặc (startBoxNumber, count) không được để trống");
        }

        Set<Integer> numbersInRequest = new HashSet<>();
        for (BoxItemRequest item : items) {
            if (item.boxNumber() == null) {
                throw new BusinessException("INVALID_BOX_NUMBER", "Số ô không được để trống");
            }
            if (!numbersInRequest.add(item.boxNumber())) {
                throw new BusinessException("DUPLICATE_BOX_NUMBER", "Trùng số ô " + item.boxNumber() + " trong yêu cầu tạo");
            }
        }

        List<Integer> duplicateInDb = items.stream()
                .map(BoxItemRequest::boxNumber)
                .filter(num -> boxRepository.existsByLockerIdAndBoxNumber(lockerId, num))
                .toList();
        if (!duplicateInDb.isEmpty()) {
            throw new BusinessException("BOX_ALREADY_EXISTS", "Các ô số sau đã tồn tại trong tủ: " + duplicateInDb);
        }

        List<LockerBox> toSave = items.stream().map(item -> {
            LockerBox box = new LockerBox();
            box.setLockerId(lockerId);
            box.setBoxNumber(item.boxNumber());
            box.setSize(StringUtils.hasText(item.size()) ? item.size().toUpperCase() : "MEDIUM");
            box.setStatus(StringUtils.hasText(item.status()) ? item.status().toUpperCase() : "AVAILABLE");
            box.setCellType(StringUtils.hasText(item.cellType()) ? item.cellType().toUpperCase() : CellType.STANDARD);
            box.setRowIndex(item.rowIndex());
            box.setColIndex(item.colIndex());
            return box;
        }).toList();

        List<LockerBox> saved = boxRepository.saveAll(toSave);

        for (LockerBox box : saved) {
            publishLockerLayoutUpdated(
                    lockerId,
                    box.getId(),
                    box.getBoxNumber(),
                    box.getStatus(),
                    "Thêm mới ô #" + box.getBoxNumber());
        }

        return saved.stream().map(this::toCell).toList();
    }

    @Transactional
    public LockerResponse updateLocker(Long id, LockerRequest request) {
        LockerUnit locker =
                lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id));
        locker.setStoreId(request.storeId());
        locker.setCode(request.code());
        locker.setName(request.name());
        applyAdminStatus(locker, StringUtils.hasText(request.status()) ? request.status() : locker.getStatus());
        locker.setAddress(request.address());
        locker.setLatitude(request.latitude());
        locker.setLongitude(request.longitude());
        LockerUnit saved = lockerRepository.save(locker);
        publishLockerAvailabilityChanged(id);
        publishLockerCatalogChanged(id);
        return toStaffResponse(saved, new HashMap<>());
    }

    @Transactional
    public LockerResponse updateLockerLocation(Long id, LockerLocationRequest request) {
        LockerUnit locker =
                lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id));
        locker.setLatitude(request.latitude());
        locker.setLongitude(request.longitude());
        if (StringUtils.hasText(request.address())) {
            locker.setAddress(request.address());
        }
        publishLockerCatalogChanged(id);
        return toStaffResponse(lockerRepository.save(locker), new HashMap<>());
    }

    /// Admin đổi trạng thái tủ bằng tay. Giữ nguyên trạng thái (lưu lại form) thì không đổi nguồn,
    /// để tủ do phiếu chặn vẫn tự mở lại khi phiếu đóng.
    private void applyAdminStatus(LockerUnit locker, String status) {
        if (!status.equalsIgnoreCase(locker.getStatus())) {
            locker.setMaintenanceSource("MAINTENANCE".equalsIgnoreCase(status) ? MAINTENANCE_SOURCE_ADMIN : null);
        }
        locker.setStatus(status);
    }

    /// Admin gán KTV tủ phụ trách; phiếu OPEN chưa ai nhận của tủ chuyển sang người mới.
    @Transactional
    public LockerResponse assignLockerTechnician(Long lockerId, Long technicianId) {
        LockerUnit locker =
                lockerRepository.findById(lockerId).orElseThrow(() -> new NotFoundException("Locker", lockerId));
        if (technicianId != null) {
            requireTechnician(technicianId, LOCKER_TECHNICIAN);
        }
        locker.setAssignedTechnicianId(technicianId);
        LockerUnit saved = lockerRepository.save(locker);
        List<LockerReport> waiting = reportRepository.findByLockerIdAndStatusAndAssignedToUserIdIsNull(lockerId, "OPEN")
                .stream()
                .filter(report -> !ReportCategory.DRONE.equals(report.getCategory()))
                .toList();
        waiting.forEach(report -> report.setRoutedToUserId(technicianId));
        reportRepository.saveAll(waiting);
        if (technicianId != null) {
            String message = "Bạn được giao phụ trách tủ " + locker.getName()
                    + (waiting.isEmpty() ? "." : " — có " + waiting.size() + " phiếu sự cố đang chờ nhận.");
            publishStaffNotification(
                    DomainEventNames.LOCKER_REPORT_ASSIGNED, technicianId, lockerId, "LOCKER", message);
        } else {
            // Bỏ người phụ trách ⇒ phiếu đang chờ chuyển sang "mọi KTV tủ", báo lại để không ai bỏ sót.
            waiting.forEach(report -> notifyRouted(report, saved));
        }
        return toStaffResponse(saved, new HashMap<>());
    }

    /// Xoá mềm: tủ và các ô biến khỏi mọi truy vấn (@SQLRestriction) nhưng còn trong DB kèm lý do.
    @Transactional
    public void deleteLocker(Long id, String reason, Long actorUserId) {
        String deleteReason = DeleteReason.require(reason);
        LockerUnit locker =
                lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id));
        List<LockerBox> boxes = boxRepository.findByLockerIdOrderByRowIndexAscColIndexAsc(id);
        boolean hasOccupied = boxes.stream().anyMatch(
                b -> "OCCUPIED".equalsIgnoreCase(b.getStatus()) || "RESERVED".equalsIgnoreCase(b.getStatus()));
        if (hasOccupied) {
            throw new BusinessException("LOCKER_IN_USE", "Không thể xóa tủ đang có ô chứa hàng hoặc đã được giữ chỗ");
        }
        boolean hasDrones = droneUnitRepository.findAllByOrderByLockerIdAscCodeAsc().stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getActive()) && id.equals(d.getLockerId()));
        if (hasDrones) {
            throw new BusinessException("LOCKER_HAS_DRONES", "Không thể xóa trạm tủ đang có Drone trực chiến. Vui lòng chuyển hoặc xử lý Drone trước.");
        }
        softDeleteBoxes(boxes, deleteReason, actorUserId);
        softDeleteLocker(locker, deleteReason, actorUserId);
        publishLockerCatalogChanged(id);
    }

    private void softDeleteLocker(LockerUnit locker, String reason, Long actorUserId) {
        locker.setDeletedAt(LocalDateTime.now());
        locker.setDeletedBy(actorUserId);
        locker.setDeletedReason(reason);
        lockerRepository.save(locker);
    }

    private void softDeleteBoxes(List<LockerBox> boxes, String reason, Long actorUserId) {
        LocalDateTime now = LocalDateTime.now();
        for (LockerBox box : boxes) {
            box.setDeletedAt(now);
            box.setDeletedBy(actorUserId);
            box.setDeletedReason(reason);
        }
        if (!boxes.isEmpty()) {
            boxRepository.saveAll(boxes);
        }
    }

    @Transactional
    public int deleteLockersBatch(BatchDeleteLockersRequest request, Long actorUserId) {
        String deleteReason = DeleteReason.require(request == null ? null : request.reason());
        List<LockerUnit> targetLockers;
        if (Boolean.TRUE.equals(request.deleteAll())) {
            targetLockers = lockerRepository.findAll();
        } else if (request.lockerIds() != null && !request.lockerIds().isEmpty()) {
            targetLockers = lockerRepository.findAllById(request.lockerIds());
        } else {
            // Không chọn tủ nào thì không được hiểu là "xoá tất cả".
            throw new BusinessException("NO_LOCKERS_SELECTED", "Chưa chọn tủ nào để xóa");
        }

        if (targetLockers.isEmpty()) {
            return 0;
        }

        List<Long> targetIds = targetLockers.stream().map(LockerUnit::getId).toList();

        boolean hasDrones = droneUnitRepository.findAllByOrderByLockerIdAscCodeAsc().stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getActive()) && targetIds.contains(d.getLockerId()));
        if (hasDrones) {
            throw new BusinessException("LOCKER_HAS_DRONES", "Không thể xóa các trạm tủ đang có Drone trực chiến. Vui lòng chuyển hoặc xử lý Drone trước.");
        }

        List<LockerBox> boxes = boxRepository.findByLockerIdIn(targetIds);

        List<LockerBox> inUseBoxes = boxes.stream()
                .filter(b -> "OCCUPIED".equalsIgnoreCase(b.getStatus()) || "RESERVED".equalsIgnoreCase(b.getStatus()))
                .toList();

        if (!inUseBoxes.isEmpty()) {
            List<Long> lockedLockerIds = inUseBoxes.stream().map(LockerBox::getLockerId).distinct().toList();
            throw new BusinessException("LOCKER_IN_USE", "Không thể xóa các tủ đang có ô chứa hàng hoặc đã được giữ chỗ (Tủ ID: " + lockedLockerIds + ")");
        }

        softDeleteBoxes(boxes, deleteReason, actorUserId);

        int count = targetLockers.size();
        targetLockers.forEach(locker -> softDeleteLocker(locker, deleteReason, actorUserId));
        targetIds.forEach(this::publishLockerCatalogChanged);
        return count;
    }

    @Transactional
    public LockerResponse setMaintenance(Long id, boolean maintenance) {
        LockerUnit locker =
                lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id));
        // Admin bật bảo trì là ý định rõ ràng ⇒ nguồn ADMIN kể cả khi tủ đang bị phiếu chặn.
        locker.setStatus(maintenance ? "MAINTENANCE" : "ACTIVE");
        locker.setMaintenanceSource(maintenance ? MAINTENANCE_SOURCE_ADMIN : null);
        publishLockerCatalogChanged(id);
        publishLockerAvailabilityChanged(id);
        return toStaffResponse(lockerRepository.save(locker), new HashMap<>());
    }

    @Transactional
    public LockerBoxSummary updateBoxStatus(Long boxId, String status) {
        LockerBox box = findBox(boxId);
        String targetStatus = status != null ? status.trim().toUpperCase(Locale.ROOT) : "";
        if (targetStatus.isEmpty()) {
            throw new BusinessException("INVALID_STATUS", "Trạng thái ô không được để trống");
        }
        if (targetStatus.equalsIgnoreCase(box.getStatus())) {
            return toSummary(box);
        }
        if (ORDER_HELD_BOX_STATUSES.contains(targetStatus)) {
            throw new BusinessException(
                    "CANNOT_MANUALLY_ASSIGN_ORDER_STATUS",
                    "Không thể gán trạng thái " + targetStatus + " thủ công. Trạng thái này do luồng đơn hàng quản lý.",
                    HttpStatus.BAD_REQUEST);
        }
        if (holdsOrder(box) || requireActiveOrderLookup(boxId) != null) {
            throw new BusinessException(
                    "BOX_IN_USE",
                    "Ô #" + box.getBoxNumber() + " đang chứa hàng hoặc giữ chỗ cho đơn hàng — không thể đổi trạng thái thủ công",
                    HttpStatus.CONFLICT);
        }
        box.setStatus(targetStatus);
        LockerBox saved = boxRepository.save(box);
        syncBoxStateQuietly(saved, targetStatus);
        publishLockerLayoutUpdated(
                saved.getLockerId(),
                saved.getId(),
                saved.getBoxNumber(),
                targetStatus,
                "Cập nhật trạng thái ô #" + saved.getBoxNumber() + " sang " + targetStatus);
        return toSummary(saved);
    }

    @Transactional
    public LockerBoxSummary updateBox(Long boxId, UpdateBoxRequest request) {
        LockerBox box = findBox(boxId);
        if (request.boxNumber() != null
                && !request.boxNumber().equals(box.getBoxNumber())
                && boxRepository.existsByLockerIdAndBoxNumber(box.getLockerId(), request.boxNumber())) {
            throw new BusinessException("BOX_ALREADY_EXISTS", "Ô số " + request.boxNumber() + " đã tồn tại trong tủ này");
        }
        if ((request.rowIndex() != null && request.rowIndex() < 0) || (request.colIndex() != null && request.colIndex() < 0)) {
            throw new BusinessException("INVALID_BOX_POSITION", "Hàng/cột của ô không được âm");
        }
        if (StringUtils.hasText(request.status())) {
            String targetStatus = request.status().trim().toUpperCase(Locale.ROOT);
            if (!targetStatus.equalsIgnoreCase(box.getStatus())) {
                if (ORDER_HELD_BOX_STATUSES.contains(targetStatus)) {
                    throw new BusinessException(
                            "CANNOT_MANUALLY_ASSIGN_ORDER_STATUS",
                            "Không thể gán trạng thái " + targetStatus + " thủ công. Trạng thái này do luồng đơn hàng quản lý.",
                            HttpStatus.BAD_REQUEST);
                }
                if (holdsOrder(box) || requireActiveOrderLookup(boxId) != null) {
                    throw new BusinessException(
                            "BOX_IN_USE",
                            "Ô #" + box.getBoxNumber() + " đang chứa hàng hoặc đã được giữ chỗ cho một đơn — không thể đổi trạng thái",
                            HttpStatus.CONFLICT);
                }
            }
        }
        if (request.boxNumber() != null) {
            box.setBoxNumber(request.boxNumber());
        }
        if (StringUtils.hasText(request.size())) {
            box.setSize(request.size().toUpperCase());
        }
        if (StringUtils.hasText(request.cellType())) {
            box.setCellType(request.cellType().toUpperCase());
        }
        if (request.rowIndex() != null) {
            box.setRowIndex(request.rowIndex());
        }
        if (request.colIndex() != null) {
            box.setColIndex(request.colIndex());
        }
        if (request.description() != null) {
            box.setDescription(request.description());
        }
        if (StringUtils.hasText(request.status())) {
            box.setStatus(request.status().toUpperCase());
        }
        LockerBox saved = boxRepository.save(box);
        publishLockerLayoutUpdated(
                saved.getLockerId(),
                saved.getId(),
                saved.getBoxNumber(),
                saved.getStatus(),
                "Cập nhật công năng ô #" + saved.getBoxNumber() + " (" + saved.getCellType() + ")");
        return toSummary(saved);
    }

    /// Ô đang giữ hàng/chỗ cho đơn: RESERVED/OCCUPIED, hoặc đang FAULT nhưng trước khi hỏng là
    /// RESERVED/OCCUPIED (đơn chưa trả ô — releaseBox sẽ xoá preFaultStatus khi đơn trả).
    private static boolean holdsOrder(LockerBox box) {
        return ORDER_HELD_BOX_STATUSES.contains(String.valueOf(box.getStatus()).toUpperCase(Locale.ROOT))
                || (isFaultHold(box.getStatus())
                        && ORDER_HELD_BOX_STATUSES.contains(String.valueOf(box.getPreFaultStatus()).toUpperCase(Locale.ROOT)));
    }

    @Transactional
    public void deleteBox(Long boxId, String reason, Long actorUserId) {
        String deleteReason = DeleteReason.require(reason);
        LockerBox box = findBox(boxId);
        if ("OCCUPIED".equalsIgnoreCase(box.getStatus()) || "RESERVED".equalsIgnoreCase(box.getStatus())) {
            throw new BusinessException("BOX_IN_USE", "Không thể xóa ô #" + box.getBoxNumber() + " đang chứa hàng hoặc đã được giữ chỗ");
        }
        Long lockerId = box.getLockerId();
        Integer boxNumber = box.getBoxNumber();
        softDeleteBoxes(List.of(box), deleteReason, actorUserId);
        publishLockerLayoutUpdated(lockerId, boxId, boxNumber, "DELETED", "Đã xóa ô #" + boxNumber);
    }

    @Transactional
    public void deleteBoxByNumber(Long lockerId, Integer boxNumber, String reason, Long actorUserId) {
        String deleteReason = DeleteReason.require(reason);
        LockerBox box = boxRepository.findByLockerIdAndBoxNumber(lockerId, boxNumber)
                .orElseThrow(() -> new NotFoundException("Box #" + boxNumber + " in locker", lockerId));
        if ("OCCUPIED".equalsIgnoreCase(box.getStatus()) || "RESERVED".equalsIgnoreCase(box.getStatus())) {
            throw new BusinessException("BOX_IN_USE", "Không thể xóa ô #" + box.getBoxNumber() + " đang chứa hàng hoặc đã được giữ chỗ");
        }
        Long boxId = box.getId();
        softDeleteBoxes(List.of(box), deleteReason, actorUserId);
        publishLockerLayoutUpdated(lockerId, boxId, boxNumber, "DELETED", "Đã xóa ô #" + boxNumber);
    }

    @Transactional
    public int deleteBoxesBatch(Long lockerId, BatchDeleteBoxesRequest request, Long actorUserId) {
        String deleteReason = DeleteReason.require(request == null ? null : request.reason());
        if (!lockerRepository.existsById(lockerId)) {
            throw new NotFoundException("Locker", lockerId);
        }

        List<LockerBox> targetBoxes = new ArrayList<>();
        if (request != null && Boolean.TRUE.equals(request.deleteAll())) {
            targetBoxes.addAll(boxRepository.findByLockerIdOrderByRowIndexAscColIndexAsc(lockerId));
        } else if (request != null) {
            if (request.boxIds() != null && !request.boxIds().isEmpty()) {
                targetBoxes.addAll(boxRepository.findByLockerIdAndIdIn(lockerId, request.boxIds()));
            }
            if (request.boxNumbers() != null && !request.boxNumbers().isEmpty()) {
                List<LockerBox> byNumbers = boxRepository.findByLockerIdAndBoxNumberIn(lockerId, request.boxNumbers());
                for (LockerBox b : byNumbers) {
                    if (targetBoxes.stream().noneMatch(existing -> existing.getId().equals(b.getId()))) {
                        targetBoxes.add(b);
                    }
                }
            }
        }

        if (targetBoxes.isEmpty()) {
            if (request != null && Boolean.TRUE.equals(request.deleteAll())) {
                return 0;
            }
            throw new BusinessException("NO_BOXES_FOUND", "Không tìm thấy ô tủ nào cần xóa theo yêu cầu");
        }

        List<Integer> inUseNumbers = targetBoxes.stream()
                .filter(b -> "OCCUPIED".equalsIgnoreCase(b.getStatus()) || "RESERVED".equalsIgnoreCase(b.getStatus()))
                .map(LockerBox::getBoxNumber)
                .toList();

        if (!inUseNumbers.isEmpty()) {
            throw new BusinessException("BOX_IN_USE", "Không thể xóa các ô đang chứa hàng hoặc đã được giữ chỗ: " + inUseNumbers);
        }

        softDeleteBoxes(targetBoxes, deleteReason, actorUserId);

        for (LockerBox box : targetBoxes) {
            publishLockerLayoutUpdated(lockerId, box.getId(), box.getBoxNumber(), "DELETED", "Đã xóa ô #" + box.getBoxNumber());
        }

        return targetBoxes.size();
    }

    @Transactional
    public int deleteAllBoxesInLocker(Long lockerId, String reason, Long actorUserId) {
        return deleteBoxesBatch(lockerId, new BatchDeleteBoxesRequest(null, null, true, reason), actorUserId);
    }

    @Transactional
    public LockerBoxSummary openBox(Long boxId) {
        LockerBox box = findBox(boxId);
        publishBoxOpened(box);
        return toSummary(box);
    }

    @Transactional
    public LockerBoxSummary reserveBox(Long boxId, String channel) {
        // Khoá dòng ô tới hết giao dịch: kiểm AVAILABLE và ghi RESERVED là một bước nguyên tử.
        LockerBox box = lockBox(boxId);
        assertLockerBookable(box.getLockerId());
        if (!Boolean.TRUE.equals(box.getActive())) {
            throw new BusinessException("BOX_NOT_AVAILABLE", "Box is not active");
        }
        if (!"AVAILABLE".equalsIgnoreCase(box.getStatus())) {
            throw new BusinessException("BOX_NOT_AVAILABLE", "Box is not available");
        }
        // Ô hàng 1 (DRONE) chỉ dành cho luồng drone thả hàng; mọi kênh khác bị chặn
        if ("DRONE".equalsIgnoreCase(box.getCellType()) && !"DRONE".equalsIgnoreCase(channel)) {
            throw new BusinessException(
                    "DRONE_CELL_RESTRICTED", "This cell is reserved for drone deliveries only");
        }
        box.setStatus("RESERVED");
        box.setReservedUntil(LocalDateTime.now().plusHours(rules.reservedTtlHours()));
        LockerBox saved = boxRepository.save(box);
        syncBoxStateQuietly(saved, "RESERVED");
        return toSummary(saved);
    }

    /// Toàn bộ ô (mọi tủ) cho job đối soát của order-service (Gap G4):
    /// trạng thái ô là bản sao best-effort của đơn nên có thể lệch; order-service
    /// cần id + status + reservedUntil để phân biệt RESERVED còn hạn (đơn vừa
    /// tạo) với RESERVED mồ côi.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBoxesForReconcile() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LockerBox box : boxRepository.findAll()) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", box.getId());
            item.put("lockerId", box.getLockerId());
            item.put("status", box.getStatus());
            item.put("reservedUntil", box.getReservedUntil());
            result.add(item);
        }
        return result;
    }

    /// Backstop sweep for boxes stuck RESERVED past their TTL — defense in
    /// depth in case order-service's own auto-cancel sweep is down. Does not
    /// touch the order itself; just frees the cell so it isn't lost forever.
    ///
    /// DRONE cells are skipped: a drone order legitimately holds its cell for its whole
    /// AWAITING_DISPATCH life (payment, technician pickup, weather holds…), which can exceed
    /// the TTL. Freeing it here let a second order grab a cell the drone was still flying to.
    /// Orphan DRONE holds are released by order-service's reconcile job, which knows the order.
    @Transactional
    public int sweepExpiredReservations() {
        List<LockerBox> expired = boxRepository.findByStatusAndReservedUntilBeforeAndCellTypeNot(
                "RESERVED", LocalDateTime.now(), CellType.DRONE);
        for (LockerBox box : expired) {
            box.setStatus("AVAILABLE");
            box.setReservedUntil(null);
            LockerBox saved = boxRepository.save(box);
            syncBoxStateQuietly(saved, "AVAILABLE");
            log.warn("Released box {} stuck RESERVED past TTL (backstop sweep)", box.getId());
        }
        return expired.size();
    }

    @Transactional
    public LockerBoxSummary occupyBox(Long boxId) {
        LockerBox box = lockBox(boxId);
        if (!"RESERVED".equalsIgnoreCase(box.getStatus()) && !"AVAILABLE".equalsIgnoreCase(box.getStatus())) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "BOX_NOT_RESERVED", "Box must be reserved before deposit");
        }
        box.setStatus("OCCUPIED");
        box.setReservedUntil(null);
        LockerBox saved = boxRepository.save(box);
        syncBoxStateQuietly(saved, "OCCUPIED");
        return toSummary(saved);
    }

    @Transactional
    public LockerBoxSummary releaseBox(Long boxId) {
        LockerBox box = lockBox(boxId);
        String status = box.getStatus();
        if (isFaultHold(status)
                || "OUT_OF_SERVICE".equalsIgnoreCase(status)
                || "CLEANING".equalsIgnoreCase(status)) {
            // Ô hỏng/ngưng dùng/đang vệ sinh phải được kỹ thuật khôi phục chủ động;
            // release thường (từ luồng đơn) không được tự đưa về AVAILABLE.
            // Nhưng đơn đã trả ô ⇒ sửa xong ô về AVAILABLE chứ không về trạng thái đơn đang giữ lúc hỏng.
            if (ORDER_HELD_BOX_STATUSES.contains(String.valueOf(box.getPreFaultStatus()))) {
                box.setPreFaultStatus(null);
                box.setReservedUntil(null);
                boxRepository.save(box);
            }
            return toSummary(box);
        }
        box.setStatus("AVAILABLE");
        box.setReservedUntil(null);
        LockerBox saved = boxRepository.save(box);
        syncBoxStateQuietly(saved, "AVAILABLE");
        return toSummary(saved);
    }

    @Transactional
    public CellResponse markFault(Long boxId, String reason, Long userId) {
        return markFault(boxId, reason, userId, List.of(), null);
    }

    @Transactional
    public CellResponse markFault(Long boxId, String reason, Long userId, List<ReportAttachmentRequest> attachments) {
        return markFault(boxId, reason, userId, attachments, null);
    }

    /// Báo ô hỏng kèm ảnh hiện trường (stage REPORT) của người báo. `rolesHeader` là
    /// `X-User-Roles` từ gateway; gọi nội bộ (order-service) không có header thì tra user-service.
    @Transactional
    public CellResponse markFault(
            Long boxId, String reason, Long userId, List<ReportAttachmentRequest> attachments, String rolesHeader) {
        return markFault(boxId, reason, userId, attachments, rolesHeader, null, null);
    }

    @Transactional
    public CellResponse markFault(
            Long boxId, String reason, Long userId, List<ReportAttachmentRequest> attachments, String rolesHeader,
            Long orderId, String orderCode) {
        return markFault(boxId, reason, userId, attachments, rolesHeader, orderId, orderCode, true);
    }

    /// `notifyOrder = false` khi chính order-service gọi (khách báo qua đơn): order-service tự mở sự cố
    /// trên đơn, không gọi ngược lại.
    @Transactional
    public CellResponse markFault(
            Long boxId, String reason, Long userId, List<ReportAttachmentRequest> attachments, String rolesHeader,
            Long orderId, String orderCode, boolean notifyOrder) {
        LockerBox box = findBox(boxId);
        List<String> roles = userId == null ? List.of() : actorRoles(userId, rolesHeader);
        // KTV tủ tự báo ⇒ tự nhận phiếu luôn.
        Long assigneeId = roles.contains(LOCKER_TECHNICIAN) ? userId : null;
        reportBoxFault(box, reason, userId, attachments, assigneeId, null, orderId, orderCode, notifyOrder,
                isConfirmedReporter(userId, roles));
        return toCell(box);
    }

    /// Báo từ KTV tủ / admin / hệ thống (tủ báo kẹt khoá, kiểm tra định kỳ — không có người báo) là xác nhận
    /// hỏng ngay; khách báo thì ô chỉ chờ KTV kiểm tra.
    private static boolean isConfirmedReporter(Long userId, List<String> roles) {
        return userId == null || userId == 0L || roles.contains(LOCKER_TECHNICIAN) || roles.contains("ADMIN");
    }

    /// Đưa ô về FAULT và trả về phiếu đang mở của ô: gộp vào phiếu cũ nếu có, không thì mở phiếu
    /// mới — giao luôn cho `assigneeId` hoặc định tuyến cho KTV phụ trách tủ khi null.
    private LockerReport reportBoxFault(
            LockerBox box, String reason, Long userId, List<ReportAttachmentRequest> attachments,
            Long assigneeId, String title) {
        return reportBoxFault(box, reason, userId, attachments, assigneeId, title, null, null, true, true);
    }

    /// `confirmed` = người báo là KTV/admin/hệ thống ⇒ ô FAULT; khách báo ⇒ ô PENDING_CHECK (chờ KTV kiểm tra).
    /// Ô đã FAULT thì giữ FAULT.
    private LockerReport reportBoxFault(
            LockerBox box, String reason, Long userId, List<ReportAttachmentRequest> attachments,
            Long assigneeId, String title, Long orderId, String orderCode, boolean notifyOrder, boolean confirmed) {
        Long boxId = box.getId();
        if (orderId == null || !StringUtils.hasText(orderCode)) {
            ActiveBoxOrderDto activeOrder = getActiveOrderByBox(boxId);
            if (activeOrder != null) {
                if (orderId == null) {
                    orderId = activeOrder.orderId();
                }
                if (!StringUtils.hasText(orderCode)) {
                    orderCode = activeOrder.orderCode();
                }
            }
        }
        String current = box.getStatus();
        String target = confirmed || "FAULT".equalsIgnoreCase(current) ? "FAULT" : PENDING_CHECK;
        boolean newlyHeld = !isFaultHold(current);
        boolean statusChanged = !target.equalsIgnoreCase(current);
        if (newlyHeld) {
            // Nhớ trạng thái đơn đang giữ ô để sửa xong trả lại đúng, không để ô chứa hàng về AVAILABLE.
            box.setPreFaultStatus("AVAILABLE".equalsIgnoreCase(current) ? null : current);
        }
        if (statusChanged) {
            box.setStatus(target);
        }
        if (newlyHeld || StringUtils.hasText(reason)) {
            box.setFaultReason(reason);
        }
        boxRepository.save(box);

        Optional<LockerReport> open =
                reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(boxId, OPEN_REPORT_STATUSES);
        LockerReport report;
        if (open.isPresent()) {
            // Ô đã có phiếu mở ⇒ gộp vào phiếu đó thay vì mở phiếu trùng và báo KTV lần nữa.
            report = open.get();
            if (report.getOrderId() == null && orderId != null) {
                report.setOrderId(orderId);
                report.setOrderCode(orderCode);
                reportRepository.save(report);
            }
            mergeIntoOpenReport(report, reason, userId, attachments);
        } else {
            LockerUnit locker = lockerRepository.findById(box.getLockerId()).orElse(null);
            LockerReport created = new LockerReport();
            created.setLockerId(box.getLockerId());
            created.setBoxId(boxId);
            created.setOrderId(orderId);
            created.setOrderCode(orderCode);
            created.setCategory(ReportCategory.BOX);
            created.setUserId(userId == null ? 0L : userId);
            // Tiêu đề/mô tả này hiện nguyên văn trên cả app khách, app KTV và web
            // admin — vốn là tiếng Anh nên lẫn giữa giao diện tiếng Việt. Các phiếu
            // khác trong service đã đặt tên tiếng Việt ("Bãi đáp drone — ...").
            created.setTitle(title != null ? title : "Ô #" + box.getBoxNumber() + " báo hỏng");
            created.setDescription(StringUtils.hasText(reason) ? reason : "Khách báo ô hỏng");
            applyRouting(created, locker, assigneeId);
            report = reportRepository.save(created);
            attachmentService.attach(
                    report, AttachmentStage.REPORT, attachments, userId, null,
                    rules.reportPhotosPerRequestReporter());
            notifyRouted(report, locker);
        }
        if (statusChanged) {
            announceBoxHold(box, reason);
        }
        if (notifyOrder && orderId != null) {
            notifyBoxIncidentOpened(orderId, LocalDateTime.now(), "FAULT".equalsIgnoreCase(box.getStatus()), userId);
        }
        return report;
    }

    /// Ô vừa vào FAULT / PENDING_CHECK: báo tủ, web admin và app (sơ đồ ô) cập nhật.
    private void announceBoxHold(LockerBox box, String reason) {
        if ("FAULT".equalsIgnoreCase(box.getStatus())) {
            publishBoxFault(box, reason);
        }
        syncBoxStateQuietly(box, box.getStatus());
        publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), box.getStatus(),
                PENDING_CHECK.equalsIgnoreCase(box.getStatus())
                        ? "Ô #" + box.getBoxNumber() + " được báo sự cố, chờ KTV kiểm tra"
                        : "Ô #" + box.getBoxNumber() + " hỏng, tạm ngưng sử dụng");
    }

    /// Báo lại order-service mọi đơn có hàng nằm trong ô đang có phiếu sự cố mở (order-service bỏ qua đơn
    /// đã mở sự cố). Trả số đơn đã báo.
    ///
    /// Đơn phải **đang** nằm trong ô của phiếu: phiếu nhớ `orderId` lúc báo, nhưng KTV có thể đã chuyển
    /// hàng sang ô khác (RELOCATE) trong khi phiếu vẫn mở để sửa ô cũ. Báo lại theo `orderId` của phiếu thì
    /// đơn đang nằm yên trong ô lành bị dừng phí và khoá mở ô lần nữa. Không tra được đơn thì bỏ qua, lần
    /// quét sau thử lại.
    public int syncOpenBoxIncidentsToOrders() {
        int synced = 0;
        Set<Long> seen = new HashSet<>();
        for (LockerReport report : reportRepository.findByStatusInOrderByCreatedAtDesc(OPEN_REPORT_STATUSES)) {
            if (report.getBoxId() == null) {
                continue;
            }
            // Ô đã được KTV sửa tại chỗ / tự gỡ giữ ô (phiếu còn mở chờ nghiệm thu) thì đơn đã lấy đồ được.
            LockerBox box = boxRepository.findById(report.getBoxId()).orElse(null);
            if (box == null || !isFaultHold(box.getStatus())) {
                continue;
            }
            ActiveBoxOrderDto active = getActiveOrderByBox(report.getBoxId());
            Long orderId = active == null ? null : active.orderId();
            if (orderId != null && report.getOrderId() != null && !orderId.equals(report.getOrderId())) {
                continue;
            }
            if (orderId != null && seen.add(orderId)) {
                // Mốc là lúc báo (không phải lúc quét): đơn bị bỏ sót lần trước được trả phần phí tính lố.
                notifyBoxIncidentOpened(orderId, report.getCreatedAt(),
                        "FAULT".equalsIgnoreCase(box.getStatus()), report.getUserId());
                synced++;
            }
        }
        return synced;
    }

    /// Ô đang có hàng của một đơn vừa bị báo sự cố ⇒ order-service dừng phí quá giờ, chặn thu tiền / mở ô
    /// bằng mã tới khi KTV xử lý. Best-effort: lỗi chỉ ghi log, không làm hỏng việc báo hỏng.
    private void notifyBoxIncidentOpened(Long orderId, LocalDateTime reportedAt, boolean confirmed, Long reporterUserId) {
        try {
            orderClient.boxIncidentOpened(orderId, new BoxIncidentNotice(reportedAt, confirmed, reporterUserId));
        } catch (Exception ex) {
            log.warn("Could not open box incident on order {}: {}", orderId, ex.getMessage());
        }
    }

    /// Phiếu báo sự cố có chọn ô (màn "Báo sự cố" của tủ): trước đây chỉ tạo phiếu, ô vẫn AVAILABLE nên
    /// vẫn được cấp cho đơn mới (ô drone có phiếu mở vẫn hiện "Sẵn sàng nhận Drone") và ô có hàng vẫn hiện
    /// như thường. Đưa ô về FAULT, nhớ trạng thái đơn đang giữ ô để đóng phiếu là trả lại đúng
    /// (restoreAssetsAfterClose), và mở sự cố trên đơn nếu ô đang có hàng.
    private void faultBoxOfReport(LockerReport report, Long reporterUserId, LocalDateTime reportedAt, boolean confirmed) {
        LockerBox box = boxRepository.findById(report.getBoxId()).orElse(null);
        if (box == null || !Objects.equals(box.getLockerId(), report.getLockerId())) {
            return;
        }
        String current = box.getStatus();
        String target = confirmed || "FAULT".equalsIgnoreCase(current) ? "FAULT" : PENDING_CHECK;
        if (!target.equalsIgnoreCase(current)) {
            if (!isFaultHold(current)) {
                box.setPreFaultStatus("AVAILABLE".equalsIgnoreCase(current) ? null : current);
                String reason = StringUtils.hasText(report.getDescription()) ? report.getDescription() : report.getTitle();
                box.setFaultReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
            }
            box.setStatus(target);
            boxRepository.save(box);
            announceBoxHold(box, box.getFaultReason());
        }
        if (report.getOrderId() != null) {
            notifyBoxIncidentOpened(report.getOrderId(), reportedAt, "FAULT".equalsIgnoreCase(box.getStatus()), reporterUserId);
        }
    }

    /// Báo trùng một ô đang có phiếu mở: thêm một dòng nhật ký + ảnh hiện trường của người báo vào phiếu cũ.
    private void mergeIntoOpenReport(
            LockerReport report, String reason, Long userId, List<ReportAttachmentRequest> attachments) {
        RepairLog log = new RepairLog();
        log.setReportId(report.getId());
        log.setActorUserId(userId);
        log.setNote("[BÁO LẠI] " + (StringUtils.hasText(reason) ? reason.trim() : "Ô tiếp tục được báo hỏng"));
        Long repairLogId = repairLogRepository.save(log).getId();
        attachmentService.attach(
                report, AttachmentStage.REPORT, attachments, userId, repairLogId,
                rules.reportPhotosPerRequestReporter());
    }

    /// Đưa ô hỏng về hoạt động. Ô đang có phiếu mở phải đóng qua phiếu (người được giao hoặc
    /// admin, áp luật ảnh nghiệm thu) — nút "đã sửa" không được bỏ qua quy trình phiếu.
    @Transactional
    public CellResponse clearFault(Long boxId, Long actorUserId, boolean admin) {
        LockerBox box = findBox(boxId);
        if (!isFaultHold(box.getStatus())) {
            throw new BusinessException("BOX_NOT_FAULT", "Ô không ở trạng thái hỏng", HttpStatus.CONFLICT);
        }
        Optional<LockerReport> open =
                reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(boxId, OPEN_REPORT_STATUSES);
        if (open.isPresent()) {
            closeReportOfAsset(open.get(), actorUserId, admin);
            return toCell(findBox(boxId));
        }
        restoreBox(box);
        return toCell(box);
    }

    /// Ngưng dùng ô có chủ đích (bảo trì/đóng). Ô bị loại khỏi mọi reserve vì
    /// reserveBox chỉ nhận từ AVAILABLE. Dùng faultReason làm ghi chú lý do.
    @Transactional
    public CellResponse setOutOfService(Long boxId, String reason) {
        return changeServiceState(boxId, "OUT_OF_SERVICE", reason, "ngưng dùng");
    }

    /// Đưa ô vào trạng thái đang vệ sinh/khử khuẩn (cũng bị loại khỏi reserve).
    @Transactional
    public CellResponse setCleaning(Long boxId) {
        return changeServiceState(boxId, "CLEANING", null, "vệ sinh");
    }

    /// Khôi phục ô từ OUT_OF_SERVICE/CLEANING về AVAILABLE. Ô đang FAULT phải
    /// dùng clear-fault, không dùng đường này.
    @Transactional
    public CellResponse returnToService(Long boxId) {
        LockerBox box = findBox(boxId);
        String status = box.getStatus();
        if (isFaultHold(status)) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "BOX_IN_FAULT", "Ô đang hỏng — dùng clear-fault để khôi phục");
        }
        if (!"OUT_OF_SERVICE".equalsIgnoreCase(status) && !"CLEANING".equalsIgnoreCase(status)) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "BOX_NOT_OUT_OF_SERVICE", "Ô không ở trạng thái ngưng dùng/vệ sinh");
        }
        box.setStatus("AVAILABLE");
        box.setFaultReason(null);
        return toCell(boxRepository.save(box));
    }

    private CellResponse changeServiceState(Long boxId, String target, String reason, String action) {
        LockerBox box = findBox(boxId);
        String status = box.getStatus();
        if ("OCCUPIED".equalsIgnoreCase(status) || "RESERVED".equalsIgnoreCase(status)) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "BOX_IN_USE", "Ô đang có đơn — không thể " + action);
        }
        // Ô hỏng chỉ về hoạt động qua phiếu/clear-fault; đi đường vệ sinh → khôi phục sẽ lách phiếu.
        if (isFaultHold(status)) {
            throw new BusinessException(
                    "BOX_IN_FAULT", "Ô đang hỏng — hoàn tất phiếu sự cố hoặc dùng clear-fault trước khi " + action,
                    HttpStatus.CONFLICT);
        }
        box.setStatus(target);
        box.setFaultReason(reason);
        return toCell(boxRepository.save(box));
    }

    @Transactional(readOnly = true)
    public LockerLayoutResponse layout(Long lockerId) {
        LockerUnit locker =
                lockerRepository.findById(lockerId).orElseThrow(() -> new NotFoundException("Locker", lockerId));
        List<LockerBox> boxes = boxRepository.findByLockerIdOrderByRowIndexAscColIndexAsc(lockerId);
        Map<Long, IotClient.BoxHardwareStatus> hwByBox = fetchHardwareStatuses(lockerId);
        List<CellResponse> cells = boxes.stream().map(b -> toCell(b, hwByBox.get(b.getId()))).toList();
        long available = isBookable(locker)
                ? boxes.stream()
                        .filter(b -> Boolean.TRUE.equals(b.getActive()) && "AVAILABLE".equalsIgnoreCase(b.getStatus()))
                        .count()
                : 0;
        long fault = boxes.stream().filter(b -> "FAULT".equalsIgnoreCase(b.getStatus())).count();

        Boolean online = null;
        try {
            var gwResp = iotClient.getGatewayByLockerId(lockerId);
            if (gwResp != null && gwResp.data() != null) {
                online = gwResp.data().online();
            }
        } catch (Exception ex) {
            log.debug("Could not fetch gateway online status for locker {}: {}", lockerId, ex.getMessage());
        }

        return new LockerLayoutResponse(
                locker.getId(), locker.getCode(), locker.getName(), locker.getStatus(),
                locker.getLandingPad(), locker.getLandingMarkerId(), locker.getLandingPadStatus(),
                cells.size(), available, fault, cells, online);
    }

    @Transactional
    public LockerLayoutResponse updateLandingPadStatus(Long lockerId, String status, String reason, Long actorUserId) {
        return updateLandingPadStatus(lockerId, status, reason, actorUserId, null);
    }

    /// #6 KTV cap nhat trang thai bao tri bai dap drone: OK / FAULT / MAINTENANCE.
    /// Khác OK ⇒ mở 1 phiếu LANDING_PAD (một phiếu mở mỗi bãi đáp). Về OK khi đang có phiếu
    /// ⇒ hoàn tất phiếu đó (áp luật như ô hỏng); đóng phiếu sẽ tự trả bãi đáp về OK.
    @Transactional
    public LockerLayoutResponse updateLandingPadStatus(
            Long lockerId, String status, String reason, Long actorUserId, String rolesHeader) {
        if (!LANDING_PAD_STATUSES.contains(status)) {
            throw new BusinessException("LANDING_PAD_STATUS_INVALID", "Trạng thái bãi đáp không hợp lệ: " + status);
        }
        LockerUnit locker =
                lockerRepository.findById(lockerId).orElseThrow(() -> new NotFoundException("Locker", lockerId));
        if (!Boolean.TRUE.equals(locker.getLandingPad())) {
            throw new BusinessException("LANDING_PAD_ABSENT", "Tủ này không có bãi đáp drone");
        }
        List<String> roles = actorRoles(actorUserId, rolesHeader);
        Optional<LockerReport> open = reportRepository.findFirstByLockerIdAndCategoryAndStatusInOrderByCreatedAtDesc(
                lockerId, ReportCategory.LANDING_PAD, OPEN_REPORT_STATUSES);
        if ("OK".equals(status) && open.isPresent()) {
            closeReportOfAsset(open.get(), actorUserId, roles.contains("ADMIN"));
            publishLockerAvailabilityChanged(lockerId);
            publishLockerCatalogChanged(lockerId);
            return layout(lockerId);
        }
        locker.setLandingPadStatus(status);
        lockerRepository.save(locker);
        publishLockerAvailabilityChanged(lockerId);
        publishLockerCatalogChanged(lockerId);
        if (!"OK".equals(status) && open.isEmpty()) {
            LockerReport report = new LockerReport();
            report.setLockerId(lockerId);
            report.setCategory(ReportCategory.LANDING_PAD);
            report.setUserId(actorUserId == null ? 0L : actorUserId);
            report.setTitle("Bãi đáp drone — " + locker.getCode());
            report.setDescription(StringUtils.hasText(reason) ? reason : "Bãi đáp cần xử lý: " + status);
            applyRouting(report, locker, roles.contains(LOCKER_TECHNICIAN) ? actorUserId : null);
            notifyRouted(reportRepository.save(report), locker);
        }
        return layout(lockerId);
    }

    /// Tủ đang bảo trì/ngưng hoạt động không nhận đơn hay đặt ô mới (kể cả drone thả hàng).
    private void assertLockerBookable(Long lockerId) {
        lockerRepository.findById(lockerId)
                .filter(locker -> !isBookable(locker))
                .ifPresent(locker -> {
                    throw new BusinessException(
                            "LOCKER_NOT_ACTIVE",
                            "Tủ " + locker.getName() + " đang bảo trì, tạm ngưng nhận đơn",
                            HttpStatus.CONFLICT);
                });
    }

    private boolean isBookable(LockerUnit locker) {
        return locker.getStatus() == null
                || !NON_BOOKABLE_LOCKER_STATUSES.contains(locker.getStatus().toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public CellResponse findAvailableBox(Long lockerId, String size, String cellType) {
        assertLockerBookable(lockerId);
        String type = StringUtils.hasText(cellType) ? cellType.toUpperCase() : "STANDARD";
        if (!StringUtils.hasText(size)) {
            return boxRepository
                    .findFirstByLockerIdAndStatusAndCellTypeAndActiveTrueOrderByBoxNumberAsc(lockerId, "AVAILABLE", type)
                    .map(this::toCell)
                    .orElseThrow(() -> new BusinessException("NO_AVAILABLE_BOX", "No available box matching criteria"));
        }
        // Exact size first; if unavailable, fall back to the next larger size
        // class instead of failing outright (a slightly bigger box still fits).
        String requested = size.toUpperCase();
        int startIndex = Math.max(0, SIZE_ORDER.indexOf(requested));
        for (int i = startIndex; i < SIZE_ORDER.size(); i++) {
            var found =
                    boxRepository.findFirstByLockerIdAndStatusAndCellTypeAndSizeAndActiveTrueOrderByBoxNumberAsc(
                            lockerId, "AVAILABLE", type, SIZE_ORDER.get(i));
            if (found.isPresent()) {
                return toCell(found.get());
            }
        }
        throw new BusinessException("NO_AVAILABLE_BOX", "No available box matching criteria");
    }

    @Transactional(readOnly = true)
    public LockerResponse getLocker(Long id) {
        return toResponse(lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id)));
    }

    @Transactional(readOnly = true)
    public LockerBoxSummary getBox(Long id) {
        return toSummary(findBox(id));
    }

    @Transactional(readOnly = true)
    public List<LockerResponse> listLockers() {
        return toResponses(lockerRepository.findAll(), null);
    }

    @Transactional(readOnly = true)
    public List<LockerResponse> listLockers(Long storeId) {
        return toResponses(storeId == null ? lockerRepository.findAll() : lockerRepository.findByStoreId(storeId), null);
    }

    /// Góc nhìn admin/KTV: kèm KTV phụ trách. `technicianId` ⇒ chỉ các tủ người đó phụ trách.
    @Transactional(readOnly = true)
    public List<LockerResponse> listLockersForStaff(Long storeId, Long technicianId) {
        List<LockerUnit> lockers;
        if (technicianId != null) {
            lockers = lockerRepository.findByAssignedTechnicianId(technicianId);
        } else {
            lockers = storeId == null ? lockerRepository.findAll() : lockerRepository.findByStoreId(storeId);
        }
        return toResponses(lockers, new HashMap<>());
    }

    @Transactional(readOnly = true)
    public LockerResponse getLockerForStaff(Long id) {
        LockerUnit locker = lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id));
        return toStaffResponse(locker, new HashMap<>());
    }

    @Transactional(readOnly = true)
    public List<LockerBoxSummary> listBoxes(Long lockerId) {
        return boxRepository.findByLockerId(lockerId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<LockerBoxSummary> listAvailableBoxes(Long lockerId) {
        boolean bookable = lockerRepository.findById(lockerId).map(this::isBookable).orElse(true);
        if (!bookable) {
            return List.of();
        }
        return boxRepository.findByLockerIdAndStatusAndActiveTrue(lockerId, "AVAILABLE").stream().map(this::toSummary).toList();
    }

    @Transactional
    public LockerReportResponse report(Long lockerId, LockerReportRequest request) {
        return report(lockerId, request, null, null);
    }

    @Transactional
    public LockerReportResponse report(Long lockerId, LockerReportRequest request, Long headerUserId) {
        return report(lockerId, request, headerUserId, null);
    }

    /// Báo sự cố cấp tủ. `headerUserId` (từ JWT qua gateway) được ưu tiên hơn `userId` trong body.
    /// `blocking` (chỉ KTV tủ/ADMIN) đưa tủ vào MAINTENANCE tới khi phiếu đóng — khách không thể
    /// đưa cả tủ ra khỏi hoạt động.
    @Transactional
    public LockerReportResponse report(
            Long lockerId, LockerReportRequest request, Long headerUserId, String rolesHeader) {
        Long userId = headerUserId != null ? headerUserId : request.userId();
        if (userId == null) {
            throw new BusinessException("REPORTER_REQUIRED", "userId is required");
        }
        LockerUnit locker =
                lockerRepository.findById(lockerId).orElseThrow(() -> new NotFoundException("Locker", lockerId));
        List<String> roles = actorRoles(userId, rolesHeader);
        boolean technician = roles.contains(LOCKER_TECHNICIAN);
        boolean blocking = Boolean.TRUE.equals(request.blocking());
        if (blocking && !technician && !roles.contains("ADMIN")) {
            throw new BusinessException(
                    "BLOCKING_REPORT_FORBIDDEN", "Chỉ KTV tủ hoặc admin được báo sự cố ngưng cả tủ",
                    HttpStatus.FORBIDDEN);
        }
        boolean confirmed = isConfirmedReporter(userId, roles);
        if (request.boxId() != null) {
            // Ô đã có phiếu mở (khách báo qua đơn, báo ở màn tủ, KTV tự báo) ⇒ gộp vào phiếu đó như
            // reportBoxFault, không mở phiếu thứ hai cho cùng một ô.
            Optional<LockerReport> open =
                    reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(request.boxId(), OPEN_REPORT_STATUSES);
            if (open.isPresent() && Objects.equals(open.get().getLockerId(), lockerId)) {
                LockerReport existing = open.get();
                if (existing.getOrderId() == null) {
                    Long mergeOrderId = request.orderId();
                    String mergeOrderCode = request.orderCode();
                    if (mergeOrderId == null) {
                        ActiveBoxOrderDto active = getActiveOrderByBox(request.boxId());
                        mergeOrderId = active == null ? null : active.orderId();
                        mergeOrderCode = active == null ? null : active.orderCode();
                    }
                    if (mergeOrderId != null) {
                        existing.setOrderId(mergeOrderId);
                        existing.setOrderCode(mergeOrderCode);
                        reportRepository.save(existing);
                    }
                }
                String reason = StringUtils.hasText(request.description()) ? request.description() : request.title();
                mergeIntoOpenReport(existing, reason, userId, request.attachments());
                // Người báo lần này có thể là chủ đồ (dừng phí từ bây giờ) hoặc KTV (xác nhận hỏng).
                faultBoxOfReport(existing, userId, LocalDateTime.now(), confirmed);
                return toReport(existing);
            }
        }
        LockerReport report = new LockerReport();
        report.setLockerId(lockerId);
        report.setCategory(request.boxId() != null ? ReportCategory.BOX : ReportCategory.LOCKER);
        report.setBoxId(request.boxId());
        report.setBlocksLocker(blocking);
        report.setUserId(userId);
        report.setTitle(request.title());
        report.setDescription(request.description());

        Long orderId = request.orderId();
        String orderCode = request.orderCode();
        if ((orderId == null || !StringUtils.hasText(orderCode)) && request.boxId() != null) {
            ActiveBoxOrderDto activeOrder = getActiveOrderByBox(request.boxId());
            if (activeOrder != null) {
                if (orderId == null) {
                    orderId = activeOrder.orderId();
                }
                if (!StringUtils.hasText(orderCode)) {
                    orderCode = activeOrder.orderCode();
                }
            }
        }
        report.setOrderId(orderId);
        report.setOrderCode(orderCode);

        applyRouting(report, locker, technician ? userId : null);
        LockerReport saved = reportRepository.save(report);
        attachmentService.attach(
                saved, AttachmentStage.REPORT, request.attachments(), userId, null,
                rules.reportPhotosPerRequestReporter());
        if (request.boxId() != null) {
            faultBoxOfReport(saved, userId, LocalDateTime.now(), confirmed);
        }
        if (blocking && "ACTIVE".equalsIgnoreCase(locker.getStatus())) {
            locker.setStatus("MAINTENANCE");
            locker.setMaintenanceSource(MAINTENANCE_SOURCE_TICKET);
            lockerRepository.save(locker);
            publishLockerAvailabilityChanged(locker.getId());
            publishLockerCatalogChanged(locker.getId());
        }
        notifyRouted(saved, locker);
        return toReport(saved);
    }

    @Transactional
    public LockerReportResponse resolveReport(Long reportId, Long userId) {
        return resolveReport(reportId, userId, null);
    }

    /// Admin đóng phiếu từ web — không bắt buộc ảnh nghiệm thu (có thể là phiếu trùng/nhầm),
    /// nhưng vẫn trả ô/bãi đáp/tủ về hoạt động như khi KTV hoàn tất.
    @Transactional
    public LockerReportResponse resolveReport(Long reportId, Long userId, ResolveReportRequest request) {
        return resolveReportAndClearFault(reportId, userId, request, true);
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> myReports(Long userId) {
        return myReports(userId, null, null);
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> myReports(Long userId, Long orderId, String orderCode) {
        if (orderId != null) {
            return toReports(reportRepository.findByUserIdAndOrderIdOrderByCreatedAtDesc(userId, orderId));
        }
        if (StringUtils.hasText(orderCode)) {
            return toReports(reportRepository.findByUserIdAndOrderCodeOrderByCreatedAtDesc(userId, orderCode));
        }
        return toReports(reportRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> allReports() {
        return toReports(reportRepository.findAllByOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public LockerReportResponse getReport(Long reportId) {
        return toReport(
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId)));
    }

    @Transactional
    public void deleteReport(Long reportId) {
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        doDeleteReport(report);
    }

    private void doDeleteReport(LockerReport report) {
        Long reportId = report.getId();
        if (OPEN_REPORT_STATUSES.contains(report.getStatus())) {
            restoreAssetsAfterClose(report);
        }

        // 1. Unlink inspection logs
        List<MaintenanceInspectionLog> inspectionLogs = inspectionLogRepository.findByCreatedReportId(reportId);
        for (MaintenanceInspectionLog log : inspectionLogs) {
            log.setCreatedReportId(null);
            inspectionLogRepository.save(log);
        }

        // 2. Clear schedule pendingReportId if pointing to this report
        for (MaintenanceSchedule schedule : scheduleRepository.findByPendingReportId(reportId)) {
            schedule.setPendingReportId(null);
            scheduleRepository.save(schedule);
        }

        // 3. Delete attachments & cloudinary images
        attachmentService.deleteByReportId(reportId);

        // 4. Delete repair logs
        List<RepairLog> logs = repairLogRepository.findByReportIdOrderByCreatedAtAsc(reportId);
        if (!logs.isEmpty()) {
            repairLogRepository.deleteAll(logs);
        }

        // 5. Delete ratings
        ratingRepository.findByReportId(reportId).ifPresent(ratingRepository::delete);

        // 6. Delete report entity
        reportRepository.delete(report);
    }

    @Transactional
    public int deleteReportsBatch(BatchDeleteReportsRequest request) {
        List<LockerReport> targetReports;
        boolean isAll = request != null && Boolean.TRUE.equals(request.deleteAll());
        String category = request != null ? request.category() : null;

        if (isAll) {
            targetReports = reportRepository.findAll();
        } else if (request != null && request.reportIds() != null && !request.reportIds().isEmpty()) {
            targetReports = reportRepository.findAllById(request.reportIds());
        } else {
            return 0;
        }

        if (StringUtils.hasText(category)) {
            if ("DRONE".equalsIgnoreCase(category)) {
                targetReports = targetReports.stream()
                        .filter(r -> "DRONE".equalsIgnoreCase(r.getCategory()) || r.getDroneUnitId() != null)
                        .toList();
            } else if ("LOCKER".equalsIgnoreCase(category) || "KIOSK".equalsIgnoreCase(category)) {
                targetReports = targetReports.stream()
                        .filter(r -> !"DRONE".equalsIgnoreCase(r.getCategory()) && r.getDroneUnitId() == null)
                        .toList();
            }
        }

        if (targetReports.isEmpty()) {
            return 0;
        }

        for (LockerReport report : targetReports) {
            doDeleteReport(report);
        }

        return targetReports.size();
    }

    @Transactional(readOnly = true)
    public List<LockerStatsResponse> stats(Long storeId) {
        return lockerRepository.findAll().stream()
                .filter(locker -> storeId == null || storeId.equals(locker.getStoreId()))
                .map(this::toStats)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FaultCellResponse> openFaults() {
        return java.util.stream.Stream.concat(
                        boxRepository.findByStatusAndActiveTrueOrderByLockerIdAscBoxNumberAsc("FAULT").stream(),
                        boxRepository.findByStatusAndActiveTrueOrderByLockerIdAscBoxNumberAsc(PENDING_CHECK).stream())
                .map(box -> {
                    LockerUnit locker = lockerRepository.findById(box.getLockerId()).orElse(null);
                    Long reportId =
                            reportRepository
                                    .findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(box.getId(), OPEN_REPORT_STATUSES)
                                    .map(LockerReport::getId)
                                    .orElse(null);
                    return new FaultCellResponse(
                            box.getLockerId(),
                            locker == null ? null : locker.getCode(),
                            locker == null ? null : locker.getName(),
                            locker == null ? null : locker.getAddress(),
                            locker == null ? null : locker.getLatitude(),
                            locker == null ? null : locker.getLongitude(),
                            box.getId(),
                            box.getBoxNumber(),
                            box.getCellType(),
                            box.getRowIndex(),
                            box.getColIndex(),
                            box.getFaultReason(),
                            reportId);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> openReports() {
        return toReports(reportRepository.findByStatusInOrderByCreatedAtDesc(OPEN_REPORT_STATUSES));
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> assignedReports(Long userId) {
        return reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(userId).stream()
                .sorted(Comparator.comparing(
                        (LockerReport r) -> r.getAssignedAt() != null ? r.getAssignedAt() : r.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(LockerReport::getId, Comparator.reverseOrder()))
                .map(this::toReport)
                .toList();
    }

    /** Phiếu sự cố chỉ thuộc đội Drone; không lẫn sự cố tủ/Kiosk. */
    @Transactional(readOnly = true)
    public List<LockerReportResponse> allDroneReports() {
        List<LockerReport> reports = reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(this::isDroneReport)
                .toList();
        return toReports(reports);
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> assignedDroneReports(Long userId) {
        List<LockerReport> reports = reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(this::isDroneReport)
                .sorted(Comparator.comparing(
                        (LockerReport r) -> r.getAssignedAt() != null ? r.getAssignedAt() : r.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LockerReport::getId, Comparator.reverseOrder()))
                .toList();
        return toReports(reports);
    }

    /** OPEN được định tuyến riêng cho KTV Drone hoặc broadcast cho toàn bộ đội Drone. */
    @Transactional(readOnly = true)
    public List<LockerReportResponse> routedDroneReports(Long userId) {
        List<LockerReport> reports = reportRepository.findByStatusInOrderByCreatedAtDesc(List.of("OPEN")).stream()
                .filter(this::isDroneReport)
                .filter(report -> report.getRoutedToUserId() == null || Objects.equals(report.getRoutedToUserId(), userId))
                .toList();
        return toReports(reports);
    }

    /**
     * Danh sách sự cố hiển thị cho KTV Drone theo đúng phân công và phụ trách:
     * 1. Sự cố đã có người nhận: CHỈ hiển thị cho người đó.
     * 2. Sự cố mới / chưa ai nhận:
     *    - Drone có KTV phụ trách: CHỈ hiển thị cho KTV phụ trách drone đó xem và nhận việc.
     *    - Drone chưa có KTV phụ trách: hiển thị cho tất cả KTV drone trong danh sách chờ nhận.
     */
    @Transactional(readOnly = true)
    public List<LockerReportResponse> visibleDroneReportsForTechnician(Long userId) {
        return allDroneReports().stream()
                .filter(report -> {
                    if (report.assignedToUserId() != null) {
                        return Objects.equals(report.assignedToUserId(), userId);
                    }
                    if (report.routedToUserId() != null) {
                        return Objects.equals(report.routedToUserId(), userId);
                    }
                    return true;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public LockerReportResponse getDroneReport(Long reportId) {
        return toReport(requireDroneReport(reportId));
    }

    @Transactional
    public LockerReportResponse claimDroneReport(Long reportId, Long userId) {
        requireDroneReport(reportId);
        return claimReport(reportId, userId);
    }

    @Transactional
    public LockerReportResponse resolveDroneReport(
            Long reportId, Long userId, ResolveReportRequest request, boolean admin) {
        requireDroneReport(reportId);
        return resolveReportAndClearFault(reportId, userId, request, admin);
    }

    /** Guard dùng chung cho các endpoint ảnh/nhật ký của đội Drone. */
    @Transactional(readOnly = true)
    public void assertDroneReport(Long reportId) {
        requireDroneReport(reportId);
    }

    private LockerReport requireDroneReport(Long reportId) {
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        if (!isDroneReport(report)) {
            throw new BusinessException("DRONE_REPORT_REQUIRED", "Phiếu này không thuộc đội kỹ thuật Drone");
        }
        return report;
    }

    private boolean isDroneReport(LockerReport report) {
        // Legacy drone reports predate the DRONE category. The drone-unit link
        // remains the canonical relationship for those reports.
        return ReportCategory.DRONE.equals(report.getCategory())
                || report.getDroneUnitId() != null;
    }

    /// Phiếu OPEN đang được định tuyến cho KTV (tủ người đó phụ trách), chờ nhận.
    @Transactional(readOnly = true)
    public List<LockerReportResponse> routedReports(Long userId) {
        return toReports(reportRepository.findByRoutedToUserIdAndStatusOrderByCreatedAtDesc(userId, "OPEN"));
    }

    @Transactional
    public LockerReportResponse claimReport(Long reportId, Long userId) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        if (!"OPEN".equals(report.getStatus())) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "REPORT_NOT_CLAIMABLE", "Phiếu không còn ở trạng thái chờ tiếp nhận");
        }
        // Kiểm tra chế tài vi phạm SLA: KTV đang giữ từ N phiếu trễ hạn trở lên (admin cấu hình) thì chặn nhận việc mới
        int slaHours = rules.slaHours();
        long overdueCount = reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> "IN_PROGRESS".equalsIgnoreCase(r.getStatus()))
                .filter(r -> {
                    LocalDateTime due = r.getSlaDueAt();
                    if (due == null && r.getCreatedAt() != null) {
                        due = r.getCreatedAt().plusHours(slaHours);
                    }
                    return due != null && java.time.LocalDateTime.now().isAfter(due);
                })
                .count();
        if (overdueCount >= rules.technicianClaimBlockOverdue()) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "TECHNICIAN_SLA_RESTRICTED",
                    "Kỹ thuật viên đang có " + overdueCount + " sự cố trễ hạn SLA. Vui lòng xử lý dứt điểm các ca tồn đọng trước khi nhận việc mới.");
        }

        report.setStatus("IN_PROGRESS");
        report.setAssignedToUserId(userId);
        report.setAssignedAt(java.time.LocalDateTime.now());
        LockerReport saved = reportRepository.save(report);
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_CLAIMED,
                "đang được đội bảo trì xử lý");
        return toReport(saved);
    }

    /// Admin chủ động phân công một phiếu sự cố cho kỹ thuật viên đúng mảng (phiếu drone ⇒ KTV drone).
    @Transactional
    public LockerReportResponse assignReport(Long reportId, Long technicianId) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        assertNotResolved(report);
        requireTechnician(technicianId, isDroneReport(report) ? DRONE_TECHNICIAN : LOCKER_TECHNICIAN);
        report.setStatus("IN_PROGRESS");
        report.setAssignedToUserId(technicianId);
        report.setAssignedAt(java.time.LocalDateTime.now());
        LockerReport saved = reportRepository.save(report);
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_CLAIMED,
                "đang được đội bảo trì xử lý");
        if (isDroneReport(saved)) {
            publishStaffNotification(
                    DRONE_REPORT_ASSIGNED, technicianId, saved.getId(), DRONE_REPORT_REFERENCE,
                    "Admin giao cho bạn phiếu RPT-" + saved.getId() + " — " + saved.getTitle()
                            + " (drone " + droneLabel(saved.getDroneUnitId()) + ").");
        } else {
            publishStaffNotification(
                    DomainEventNames.LOCKER_REPORT_ASSIGNED, technicianId, saved.getId(), "LOCKER_REPORT",
                    "Admin giao cho bạn phiếu RPT-" + saved.getId() + " — " + saved.getTitle()
                            + " tại tủ " + lockerLabel(saved.getLockerId()) + ".");
        }
        return toReport(saved);
    }

    /// Admin thu hồi phân công phiếu sự cố (chuyển lại về OPEN để giao cho KTV khác)
    @Transactional
    public LockerReportResponse unassignReport(Long reportId) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        assertNotResolved(report);
        report.setStatus("OPEN");
        report.setAssignedToUserId(null);
        report.setAssignedAt(null);
        return toReport(reportRepository.save(report));
    }

    /// KTV đang giữ phiếu trả phiếu về hàng chờ (OPEN, bỏ người được giao) kèm lý do. Phiếu được định
    /// tuyến lại như lúc tạo (KTV phụ trách tủ / drone) nhưng không về lại chính người vừa trả; ghi một
    /// dòng nhật ký và báo các KTV có thể nhận.
    @Transactional
    public LockerReportResponse releaseReport(Long reportId, Long actorUserId, String reason, boolean admin) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        assertNotResolved(report);
        if (!admin && (actorUserId == null || !actorUserId.equals(report.getAssignedToUserId()))) {
            throw new BusinessException(
                    "REPORT_NOT_ASSIGNED", "Chỉ KTV đang giữ phiếu mới trả được phiếu về hàng chờ",
                    HttpStatus.FORBIDDEN);
        }
        if (report.getAssignedToUserId() == null) {
            throw new BusinessException("REPORT_NOT_CLAIMED", "Phiếu đang ở hàng chờ, chưa có KTV nhận", HttpStatus.CONFLICT);
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("REPORT_RELEASE_REASON_REQUIRED", "Cần nêu lý do trả phiếu về hàng chờ");
        }
        Long releasedBy = report.getAssignedToUserId();
        boolean drone = isDroneReport(report);
        LockerUnit locker = lockerRepository.findById(report.getLockerId()).orElse(null);
        Long routeTo = drone
                ? (report.getDroneUnitId() == null ? null : droneUnitRepository.findById(report.getDroneUnitId())
                        .map(DroneUnit::getAssignedTechnicianId).orElse(null))
                : (locker == null ? null : locker.getAssignedTechnicianId());
        report.setStatus("OPEN");
        report.setAssignedToUserId(null);
        report.setAssignedAt(null);
        report.setRoutedToUserId(Objects.equals(routeTo, releasedBy) ? null : routeTo);
        LockerReport saved = reportRepository.save(report);

        RepairLog entry = new RepairLog();
        entry.setReportId(saved.getId());
        entry.setActorUserId(actorUserId);
        entry.setNote("[TRẢ PHIẾU] Trả phiếu về hàng chờ để KTV khác nhận. Lý do: " + reason.trim());
        repairLogRepository.save(entry);

        if (drone) {
            notifyDroneRouted(saved, releasedBy);
        } else {
            notifyRouted(saved, locker, releasedBy);
        }
        return toReport(saved);
    }

    /// Admin gia hạn SLA trên web: không giới hạn số lần, không tính vào lượt tự gia hạn của KTV,
    /// lý do không bắt buộc (giữ tương thích web admin).
    @Transactional
    public LockerReportResponse extendSla(Long reportId, Integer extensionHours, String reason, Long actorUserId) {
        requireExtensionHours(extensionHours);
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        assertNotResolved(report);
        String reasonText = StringUtils.hasText(reason) ? reason.trim() : null;
        LocalDateTime newDue = applySlaExtension(report, extensionHours, reasonText);
        LockerReport saved = reportRepository.save(report);
        appendSlaExtensionLog(saved, actorUserId,
                "[GIA HẠN SLA] Admin gia hạn thêm +" + extensionHours + " giờ.", newDue, reasonText);
        return toReport(saved);
    }

    /// KTV đang được giao phiếu tự gia hạn SLA: bắt buộc lý do, tối đa `slaMaxExtensions` lần mỗi phiếu,
    /// mỗi lần ≤ `slaMaxHoursPerExtension` giờ (admin cấu hình). Mỗi lần ghi một dòng nhật ký.
    @Transactional
    public LockerReportResponse extendSlaAsTechnician(
            Long reportId, Integer hours, String reason, Long actorUserId) {
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        assertNotResolved(report);
        if (actorUserId == null || !actorUserId.equals(report.getAssignedToUserId())) {
            throw new BusinessException(
                    "SLA_EXTENSION_NOT_ASSIGNEE", "Chỉ KTV đang được giao phiếu mới gia hạn được SLA",
                    HttpStatus.FORBIDDEN);
        }
        int used = report.getSlaExtensionCount() == null ? 0 : report.getSlaExtensionCount();
        int max = rules.slaMaxExtensions();
        if (used >= max) {
            throw new BusinessException(
                    "SLA_EXTENSION_LIMIT_REACHED",
                    "Phiếu đã gia hạn SLA " + used + "/" + max + " lần — liên hệ admin nếu cần thêm thời gian",
                    HttpStatus.CONFLICT);
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("SLA_EXTENSION_REASON_REQUIRED", "Cần nêu lý do gia hạn SLA");
        }
        requireExtensionHours(hours);
        int maxHours = rules.slaMaxHoursPerExtension();
        if (hours > maxHours) {
            throw new BusinessException(
                    "SLA_EXTENSION_HOURS_EXCEEDED", "Mỗi lần chỉ được gia hạn tối đa " + maxHours + " giờ");
        }
        String reasonText = reason.trim();
        LocalDateTime newDue = applySlaExtension(report, hours, reasonText);
        report.setSlaExtensionCount(used + 1);
        LockerReport saved = reportRepository.save(report);
        appendSlaExtensionLog(saved, actorUserId,
                "[GIA HẠN SLA] KTV gia hạn thêm +" + hours + " giờ (lần " + (used + 1) + "/" + max + ").",
                newDue, reasonText);
        return toReport(saved);
    }

    private static void requireExtensionHours(Integer hours) {
        if (hours == null || hours < 1) {
            throw new BusinessException("INVALID_EXTENSION_HOURS", "Thời gian gia hạn phải từ 1 giờ trở lên");
        }
    }

    /// Cộng giờ vào hạn xử lý; phiếu đã quá hạn thì tính từ bây giờ. Trả về hạn mới.
    private LocalDateTime applySlaExtension(LockerReport report, int hours, String reason) {
        int currentExt = report.getSlaExtendedHours() != null ? report.getSlaExtendedHours() : 0;
        report.setSlaExtendedHours(currentExt + hours);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentDue = report.getSlaDueAt();
        if (currentDue == null) {
            currentDue = report.getCreatedAt() != null ? report.getCreatedAt().plusHours(rules.slaHours()) : now;
        }
        LocalDateTime newDue = (now.isAfter(currentDue) ? now : currentDue).plusHours(hours);
        report.setSlaDueAt(newDue);
        if (reason != null) {
            report.setSlaExtensionReason(reason);
        }
        return newDue;
    }

    private void appendSlaExtensionLog(
            LockerReport report, Long actorUserId, String header, LocalDateTime newDue, String reason) {
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
        RepairLog entry = new RepairLog();
        entry.setReportId(report.getId());
        entry.setActorUserId(actorUserId);
        entry.setNote(header + "\n- Hạn hoàn tất mới: " + newDue.format(dtf)
                + (reason != null ? "\n- Lý do: " + reason : ""));
        repairLogRepository.save(entry);
    }

    /// Tổng hợp chỉ số hiệu suất, số lần vi phạm SLA, mức độ chế tài và đánh giá của một KTV
    @Transactional(readOnly = true)
    public Map<String, Object> technicianPerformance(Long technicianId) {
        List<LockerReport> allAssigned = reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(technicianId);
        int total = allAssigned.size();
        long inProgress = allAssigned.stream().filter(r -> "IN_PROGRESS".equalsIgnoreCase(r.getStatus())).count();
        long resolved = allAssigned.stream().filter(r -> "RESOLVED".equalsIgnoreCase(r.getStatus())).count();
        int slaHours = rules.slaHours();
        long overdue = allAssigned.stream()
                .filter(r -> !isClosedReport(r.getStatus()))
                .filter(r -> {
                    LocalDateTime due = r.getSlaDueAt();
                    if (due == null && r.getCreatedAt() != null) {
                        due = r.getCreatedAt().plusHours(slaHours);
                    }
                    return due != null && java.time.LocalDateTime.now().isAfter(due);
                })
                .count();

        // Xác định bậc chế tài (Penalty Level) theo ngưỡng admin cấu hình
        LockerRules.PenaltyThresholds thresholds = rules.penaltyThresholds();
        String penaltyLevel;
        String penaltyReason;
        if (overdue >= thresholds.suspended()) {
            penaltyLevel = "SUSPENDED";
            penaltyReason = "Vi phạm nghiêm trọng: có từ " + thresholds.suspended()
                    + " phiếu trễ hạn SLA. Đề xuất đình chỉ công tác & khóa tài khoản.";
        } else if (overdue >= thresholds.restricted()) {
            penaltyLevel = "RESTRICTED";
            penaltyReason = "Hạn chế nhận việc: có " + overdue + " phiếu trễ hạn SLA. Tạm ngưng phân công mới.";
        } else if (overdue >= thresholds.warning()) {
            penaltyLevel = "WARNING";
            penaltyReason = "Cảnh báo SLA: có " + overdue + " phiếu trễ hạn SLA cần đẩy nhanh tiến độ.";
        } else {
            penaltyLevel = "NORMAL";
            penaltyReason = "Hiệu suất hoạt động tốt, không có phiếu trễ hạn.";
        }

        List<Long> reportIds = allAssigned.stream().map(LockerReport::getId).toList();
        List<LockerReportRating> ratings = reportIds.isEmpty() ? List.of() : ratingRepository.findByReportIdIn(reportIds);
        double average = ratings.stream().mapToInt(LockerReportRating::getRating).average().orElse(0.0);

        List<Map<String, Object>> ratingList = ratings.stream().map(r -> {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", r.getId());
            item.put("reportId", r.getReportId());
            item.put("rating", r.getRating());
            item.put("comment", r.getComment() == null ? "" : r.getComment());
            item.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            return item;
        }).toList();

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("technicianId", technicianId);
        result.put("totalAssigned", total);
        result.put("inProgress", inProgress);
        result.put("resolved", resolved);
        result.put("overdue", overdue);
        result.put("penaltyLevel", penaltyLevel);
        result.put("penaltyReason", penaltyReason);
        result.put("ratingCount", ratings.size());
        result.put("averageRating", Math.round(average * 10) / 10.0);
        result.put("ratings", ratingList);
        return result;
    }

    // Resolving a report tied to a faulty cell also returns the cell to service —
    // the technician confirms the physical repair in one step.
    @Transactional
    public LockerReportResponse resolveReportAndClearFault(Long reportId, Long userId) {
        return resolveReportAndClearFault(reportId, userId, null, false);
    }

    /// Hoàn tất từ app KTV: ảnh nghiệm thu (stage RESOLUTION) + ghi chú được lưu trước khi đóng phiếu.
    /// Không phải admin thì phải là KTV đang được giao phiếu.
    @Transactional
    public LockerReportResponse resolveReportAndClearFault(
            Long reportId, Long userId, ResolveReportRequest request, boolean admin) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        return toReport(closeReport(report, userId, request, admin));
    }

    private LockerReport closeReport(
            LockerReport report, Long userId, ResolveReportRequest request, boolean admin) {
        assertNotResolved(report);
        if (!admin && (userId == null || !userId.equals(report.getAssignedToUserId()))) {
            throw new BusinessException(
                    "REPORT_NOT_ASSIGNED", "Chỉ KTV đang được giao phiếu mới hoàn tất được phiếu",
                    HttpStatus.FORBIDDEN);
        }
        attachResolutionEvidence(report, userId, admin, request);
        if (rules.requireResolutionPhoto() && !admin && !attachmentService.hasStage(report.getId(), AttachmentStage.RESOLUTION)) {
            throw new BusinessException(
                    "RESOLUTION_PHOTO_REQUIRED", "Cần ít nhất một ảnh nghiệm thu trước khi hoàn tất phiếu");
        }
        report.setStatus("RESOLVED");
        report.setResolvedByUserId(userId);
        report.setResolvedAt(java.time.LocalDateTime.now());
        LockerReport saved = reportRepository.save(report);
        restoreAssetsAfterClose(saved);
        if (saved.getOrderId() != null && !hasOtherOpenReportForOrder(saved)) {
            notifyBoxIncidentResolved(saved.getOrderId(), "Hoàn tất phiếu sự cố RPT-" + saved.getId());
        }
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_RESOLVED,
                "đã được xử lý xong");
        return saved;
    }

    private boolean hasOtherOpenReportForOrder(LockerReport report) {
        return reportRepository.findByOrderIdOrderByCreatedAtDesc(report.getOrderId()).stream()
                .anyMatch(r -> !r.getId().equals(report.getId())
                        && OPEN_REPORT_STATUSES.contains(r.getStatus()));
    }

    /// Đơn có hàng không còn kẹt vì sự cố ô ⇒ order-service mở lại việc lấy đồ và bù thời gian chờ KTV
    /// vào hạn (phí quá giờ đã dừng từ lúc khách báo). Best-effort: lỗi chỉ ghi log — admin còn gỡ được
    /// bằng tay trên đơn; không được làm hỏng việc KTV đóng phiếu.
    private void notifyBoxIncidentResolved(Long orderId, String note) {
        try {
            orderClient.boxIncidentResolved(orderId, note);
        } catch (Exception ex) {
            log.warn("Could not resume order {} after box incident: {}", orderId, ex.getMessage());
        }
    }

    /// Khôi phục ô/bãi đáp đang có phiếu mở = hoàn tất phiếu đó. Người chưa được giao phiếu nhận
    /// thông báo rõ ràng thay vì lỗi chung chung.
    private void closeReportOfAsset(LockerReport report, Long actorUserId, boolean admin) {
        if (!admin && (actorUserId == null || !actorUserId.equals(report.getAssignedToUserId()))) {
            throw new BusinessException(
                    "REPORT_OPEN",
                    "Đang có phiếu sự cố RPT-" + report.getId() + " — nhận phiếu rồi hoàn tất phiếu để khôi phục",
                    HttpStatus.CONFLICT);
        }
        closeReport(report, actorUserId, null, admin);
    }

    private void assertNotResolved(LockerReport report) {
        if (isClosedReport(report.getStatus())) {
            throw new BusinessException("REPORT_ALREADY_RESOLVED", "Phiếu đã được hoàn tất", HttpStatus.CONFLICT);
        }
    }

    /// Đóng phiếu ⇒ trả tài sản về hoạt động nếu không còn phiếu mở nào khác giữ nó: ô về trạng
    /// thái trước khi hỏng, bãi đáp về OK, tủ do phiếu chặn về ACTIVE, lịch kiểm tra đang chờ
    /// phiếu này thì dời hạn.
    private void restoreAssetsAfterClose(LockerReport report) {
        if (report.getDroneUnitId() != null
                && !reportRepository.existsByDroneUnitIdAndStatusInAndIdNot(
                        report.getDroneUnitId(), OPEN_REPORT_STATUSES, report.getId())) {
            synchronizeDroneOperationalStatus(report.getDroneUnitId(), report.getResolvedByUserId());
        }
        if (report.getBoxId() != null
                && !reportRepository.existsByBoxIdAndStatusInAndIdNot(
                        report.getBoxId(), OPEN_REPORT_STATUSES, report.getId())) {
            boxRepository.findById(report.getBoxId())
                    .filter(box -> isFaultHold(box.getStatus()))
                    .ifPresent(this::restoreBox);
        }
        if (ReportCategory.LANDING_PAD.equals(report.getCategory())
                && !reportRepository.existsByLockerIdAndCategoryAndStatusInAndIdNot(
                        report.getLockerId(), ReportCategory.LANDING_PAD, OPEN_REPORT_STATUSES, report.getId())) {
            lockerRepository.findById(report.getLockerId())
                    .filter(locker -> !"OK".equals(locker.getLandingPadStatus()))
                    .ifPresent(locker -> {
                        locker.setLandingPadStatus("OK");
                        lockerRepository.save(locker);
                        publishLockerAvailabilityChanged(locker.getId());
                        publishLockerCatalogChanged(locker.getId());
                    });
        }
        if (Boolean.TRUE.equals(report.getBlocksLocker())
                && !reportRepository.existsByLockerIdAndBlocksLockerTrueAndStatusInAndIdNot(
                        report.getLockerId(), OPEN_REPORT_STATUSES, report.getId())) {
            lockerRepository.findById(report.getLockerId())
                    .filter(locker -> "MAINTENANCE".equalsIgnoreCase(locker.getStatus())
                            && MAINTENANCE_SOURCE_TICKET.equals(locker.getMaintenanceSource()))
                    .ifPresent(locker -> {
                        locker.setStatus("ACTIVE");
                        locker.setMaintenanceSource(null);
                        lockerRepository.save(locker);
                        publishLockerAvailabilityChanged(locker.getId());
                        publishLockerCatalogChanged(locker.getId());
                    });
        }
        // Tra theo lịch (không theo report.scheduleId): kiểm tra KHÔNG ĐẠT có thể gộp vào phiếu ô đã mở sẵn.
        for (MaintenanceSchedule schedule : scheduleRepository.findByPendingReportId(report.getId())) {
            schedule.setPendingReportId(null);
            schedule.setNextDueAt(LocalDateTime.now().plusDays(schedule.getIntervalDays()));
            schedule.setLastDueNotifiedAt(null);
            scheduleRepository.save(schedule);
        }
    }

    private void restoreBox(LockerBox box) {
        String target;
        if (StringUtils.hasText(box.getPreFaultStatus())) {
            target = box.getPreFaultStatus();
        } else {
            ActiveBoxOrderDto activeOrder = requireActiveOrderLookup(box.getId());
            target = (activeOrder != null) ? "OCCUPIED" : "AVAILABLE";
        }
        box.setStatus(target);
        box.setPreFaultStatus(null);
        box.setFaultReason(null);
        LockerBox saved = boxRepository.save(box);
        syncBoxStateQuietly(saved, target);
    }

    /// L5: kỹ thuật viên thêm 1 dòng nhật ký xử lý vào phiếu bảo trì.
    @Transactional
    public RepairLogResponse addRepairLog(Long reportId, String note, Long actorUserId) {
        return addRepairLog(reportId, note, actorUserId, List.of(), false);
    }

    /// Nhật ký kèm ảnh quá trình sửa (stage PROGRESS) — ảnh chỉ nhận từ KTV được giao hoặc ADMIN.
    @Transactional
    public RepairLogResponse addRepairLog(
            Long reportId, String note, Long actorUserId, List<ReportAttachmentRequest> attachments, boolean admin) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("Report", reportId));
        if (!StringUtils.hasText(note)) {
            throw new BusinessException("REPAIR_LOG_NOTE_REQUIRED", "note is required");
        }
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        if (hasAttachments) {
            attachmentService.assertCanAttachAsStaff(report, actorUserId, admin);
        }
        RepairLog log = new RepairLog();
        log.setReportId(report.getId());
        log.setActorUserId(actorUserId);
        log.setNote(note);
        RepairLog saved = repairLogRepository.save(log);
        List<ReportAttachmentResponse> savedAttachments = attachmentService.attach(
                report, AttachmentStage.PROGRESS, attachments, actorUserId, saved.getId(),
                rules.reportPhotosPerRequestStaff());
        return toRepairLog(saved, savedAttachments);
    }

    @Transactional(readOnly = true)
    public List<RepairLogResponse> repairLogs(Long reportId) {
        List<RepairLog> logs = repairLogRepository.findByReportIdOrderByCreatedAtAsc(reportId);
        Map<Long, List<ReportAttachmentResponse>> attachments =
                attachmentService.byRepairLogIds(logs.stream().map(RepairLog::getId).toList());
        Map<Long, Optional<UserSummary>> users = new HashMap<>();
        return logs.stream()
                .map(log -> toRepairLog(log, attachments.getOrDefault(log.getId(), List.of()), users))
                .toList();
    }

    private RepairLogResponse toRepairLog(RepairLog log, List<ReportAttachmentResponse> attachments) {
        return toRepairLog(log, attachments, new HashMap<>());
    }

    private RepairLogResponse toRepairLog(
            RepairLog log, List<ReportAttachmentResponse> attachments, Map<Long, Optional<UserSummary>> users) {
        UserSummary actor = cachedUser(log.getActorUserId(), users);
        return new RepairLogResponse(
                log.getId(), log.getReportId(), log.getActorUserId(), log.getNote(), log.getCreatedAt(), attachments,
                actor == null ? null : actor.fullName());
    }

    /// Tra user một lần cho mỗi id trong một lần dựng response (user-service chưa có API tra theo lô).
    private UserSummary cachedUser(Long userId, Map<Long, Optional<UserSummary>> cache) {
        if (userId == null || userId == 0L) {
            return null;
        }
        return cache.computeIfAbsent(userId, id -> Optional.ofNullable(lookupUserQuietly(id))).orElse(null);
    }

    @Transactional(readOnly = true)
    public LockerReportResponse getReportForUser(Long reportId, Long userId) {
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        return toReport(report);
    }

    @Transactional(readOnly = true)
    public List<RepairLogResponse> repairLogsForUser(Long reportId, Long userId) {
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        return repairLogs(reportId);
    }

    /// Ghi chú + ảnh nghiệm thu gửi kèm lúc hoàn tất. Không gửi gì ⇒ giữ hành vi cũ.
    private void attachResolutionEvidence(
            LockerReport report, Long actorUserId, boolean admin, ResolveReportRequest request) {
        if (request == null) {
            return;
        }
        boolean hasAttachments = request.attachments() != null && !request.attachments().isEmpty();
        if (hasAttachments) {
            attachmentService.assertCanAttachAsStaff(report, actorUserId, admin);
        }
        Long repairLogId = null;
        if (StringUtils.hasText(request.note())) {
            RepairLog log = new RepairLog();
            log.setReportId(report.getId());
            log.setActorUserId(actorUserId);
            String noteText = request.note().trim();
            if (!noteText.startsWith("[")) {
                noteText = "[NGHIỆM THU THÀNH CÔNG] " + noteText;
            }
            log.setNote(noteText);
            repairLogId = repairLogRepository.save(log).getId();
        }
        attachmentService.attach(
                report, AttachmentStage.RESOLUTION, request.attachments(), actorUserId, repairLogId,
                rules.reportPhotosPerRequestStaff());
    }

    // ---- L5: bảo trì phòng ngừa (lịch kiểm tra định kỳ) ----

    @Transactional
    public MaintenanceScheduleResponse createSchedule(MaintenanceScheduleRequest request) {
        // Lich nham vao 1 tu HOAC 1 drone — bat buoc dung 1 trong 2.
        if ((request.lockerId() == null) == (request.droneUnitId() == null)) {
            throw new BusinessException(
                    "SCHEDULE_TARGET_INVALID", "Provide exactly one of lockerId or droneUnitId");
        }
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        if (request.lockerId() != null) {
            lockerRepository
                    .findById(request.lockerId())
                    .orElseThrow(() -> new NotFoundException("Locker", request.lockerId()));
            schedule.setLockerId(request.lockerId());
        } else {
            findDroneUnit(request.droneUnitId());
            schedule.setDroneUnitId(request.droneUnitId());
        }
        if (request.assignedTechnicianId() != null) {
            requireTechnician(request.assignedTechnicianId(), scheduleTechnicianRole(schedule));
        }
        schedule.setTitle(request.title());
        schedule.setIntervalDays(request.intervalDays());
        schedule.setAssignedTechnicianId(request.assignedTechnicianId());
        schedule.setPriority(StringUtils.hasText(request.priority()) ? request.priority() : "NORMAL");
        schedule.setDescription(request.description());
        schedule.setLocationNote(request.locationNote());
        schedule.setScheduledTimeSlot(request.scheduledTimeSlot());
        schedule.setChecklist(request.checklist());
        LocalDateTime dueAt = request.firstDueDate() != null
                ? request.firstDueDate()
                : LocalDateTime.now().plusDays(request.intervalDays());
        schedule.setNextDueAt(dueAt);
        schedule.setActive(true);
        MaintenanceSchedule saved = scheduleRepository.save(schedule);
        // Lịch Drone mới được xem là đang chờ nghiệm thu lần đầu, nên phải tạm dừng nhận chuyến bay.
        if (saved.getDroneUnitId() != null) {
            markDroneMaintenancePending(saved.getDroneUnitId(), null, saved.getTitle());
        }
        if (saved.getAssignedTechnicianId() != null) {
            notifyScheduleAssignment(saved);
        }
        return toSchedule(saved);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceScheduleResponse> listSchedules() {
        return listSchedules(null, null);
    }

    /// `technicianId` ⇒ chỉ lịch người đó phụ trách; `target` = LOCKER / DRONE lọc theo đối tượng lịch.
    @Transactional(readOnly = true)
    public List<MaintenanceScheduleResponse> listSchedules(Long technicianId, String target) {
        Map<Long, String> technicianNames = new HashMap<>();
        return scheduleRepository.findByActiveTrueOrderByNextDueAtAsc().stream()
                .filter(s -> technicianId == null || technicianId.equals(s.getAssignedTechnicianId()))
                .filter(s -> !"LOCKER".equalsIgnoreCase(target) || s.getLockerId() != null)
                .filter(s -> !"DRONE".equalsIgnoreCase(target) || s.getDroneUnitId() != null)
                .map(s -> toSchedule(s, technicianNames))
                .toList();
    }

    /// KTV đã kiểm tra xong lần này: dời mốc đến hạn = now + intervalDays.
    @Transactional
    public MaintenanceScheduleResponse completeSchedule(Long id) {
        return completeSchedule(id, null, null, true);
    }

    @Transactional
    public MaintenanceScheduleResponse completeSchedule(
            Long id, CompleteScheduleRequest req, Long actorUserId) {
        return completeSchedule(id, req, actorUserId, true);
    }

    @Transactional
    public MaintenanceScheduleResponse completeSchedule(
            Long id, CompleteScheduleRequest req, Long actorUserId, boolean admin, String technicianTarget) {
        return completeScheduleInternal(id, req, actorUserId, admin, technicianTarget);
    }

    /// Hoàn tất một lượt kiểm tra định kỳ + lưu biên bản. ĐẠT ⇒ hạn kế tiếp = now + chu kỳ.
    /// KHÔNG ĐẠT (lịch của tủ) ⇒ không dời hạn; tự mở phiếu gắn lịch, giao cho KTV vừa kiểm tra;
    /// phiếu đóng mới dời hạn. Chỉ KTV phụ trách lịch (hoặc ADMIN) được hoàn tất.
    @Transactional
    public MaintenanceScheduleResponse completeSchedule(
            Long id, CompleteScheduleRequest req, Long actorUserId, boolean admin) {
        return completeScheduleInternal(id, req, actorUserId, admin, null);
    }

    private MaintenanceScheduleResponse completeScheduleInternal(
            Long id, CompleteScheduleRequest req, Long actorUserId, boolean admin, String technicianTarget) {
        MaintenanceSchedule schedule =
                scheduleRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("MaintenanceSchedule", id));
        requireScheduleTarget(schedule, technicianTarget);
        if (!Boolean.TRUE.equals(schedule.getActive())) {
            throw new BusinessException("SCHEDULE_INACTIVE", "Lịch kiểm tra đã ngưng", HttpStatus.CONFLICT);
        }
        if (!admin && schedule.getAssignedTechnicianId() != null
                && !schedule.getAssignedTechnicianId().equals(actorUserId)) {
            throw new BusinessException(
                    "SCHEDULE_NOT_ASSIGNED", "Chỉ KTV phụ trách lịch mới hoàn tất được lần kiểm tra",
                    HttpStatus.FORBIDDEN);
        }
        Long pendingReportId = schedule.getPendingReportId();
        if (pendingReportId != null && reportRepository.findById(pendingReportId)
                .filter(r -> OPEN_REPORT_STATUSES.contains(r.getStatus())).isPresent()) {
            throw new BusinessException(
                    "SCHEDULE_PENDING_REPORT",
                    "Lần kiểm tra trước chưa đạt — hoàn tất phiếu RPT-" + pendingReportId + " trước",
                    HttpStatus.CONFLICT);
        }
        InspectionOutcome outcome = evaluateInspection(schedule, req);
        // Lịch drone là việc của KTV drone (drone FAULT tự mở phiếu) ⇒ vẫn dời hạn như cũ.
        boolean opensReport = outcome.failed() && schedule.getLockerId() != null;

        LocalDateTime now = LocalDateTime.now();
        schedule.setLastDoneAt(now);
        schedule.setLastResult(outcome.failed() ? "FAILED" : "PASSED");
        schedule.setPendingReportId(null);
        if (!opensReport) {
            schedule.setNextDueAt(now.plusDays(schedule.getIntervalDays()));
            schedule.setLastDueNotifiedAt(null);
        }

        MaintenanceInspectionLog inspectionLog = new MaintenanceInspectionLog();
        inspectionLog.setScheduleId(schedule.getId());
        inspectionLog.setLockerId(schedule.getLockerId());
        inspectionLog.setDroneUnitId(schedule.getDroneUnitId());

        // KTV ghi biên bản cho chính mình. Admin ghi hộ: người kiểm tra là KTV được chọn, không chọn
        // thì là KTV phụ trách lịch. Phiếu KHÔNG ĐẠT giao cho người kiểm tra đó chứ không giao cho tài
        // khoản admin; không xác định được thì định tuyến như phiếu thường.
        Long inspectorId = admin
                ? (req != null && req.technicianId() != null ? req.technicianId() : schedule.getAssignedTechnicianId())
                : actorUserId;
        Long techId = inspectorId != null ? inspectorId : actorUserId;
        inspectionLog.setTechnicianId(techId);

        String techName = admin && req != null ? req.technicianName() : null;
        if (!StringUtils.hasText(techName) && techId != null) {
            techName = resolveUserName(techId);
        }
        inspectionLog.setTechnicianName(techName);
        inspectionLog.setStatus(outcome.status());
        inspectionLog.setNote(req != null ? req.note() : null);
        if (req != null && req.photoUrls() != null && !req.photoUrls().isEmpty()) {
            inspectionLog.setPhotoUrls(String.join(",", req.photoUrls()));
        }
        inspectionLog.setChecklistResults(outcome.checklistResults());

        if (opensReport) {
            LockerReport report = openInspectionReport(schedule, req, outcome, techId, inspectorId);
            schedule.setPendingReportId(report.getId());
            inspectionLog.setCreatedReportId(report.getId());
        }
        MaintenanceSchedule saved = scheduleRepository.save(schedule);
        inspectionLogRepository.save(inspectionLog);
        if (saved.getDroneUnitId() != null) {
            if (outcome.failed()) {
                markDroneMaintenanceFailure(saved.getDroneUnitId(), req, outcome, techId, saved.getTitle());
            } else {
                synchronizeDroneOperationalStatus(saved.getDroneUnitId(), techId);
            }
        }
        return toSchedule(saved);
    }

    @Transactional(readOnly = true)
    public void requireScheduleTarget(Long id, String technicianTarget) {
        MaintenanceSchedule schedule = scheduleRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("MaintenanceSchedule", id));
        requireScheduleTarget(schedule, technicianTarget);
    }

    private void requireScheduleTarget(MaintenanceSchedule schedule, String technicianTarget) {
        if (technicianTarget == null) {
            return;
        }
        boolean allowed = "DRONE".equalsIgnoreCase(technicianTarget)
                ? schedule.getDroneUnitId() != null
                : "LOCKER".equalsIgnoreCase(technicianTarget) && schedule.getLockerId() != null;
        if (!allowed) {
            throw new BusinessException(
                    "SCHEDULE_TARGET_FORBIDDEN",
                    "Lịch bảo trì không thuộc mảng kỹ thuật viên đang đăng nhập",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static final Set<String> LEGACY_PASSED_STATUSES = Set.of("PASSED", "ATTENTION");
    private static final Set<String> LEGACY_FAILED_STATUSES = Set.of("DEFECT_DETECTED", "FAILED");
    private static final Set<String> INSPECTION_ITEM_RESULTS = Set.of("PASS", "FAIL", "NA");
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /// `status` ghi vào biên bản; `checklistResults` là JSON các mục khi client gửi `items`.
    private record InspectionOutcome(String status, boolean failed, String checklistResults, List<String> failedItems) {
    }

    /// Có `items` ⇒ đối chiếu checklist của lịch (đủ mục, không mục lạ) và tự suy kết quả: một mục
    /// FAIL là KHÔNG ĐẠT. Client cũ không gửi `items` ⇒ dùng `status` (PASSED/ATTENTION là đạt).
    private InspectionOutcome evaluateInspection(MaintenanceSchedule schedule, CompleteScheduleRequest req) {
        if (req == null || req.items() == null || req.items().isEmpty()) {
            String status = req == null || !StringUtils.hasText(req.status())
                    ? "PASSED"
                    : req.status().trim().toUpperCase(Locale.ROOT);
            if (!LEGACY_PASSED_STATUSES.contains(status) && !LEGACY_FAILED_STATUSES.contains(status)) {
                throw new BusinessException("INSPECTION_STATUS_INVALID", "Kết quả kiểm tra không hợp lệ: " + status);
            }
            return new InspectionOutcome(
                    status, LEGACY_FAILED_STATUSES.contains(status), req == null ? null : req.checklistResults(),
                    List.of());
        }
        List<String> expected = checklistItems(schedule.getChecklist());
        Map<String, InspectionItemResult> byLabel = new java.util.LinkedHashMap<>();
        for (InspectionItemResult item : req.items()) {
            String label = item.label().trim();
            String result = item.result().trim().toUpperCase(Locale.ROOT);
            if (!INSPECTION_ITEM_RESULTS.contains(result)) {
                throw new BusinessException(
                        "INSPECTION_ITEM_RESULT_INVALID", "Mục \"" + label + "\" phải là PASS, FAIL hoặc NA");
            }
            if (!expected.isEmpty() && !expected.contains(label)) {
                throw new BusinessException("CHECKLIST_ITEM_UNKNOWN", "Mục \"" + label + "\" không có trong checklist của lịch");
            }
            String note = StringUtils.hasText(item.note()) ? item.note().trim() : null;
            if (byLabel.put(label, new InspectionItemResult(label, result, note)) != null) {
                throw new BusinessException("CHECKLIST_ITEM_DUPLICATE", "Mục \"" + label + "\" bị gửi hai lần");
            }
        }
        List<String> missing = expected.stream().filter(label -> !byLabel.containsKey(label)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("CHECKLIST_INCOMPLETE", "Chưa đánh giá: " + String.join("; ", missing));
        }
        List<String> failedItems = byLabel.values().stream()
                .filter(item -> "FAIL".equals(item.result()))
                .map(InspectionItemResult::label)
                .toList();
        String json;
        try {
            json = JSON.writeValueAsString(byLabel.values());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise inspection items", ex);
        }
        boolean failed = !failedItems.isEmpty();
        return new InspectionOutcome(failed ? "FAILED" : "PASSED", failed, json, failedItems);
    }

    /// Checklist lưu dạng văn bản; tách theo dòng hoặc ';' (web cũ nối bằng "; ").
    static List<String> checklistItems(String checklist) {
        if (!StringUtils.hasText(checklist)) {
            return List.of();
        }
        return java.util.Arrays.stream(checklist.split("\\r?\\n|;"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /// Phiếu cho lần kiểm tra KHÔNG ĐẠT: có ô hỏng ⇒ đi qua luồng báo ô hỏng (ô FAULT, gộp phiếu mở
    /// sẵn); không thì phiếu cấp tủ. Giao cho `assigneeId` (KTV vừa kiểm tra); null ⇒ định tuyến.
    private LockerReport openInspectionReport(
            MaintenanceSchedule schedule, CompleteScheduleRequest req, InspectionOutcome outcome,
            Long reporterId, Long assigneeId) {
        String description = req != null && StringUtils.hasText(req.faultReason())
                ? req.faultReason().trim()
                : !outcome.failedItems().isEmpty()
                        ? "Mục không đạt: " + String.join("; ", outcome.failedItems())
                        : req != null && StringUtils.hasText(req.note())
                                ? req.note().trim()
                                : "Phát hiện lỗi trong ca kiểm tra định kỳ";
        String title = "Kiểm tra định kỳ không đạt: " + schedule.getTitle();
        LockerReport report;
        if (req != null && req.faultBoxId() != null) {
            LockerBox box = findBox(req.faultBoxId());
            if (!schedule.getLockerId().equals(box.getLockerId())) {
                throw new BusinessException("FAULT_BOX_NOT_IN_LOCKER", "Ô báo hỏng không thuộc tủ của lịch kiểm tra");
            }
            report = reportBoxFault(box, description, reporterId, List.of(), assigneeId, title);
        } else {
            LockerUnit locker = lockerRepository.findById(schedule.getLockerId()).orElse(null);
            LockerReport created = new LockerReport();
            created.setLockerId(schedule.getLockerId());
            created.setCategory(ReportCategory.LOCKER);
            created.setUserId(reporterId == null ? 0L : reporterId);
            created.setTitle(title);
            created.setDescription(description);
            applyRouting(created, locker, assigneeId);
            report = reportRepository.save(created);
            notifyRouted(report, locker);
        }
        if (report.getScheduleId() == null) {
            report.setScheduleId(schedule.getId());
            report = reportRepository.save(report);
        }
        return report;
    }

    /// Nhắc KTV các lịch sắp/đã tới hạn — mỗi kỳ hạn một lần; lịch đang chờ phiếu KHÔNG ĐẠT thì bỏ
    /// qua (KTV đã có phiếu). Lịch chưa có người phụ trách ⇒ nhắc mọi KTV của mảng đó.
    @Transactional
    public int remindDueSchedules() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusHours(rules.scheduleReminderLeadHours());
        int reminded = 0;
        Map<String, List<Long>> techniciansByRole = new HashMap<>();
        for (MaintenanceSchedule schedule :
                scheduleRepository.findByActiveTrueAndLastDueNotifiedAtIsNullAndNextDueAtBefore(cutoff)) {
            if (schedule.getPendingReportId() != null) {
                continue;
            }
            if (scheduleRepository.markDueNotified(schedule.getId(), now) == 0) {
                continue;
            }
            String target = schedule.getLockerId() != null
                    ? "tủ " + lockerLabel(schedule.getLockerId())
                    : "drone " + droneUnitRepository.findById(schedule.getDroneUnitId())
                            .map(DroneUnit::getCode).orElse("#" + schedule.getDroneUnitId());
            String when = schedule.getNextDueAt().isAfter(now) ? "sắp tới hạn" : "đã tới hạn";
            String message = "Lịch \"" + schedule.getTitle() + "\" tại " + target + " " + when + " kiểm tra.";
            List<Long> recipients = schedule.getAssignedTechnicianId() != null
                    ? List.of(schedule.getAssignedTechnicianId())
                    : techniciansByRole.computeIfAbsent(scheduleTechnicianRole(schedule), this::technicianIds);
            String eventType = schedule.getLockerId() != null ? DomainEventNames.LOCKER_SCHEDULE_DUE : DRONE_SCHEDULE_DUE;
            for (Long technicianId : recipients) {
                publishStaffNotification(eventType, technicianId, schedule.getId(), "MAINTENANCE_SCHEDULE", message);
            }
            reminded++;
        }
        return reminded;
    }

    private static String scheduleTechnicianRole(MaintenanceSchedule schedule) {
        return schedule.getLockerId() != null ? LOCKER_TECHNICIAN : DRONE_TECHNICIAN;
    }

    /// Lấy danh sách lịch sử kiểm tra định kỳ
    @Transactional(readOnly = true)
    public List<MaintenanceInspectionLogResponse> listInspectionLogs(
            Long scheduleId, Long lockerId, Long technicianId) {
        List<MaintenanceInspectionLog> logs;
        if (scheduleId != null) {
            logs = inspectionLogRepository.findByScheduleIdOrderByCreatedAtDesc(scheduleId);
        } else if (lockerId != null && technicianId != null) {
            logs = inspectionLogRepository.findByLockerIdAndTechnicianIdOrderByCreatedAtDesc(lockerId, technicianId);
        } else if (lockerId != null) {
            logs = inspectionLogRepository.findByLockerIdOrderByCreatedAtDesc(lockerId);
        } else if (technicianId != null) {
            logs = inspectionLogRepository.findByTechnicianIdOrderByCreatedAtDesc(technicianId);
        } else {
            logs = inspectionLogRepository.findAllByOrderByCreatedAtDesc();
        }
        return logs.stream().map(this::toInspectionLogResponse).toList();
    }

    /// Lịch sử tổng hợp của đúng một Drone: biên bản kiểm tra định kỳ và phiếu sự cố đã đóng.
    @Transactional(readOnly = true)
    public DroneMaintenanceHistoryResponse droneMaintenanceHistory(Long droneUnitId) {
        DroneUnit drone = findDroneUnit(droneUnitId);
        List<MaintenanceInspectionLogResponse> completedMaintenance =
                inspectionLogRepository.findByDroneUnitIdOrderByCreatedAtDesc(droneUnitId).stream()
                        .map(this::toInspectionLogResponse)
                        .toList();
        List<LockerReportResponse> resolvedIncidents = toReports(
                reportRepository.findByDroneUnitIdAndStatusInOrderByCreatedAtDesc(
                        droneUnitId, List.of("RESOLVED")));
        return new DroneMaintenanceHistoryResponse(
                drone.getId(), drone.getCode(), completedMaintenance, resolvedIncidents);
    }

    private MaintenanceInspectionLogResponse toInspectionLogResponse(MaintenanceInspectionLog log) {
        LockerUnit locker = log.getLockerId() == null ? null : lockerRepository.findById(log.getLockerId()).orElse(null);
        DroneUnit drone = log.getDroneUnitId() == null ? null : droneUnitRepository.findById(log.getDroneUnitId()).orElse(null);
        List<String> photos = StringUtils.hasText(log.getPhotoUrls())
                ? java.util.Arrays.stream(log.getPhotoUrls().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()
                : List.of();
        return new MaintenanceInspectionLogResponse(
                log.getId(),
                log.getScheduleId(),
                log.getLockerId(),

                locker == null ? null : locker.getName(),
                locker == null ? null : locker.getCode(),
                log.getDroneUnitId(),
                drone == null ? null : drone.getCode(),
                log.getTechnicianId(),
                log.getTechnicianName(),
                log.getStatus(),
                log.getNote(),
                photos,
                log.getChecklistResults(),
                log.getCreatedReportId(),
                log.getCreatedAt());
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        try {
            var resp = userClient.getUser(userId);
            if (resp != null && resp.data() != null && StringUtils.hasText(resp.data().fullName())) {
                return resp.data().fullName();
            }
        } catch (Exception ignored) {}
        return "KTV #" + userId;
    }

    /// Phân công hoặc thay đổi KTV phụ trách lịch kiểm tra định kỳ
    @Transactional
    public MaintenanceScheduleResponse assignTechnician(Long id, Long technicianId) {
        MaintenanceSchedule schedule = scheduleRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("MaintenanceSchedule", id));
        if (technicianId != null) {
            requireTechnician(technicianId, scheduleTechnicianRole(schedule));
        }
        schedule.setAssignedTechnicianId(technicianId);
        MaintenanceSchedule saved = scheduleRepository.save(schedule);
        if (technicianId != null) {
            notifyScheduleAssignment(saved);
        }
        return toSchedule(saved);
    }

    /// Cập nhật thông tin lịch kiểm tra định kỳ (chu kỳ, ưu tiên, checklist, mô tả, KTV)
    @Transactional
    public MaintenanceScheduleResponse updateSchedule(Long id, MaintenanceScheduleRequest request) {
        MaintenanceSchedule schedule = scheduleRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("MaintenanceSchedule", id));
        if (request.title() != null) {
            schedule.setTitle(request.title());
        }
        if (request.intervalDays() != null && request.intervalDays() > 0) {
            schedule.setIntervalDays(request.intervalDays());
        }
        if (request.assignedTechnicianId() != null
                && !request.assignedTechnicianId().equals(schedule.getAssignedTechnicianId())) {
            requireTechnician(request.assignedTechnicianId(), scheduleTechnicianRole(schedule));
            schedule.setAssignedTechnicianId(request.assignedTechnicianId());
            notifyScheduleAssignment(schedule);
        }
        if (StringUtils.hasText(request.priority())) {
            schedule.setPriority(request.priority());
        }
        if (request.description() != null) {
            schedule.setDescription(request.description());
        }
        if (request.locationNote() != null) {
            schedule.setLocationNote(request.locationNote());
        }
        if (request.scheduledTimeSlot() != null) {
            schedule.setScheduledTimeSlot(request.scheduledTimeSlot());
        }
        if (request.checklist() != null) {
            schedule.setChecklist(request.checklist());
        }
        if (request.firstDueDate() != null) {
            schedule.setNextDueAt(request.firstDueDate());
            schedule.setLastDueNotifiedAt(null);
        }
        return toSchedule(scheduleRepository.save(schedule));
    }

    /// Xóa mềm (active=false) để giữ lịch sử.
    @Transactional
    public void deleteSchedule(Long id) {
        MaintenanceSchedule schedule =
                scheduleRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("MaintenanceSchedule", id));
        schedule.setActive(false);
        scheduleRepository.save(schedule);
        if (schedule.getDroneUnitId() != null) {
            synchronizeDroneOperationalStatus(schedule.getDroneUnitId(), null);
        }
    }

    private MaintenanceScheduleResponse toSchedule(MaintenanceSchedule s) {
        return toSchedule(s, new HashMap<>());
    }

    private MaintenanceScheduleResponse toSchedule(MaintenanceSchedule s, Map<Long, String> technicianNames) {
        LockerUnit locker =
                s.getLockerId() == null ? null : lockerRepository.findById(s.getLockerId()).orElse(null);
        DroneUnit drone =
                s.getDroneUnitId() == null
                        ? null
                        : droneUnitRepository.findById(s.getDroneUnitId()).orElse(null);
        // Drone luôn đóng tại một tủ/bãi đáp cố định. Trả kèm thông tin tủ liên kết
        // để màn hình lịch Drone hiển thị vị trí thực tế, nhưng vẫn giữ droneUnitId
        // để client phân biệt với lịch bảo trì Kiosk.
        if (locker == null && drone != null && drone.getLockerId() != null) {
            locker = lockerRepository.findById(drone.getLockerId()).orElse(null);
        }
        boolean due =
                Boolean.TRUE.equals(s.getActive())
                        && s.getNextDueAt() != null
                        && !LocalDateTime.now().isBefore(s.getNextDueAt());
        String techName = s.getAssignedTechnicianId() == null
                ? null
                : technicianNames.computeIfAbsent(s.getAssignedTechnicianId(), this::resolveUserName);
        return new MaintenanceScheduleResponse(
                s.getId(),
                locker == null ? s.getLockerId() : locker.getId(),
                locker == null ? null : locker.getName(),
                locker == null ? null : locker.getCode(),
                s.getDroneUnitId(),
                drone == null ? null : drone.getCode(),
                s.getTitle(),
                s.getIntervalDays(),
                s.getLastDoneAt(),
                s.getNextDueAt(),
                s.getActive(),
                due,
                s.getAssignedTechnicianId(),
                techName,
                s.getPriority() != null ? s.getPriority() : "NORMAL",
                s.getDescription(),
                s.getChecklist(),
                locker == null ? null : locker.getStoreId(),
                locker == null ? null : locker.getAddress(),
                s.getLocationNote(),
                s.getScheduledTimeSlot(),
                checklistItems(s.getChecklist()),
                s.getLastResult(),
                s.getPendingReportId(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }

    // ---- Drone fleet (thiết bị bay vật lý, khác ô tủ cellType=DRONE) ----

    /** Đồng bộ trạng thái vận hành từ các công việc kỹ thuật đang mở của đúng Drone này. */
    private void synchronizeDroneOperationalStatus(Long droneUnitId, Long actorUserId) {
        DroneUnit unit = findDroneUnit(droneUnitId);
        LockerReport openReport = reportRepository
                .findFirstByDroneUnitIdAndStatusInOrderByCreatedAtDesc(droneUnitId, OPEN_REPORT_STATUSES)
                .orElse(null);
        String targetStatus;
        String reason = null;
        if (openReport != null) {
            targetStatus = DroneStatus.FAULT;
            reason = normalizeText(openReport.getDescription());
        } else if (scheduleRepository.existsByDroneUnitIdAndActiveTrueAndLastDoneAtIsNull(droneUnitId)) {
            targetStatus = DroneStatus.MAINTENANCE;
        } else {
            targetStatus = DroneStatus.IDLE;
        }
        if (targetStatus.equals(unit.getStatus())
                && java.util.Objects.equals(reason, unit.getFaultReason())) {
            return;
        }
        String previous = unit.getStatus();
        unit.setStatus(targetStatus);
        unit.setFaultReason(reason);
        DroneUnit saved = droneUnitRepository.save(unit);
        appendDroneLog(saved.getId(), "Đồng bộ trạng thái " + previous + " → " + targetStatus, actorUserId);
    }

    private void markDroneMaintenancePending(Long droneUnitId, Long actorUserId, String scheduleTitle) {
        DroneUnit unit = findDroneUnit(droneUnitId);
        requireDroneWithoutActiveMission(unit, "schedule maintenance");
        synchronizeDroneOperationalStatus(droneUnitId, actorUserId);
        appendDroneLog(unit.getId(), "Chờ nghiệm thu bảo trì: " + scheduleTitle, actorUserId);
    }

    private void markDroneMaintenanceFailure(
            Long droneUnitId, CompleteScheduleRequest request, InspectionOutcome outcome,
            Long actorUserId, String scheduleTitle) {
        DroneUnit unit = findDroneUnit(droneUnitId);
        String reason = request != null && StringUtils.hasText(request.faultReason())
                ? request.faultReason().trim()
                : !outcome.failedItems().isEmpty()
                        ? "Mục không đạt: " + String.join("; ", outcome.failedItems())
                        : "Không đạt bảo trì: " + scheduleTitle;
        String previous = unit.getStatus();
        unit.setStatus(DroneStatus.FAULT);
        unit.setFaultReason(reason);
        DroneUnit saved = droneUnitRepository.save(unit);
        appendDroneLog(saved.getId(), "Bảo trì không đạt, chuyển " + previous + " → FAULT: " + reason, actorUserId);
        openDroneFaultReport(saved, reason, actorUserId);
    }

    @Transactional
    public DroneUnitResponse createDroneUnit(DroneUnitRequest request) {
        requireLandingPad(lockerRepository
                .findById(request.lockerId())
                .orElseThrow(() -> new NotFoundException("Locker", request.lockerId())));
        if (droneUnitRepository.existsByCodeAndActiveTrue(request.code())) {
            throw new BusinessException("DRONE_CODE_DUPLICATE", "Drone code already exists: " + request.code());
        }
        DroneUnit unit = new DroneUnit();
        unit.setLockerId(request.lockerId());
        unit.setCode(request.code());
        return toDroneUnit(droneUnitRepository.save(unit));
    }

    /// Drone chỉ đóng trạm ở tủ có bãi đáp (cùng mã lỗi với cập nhật trạng thái bãi đáp).
    private static void requireLandingPad(LockerUnit locker) {
        if (!Boolean.TRUE.equals(locker.getLandingPad())) {
            throw new BusinessException(
                    "LANDING_PAD_ABSENT", "Tủ " + locker.getCode() + " không có bãi đáp drone — không gán drone vào tủ này được");
        }
    }

    @Transactional(readOnly = true)
    public List<DroneUnitResponse> listDroneUnits() {
        return droneUnitRepository.findAllByOrderByLockerIdAscCodeAsc().stream()
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .map(this::toDroneUnit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DroneUnitResponse> listDroneUnits(String status, Long lockerId) {
        return droneUnitRepository.findAllByOrderByLockerIdAscCodeAsc().stream()
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .filter(d -> !StringUtils.hasText(status) || status.equalsIgnoreCase(d.getStatus()))
                .filter(d -> lockerId == null || lockerId.equals(d.getLockerId()))
                .map(this::toDroneUnit)
                .toList();
    }

    @Transactional(readOnly = true)
    public DroneUnitResponse getDroneUnit(Long id) {
        return toDroneUnit(findDroneUnit(id));
    }

    @Transactional
    public DroneUnitResponse claimDrone(Long id, Long userId) {
        DroneUnit unit = findDroneUnit(id);
        if (unit.getAssignedTechnicianId() != null && !unit.getAssignedTechnicianId().equals(userId)) {
            throw new BusinessException("DRONE_ALREADY_ASSIGNED", "Drone is already assigned to another technician");
        }
        unit.setAssignedTechnicianId(userId);
        return toDroneUnit(droneUnitRepository.save(unit));
    }

    /** Admin giao Drone cho đúng KTV Drone hoặc bỏ phân công để hàng đợi chung xử lý. */
    @Transactional
    public DroneUnitResponse assignDroneTechnician(Long id, Long technicianId, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        if (technicianId != null) {
            requireTechnician(technicianId, DRONE_TECHNICIAN);
        }
        Long previousTechnicianId = unit.getAssignedTechnicianId();
        unit.setAssignedTechnicianId(technicianId);
        DroneUnit saved = droneUnitRepository.save(unit);
        appendDroneLog(
                saved.getId(),
                "Đổi KTV phụ trách: " + technicianLabel(previousTechnicianId) + " → " + technicianLabel(technicianId),
                actorUserId);
        // Chỉ đổi cấu hình phụ trách hiện tại của Drone. Phiếu/lịch cũ giữ nguyên người đã
        // được giao hoặc được định tuyến tại thời điểm chúng được tạo.
        return toDroneUnit(saved);
    }

    @Transactional
    public DroneUnitResponse updateDroneStatus(Long id, String status, String reason, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        requireDroneOwnership(unit, actorUserId);
        validateManualDroneStatusChange(unit, status);
        return updateDroneStatusInternal(unit, status, reason, actorUserId, false);
    }

    @Transactional
    public DroneUnitResponse updateDroneStatusAsAdmin(Long id, String status, String reason, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        validateManualDroneStatusChange(unit, status);
        return updateDroneStatusInternal(unit, status, reason, actorUserId, false);
    }

    /// Admin chủ động mở một phiếu mới cho drone. Mỗi lần gửi là một sự cố độc lập,
    /// kể cả khi drone đã có phiếu OPEN/IN_PROGRESS trước đó.
    @Transactional
    public LockerReportResponse createDroneIncidentReport(
            Long droneId, CreateDroneIncidentReportRequest request, Long actorUserId) {
        DroneUnit unit = findDroneUnit(droneId);
        String title = normalizeText(request.title());
        String description = normalizeText(request.description());

        String previousStatus = unit.getStatus();
        unit.setStatus(DroneStatus.FAULT);
        unit.setFaultReason(description);
        DroneUnit savedUnit = droneUnitRepository.save(unit);
        if (!DroneStatus.FAULT.equals(previousStatus)) {
            appendDroneLog(savedUnit.getId(), "Admin mở phiếu sự cố: " + title, actorUserId);
        }

        LockerReport report = new LockerReport();
        report.setLockerId(savedUnit.getLockerId());
        report.setDroneUnitId(savedUnit.getId());
        report.setCategory(ReportCategory.DRONE);
        report.setUserId(actorUserId == null ? 0L : actorUserId);
        report.setTitle(title);
        report.setDescription(description);
        // Snapshot tuyến xử lý tại thời điểm tạo. Đổi KTV phụ trách Drone về sau không được
        // phép kéo các phiếu cũ sang KTV mới.
        report.setRoutedToUserId(savedUnit.getAssignedTechnicianId());
        LockerReport savedReport = reportRepository.save(report);
        attachmentService.attach(
                savedReport,
                AttachmentStage.REPORT,
                request.attachments(),
                actorUserId,
                null,
                rules.reportPhotosPerRequestReporter());
        notifyDroneRouted(savedReport, actorUserId);
        return toReport(savedReport);
    }

    /**
     * Idempotently creates the physical-drone inspection ticket and selects the
     * responsible kiosk technician for parcel recovery. The recovery workflow is
     * owned by order-service because it is tied to parcel custody and resolution.
     */
    @Transactional
    public IncidentTicketBundleResponse createIncidentTickets(CreateIncidentTicketsRequest request) {
        Optional<LockerReport> existing = reportRepository.findByExternalIncidentIdAndTicketType(
                request.incidentId(), "DRONE_INSPECTION");
        if (existing.isPresent()) {
            LockerReport report = existing.get();
            RecoveryAssignment recovery = selectRecoveryTechnician(request.latitude(), request.longitude());
            return new IncidentTicketBundleResponse(
                    report.getId(), report.getAssignedToUserId(), recovery.technicianId(), recovery.lockerId());
        }

        DroneUnit drone = findDroneUnit(request.droneUnitId());
        if (!drone.getCode().equalsIgnoreCase(request.droneCode())) {
            throw new BusinessException("INCIDENT_DRONE_MISMATCH", "Incident drone does not match the fleet record");
        }

        LockerReport report = new LockerReport();
        report.setLockerId(drone.getLockerId());
        report.setDroneUnitId(drone.getId());
        report.setCategory(ReportCategory.DRONE);
        report.setUserId(request.reporterUserId());
        report.setOrderId(request.orderId());
        report.setOrderCode(request.orderCode());
        report.setExternalIncidentId(request.incidentId());
        report.setIncidentCode(request.incidentCode());
        report.setTicketType("DRONE_INSPECTION");
        report.setTitle("Kiểm tra an toàn sau sự cố " + request.incidentCode());
        report.setDescription(buildIncidentInspectionDescription(request));
        // Chỉ tự giao cho KTV phụ trách drone khi người đó đang trong ca — giao cho người không trực thì phiếu nằm
        // IN_PROGRESS mà không ai làm (test 2026-10-29, RPT-44 giao cho tài khoản demo). Không trực ⇒ phiếu mở,
        // báo cả đội drone nhận.
        Long inspectionTechnician = activeTechnician(drone.getAssignedTechnicianId(), DRONE_TECHNICIAN);
        if (inspectionTechnician != null && !onDuty(inspectionTechnician)) {
            inspectionTechnician = null;
        }
        report.setRoutedToUserId(drone.getAssignedTechnicianId());
        if (inspectionTechnician != null) {
            report.setAssignedToUserId(inspectionTechnician);
            report.setAssignedAt(LocalDateTime.now());
            report.setStatus("IN_PROGRESS");
        } else {
            report.setStatus("OPEN");
        }
        LockerReport saved = reportRepository.save(report);
        if (inspectionTechnician != null) {
            publishStaffNotification(
                    DRONE_REPORT_ASSIGNED,
                    inspectionTechnician,
                    saved.getId(),
                    DRONE_REPORT_REFERENCE,
                    "Bạn được giao kiểm tra " + drone.getCode() + " sau sự cố " + request.incidentCode() + ".");
        } else {
            // Drone chưa có KTV phụ trách (hoặc KTV không còn hoạt động) ⇒ báo cả đội drone nhận phiếu.
            notifyDroneRouted(saved, null);
        }
        RecoveryAssignment recovery = selectRecoveryTechnician(request.latitude(), request.longitude());
        return new IncidentTicketBundleResponse(
                saved.getId(), inspectionTechnician, recovery.technicianId(), recovery.lockerId());
    }

    private String buildIncidentInspectionDescription(CreateIncidentTicketsRequest request) {
        StringBuilder value = new StringBuilder()
                .append("Kiểm tra hook/gripper, servo/actuator, dây điện và cơ cấu giữ hàng. ")
                .append("Incident ").append(request.incidentCode())
                .append(", đơn ").append(request.orderCode()).append(". ")
                .append(request.reason() == null ? "" : request.reason());
        if (request.latitude() != null && request.longitude() != null) {
            value.append(" Vị trí báo rơi: ").append(request.latitude()).append(", ").append(request.longitude());
        }
        if (StringUtils.hasText(request.cameraSnapshotUrl())) {
            value.append(". Ảnh camera được lưu trong hồ sơ incident.");
        }
        return value.length() <= 2000 ? value.toString() : value.substring(0, 2000);
    }

    private RecoveryAssignment selectRecoveryTechnician(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return new RecoveryAssignment(null, null);
        }

        // Lấy danh sách ID KTV drone đang bận đơn/chuyến bay vận hành từ order-service
        Set<Long> busyDroneTechIds = Set.of();
        try {
            var busyRes = orderClient.getBusyDroneTechnicianIds();
            if (busyRes != null && busyRes.data() != null) {
                busyDroneTechIds = busyRes.data();
            }
        } catch (Exception ex) {
            log.warn("Could not query busy drone technician IDs from order-service: {}", ex.getMessage());
        }

        final Set<Long> finalBusyDroneTechIds = busyDroneTechIds;

        // 1. Quét KTV đang trong ca trực (on-duty) có toạ độ GPS gần vị trí rơi nhất:
        //    Bao gồm cả LOCKER_TECHNICIAN và DRONE_TECHNICIAN (với KTV drone thì không được có đơn đang vận hành)
        var liveCandidate = presenceRepository.findByOnDutyTrue().stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .filter(p -> p.getLastLocatedAt() != null
                        && p.getLastLocatedAt().isAfter(LocalDateTime.now().minusMinutes(rules.technicianLocationMaxAgeMinutes())))
                .filter(p -> {
                    String type = p.getTechnicianType() != null ? p.getTechnicianType().toUpperCase() : "";
                    if (DRONE_TECHNICIAN.equals(type)) {
                        return activeTechnician(p.getUserId(), DRONE_TECHNICIAN) != null
                                && !finalBusyDroneTechIds.contains(p.getUserId())
                                && !slaRestricted(p.getUserId());
                    } else {
                        return activeTechnician(p.getUserId(), LOCKER_TECHNICIAN) != null
                                && !slaRestricted(p.getUserId());
                    }
                })
                .sorted(Comparator
                        .comparingDouble((TechnicianPresence p) -> distanceMeters(latitude, longitude, p.getLatitude(), p.getLongitude()))
                        .thenComparingLong(p -> activeWorkload(p.getUserId())))
                .findFirst();

        if (liveCandidate.isPresent()) {
            Long techId = liveCandidate.get().getUserId();
            Long responsibilityLockerId = lockerRepository.findAll().stream()
                    .filter(l -> techId.equals(l.getAssignedTechnicianId()))
                    .map(LockerUnit::getId)
                    .findFirst()
                    .orElse(null);
            return new RecoveryAssignment(techId, responsibilityLockerId);
        }

        // 2. Fallback: tìm KTV phụ trách tủ gần điểm rơi nhất
        return lockerRepository.findAll().stream()
                .filter(locker -> "ACTIVE".equalsIgnoreCase(locker.getStatus()))
                .filter(locker -> locker.getLatitude() != null && locker.getLongitude() != null)
                .map(locker -> new RecoveryCandidate(
                        locker,
                        activeTechnician(locker.getAssignedTechnicianId(), LOCKER_TECHNICIAN),
                        distanceMeters(latitude, longitude, locker.getLatitude(), locker.getLongitude())))
                .filter(candidate -> candidate.technicianId() != null && !slaRestricted(candidate.technicianId()))
                .sorted(Comparator
                        .comparingDouble(RecoveryCandidate::distanceMeters)
                        .thenComparingLong(candidate -> activeWorkload(candidate.technicianId())))
                .map(candidate -> new RecoveryAssignment(candidate.technicianId(), candidate.locker().getId()))
                .findFirst()
                .orElse(new RecoveryAssignment(null, null));
    }

    /// KTV đang ở bậc "Hạn chế nhận việc" (đủ số phiếu trễ hạn SLA theo ngưỡng admin) thì không được phân công
    /// việc mới — cùng cách đếm với trang hiệu suất KTV.
    private boolean slaRestricted(Long technicianId) {
        int slaHours = rules.slaHours();
        LocalDateTime now = LocalDateTime.now();
        long overdue = reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(technicianId).stream()
                .filter(r -> !isClosedReport(r.getStatus()))
                .filter(r -> {
                    LocalDateTime due = r.getSlaDueAt();
                    if (due == null && r.getCreatedAt() != null) {
                        due = r.getCreatedAt().plusHours(slaHours);
                    }
                    return due != null && now.isAfter(due);
                })
                .count();
        return overdue >= rules.penaltyThresholds().restricted();
    }

    private Long activeTechnician(Long technicianId, String requiredRole) {
        if (technicianId == null) {
            return null;
        }
        UserSummary user = lookupUserQuietly(technicianId);
        return user != null
                        && "ACTIVE".equalsIgnoreCase(user.status())
                        && user.roles() != null
                        && user.roles().contains(requiredRole)
                ? technicianId
                : null;
    }

    private boolean onDuty(Long technicianId) {
        return presenceRepository.findByUserId(technicianId)
                .map(presence -> Boolean.TRUE.equals(presence.getOnDuty()))
                .orElse(false);
    }

    private long activeWorkload(Long technicianId) {
        return reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(technicianId).stream()
                .filter(report -> OPEN_REPORT_STATUSES.contains(report.getStatus()))
                .count();
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double lat = Math.toRadians(lat2 - lat1);
        double lon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record RecoveryCandidate(LockerUnit locker, Long technicianId, double distanceMeters) {
    }

    private record RecoveryAssignment(Long technicianId, Long lockerId) {
    }

    /// Đổi trạng thái tay (KTV drone / admin):
    /// - RESERVED/IN_FLIGHT do luồng điều phối quản lý;
    /// - drone đang bay gặp sự cố phải báo qua luồng FAIL của đơn drone (giữ đơn/kiện hàng đúng trạng thái);
    /// - drone còn phiếu sự cố/kiểm tra mở không được về IDLE/CHARGING — hoàn tất phiếu sẽ tự đưa drone
    ///   về hoạt động.
    private void validateManualDroneStatusChange(DroneUnit unit, String status) {
        if (DroneStatus.RESERVED.equals(status) || DroneStatus.IN_FLIGHT.equals(status)) {
            throw new BusinessException(
                    "DRONE_STATUS_WORKFLOW_MANAGED",
                    status + " is managed by the dispatch workflow");
        }
        if (DroneStatus.FAULT.equals(status) && DroneStatus.IN_FLIGHT.equals(unit.getStatus())) {
            throw new BusinessException(
                    "DRONE_IN_FLIGHT_USE_FAIL",
                    "Drone đang bay — báo sự cố qua thao tác FAIL của đơn giao bằng drone thay vì đổi trạng thái",
                    HttpStatus.CONFLICT);
        }
        if ((DroneStatus.RESERVED.equals(unit.getStatus()) || DroneStatus.IN_FLIGHT.equals(unit.getStatus()))
                && !DroneStatus.FAULT.equals(status)) {
            throw new BusinessException(
                    "DRONE_ACTIVE_MISSION",
                    DroneStatus.MAINTENANCE.equals(status)
                            ? "Drone đang thực hiện đơn giao hàng. Vui lòng hoàn tất đơn hiện tại trước khi chuyển sang bảo trì"
                            : "Không thể đổi trạng thái khi Drone đang thực hiện đơn giao hàng");
        }
        if (DRONE_OPERATIONAL_STATUSES.contains(status)) {
            reportRepository
                    .findFirstByDroneUnitIdAndStatusInOrderByCreatedAtDesc(unit.getId(), OPEN_REPORT_STATUSES)
                    .ifPresent(report -> {
                        throw new BusinessException(
                                "DRONE_HAS_OPEN_REPORT",
                                "Drone còn phiếu RPT-" + report.getId()
                                        + " chưa hoàn tất — hoàn tất phiếu để đưa drone về hoạt động",
                                HttpStatus.CONFLICT);
                    });
        }
    }

    @Transactional
    public DroneUnitResponse updateDroneStatusInternal(Long id, String status, String reason) {
        return updateDroneStatusInternal(id, status, reason, null, false);
    }

    @Transactional
    public DroneUnitResponse transitionDroneStatusInternal(
            Long id, String expectedStatus, String status, String reason) {
        return transitionDroneStatusInternal(id, expectedStatus, status, reason, true);
    }

    @Transactional
    public DroneUnitResponse transitionDroneStatusInternal(
            Long id, String expectedStatus, String status, String reason, boolean createFaultReport) {
        DroneUnit unit = droneUnitRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("DroneUnit", id));
        if (!expectedStatus.equals(unit.getStatus())) {
            throw new BusinessException(
                    "DRONE_STATUS_CONFLICT",
                    "Drone status changed; expected " + expectedStatus + " but was " + unit.getStatus());
        }
        return updateDroneStatusInternal(unit, status, reason, null, false, createFaultReport);
    }

    private DroneUnitResponse updateDroneStatusInternal(
            Long id, String status, String reason, Long actorUserId, boolean requireOwnership) {
        return updateDroneStatusInternal(findDroneUnit(id), status, reason, actorUserId, requireOwnership);
    }

    private DroneUnitResponse updateDroneStatusInternal(
            DroneUnit unit, String status, String reason, Long actorUserId, boolean requireOwnership) {
        return updateDroneStatusInternal(unit, status, reason, actorUserId, requireOwnership, true);
    }

    private DroneUnitResponse updateDroneStatusInternal(
            DroneUnit unit, String status, String reason, Long actorUserId,
            boolean requireOwnership, boolean createFaultReport) {
        if (!DroneStatus.ALL.contains(status)) {
            throw new BusinessException("DRONE_STATUS_INVALID", "Unknown drone status: " + status);
        }
        boolean fault = DroneStatus.FAULT.equals(status);
        String normalizedReason = normalizeText(reason);
        if (fault && !StringUtils.hasText(normalizedReason)) {
            throw new BusinessException("DRONE_FAULT_REASON_REQUIRED", "A reason is required to mark a drone as FAULT");
        }
        if (requireOwnership) {
            requireDroneOwnership(unit, actorUserId);
        }
        if (DroneStatus.IN_FLIGHT.equals(status) && !Boolean.TRUE.equals(unit.getActive())) {
            throw new BusinessException("DRONE_INACTIVE", "Inactive drone cannot take off");
        }
        // #7 An toan: khong cho cat canh khi pin qua thap.
        if (DroneStatus.IN_FLIGHT.equals(status)
                && unit.getBatteryPercent() != null
                && unit.getBatteryPercent() <= rules.droneLowBatteryPercent()) {
            throw new BusinessException(
                    "DRONE_BATTERY_TOO_LOW",
                    "Battery is too low to fly (" + unit.getBatteryPercent() + "%), needs charging first");
        }
        String previousStatus = unit.getStatus();
        unit.setStatus(status);
        unit.setFaultReason(fault ? normalizedReason : null);
        // #7 Roi trang thai CHARGING => ghi nhan mốc sac xong gan nhat.
        if (DroneStatus.CHARGING.equals(previousStatus) && !DroneStatus.CHARGING.equals(status)) {
            unit.setLastChargedAt(LocalDateTime.now());
        }
        DroneUnit saved = droneUnitRepository.save(unit);
        String note = "Chuyển trạng thái %s → %s%s"
                .formatted(previousStatus, status, fault && StringUtils.hasText(normalizedReason) ? ": " + normalizedReason : "");
        appendDroneLog(saved.getId(), note, actorUserId);
        // #2 FAULT mở phiếu cho đội drone. Phiếu chỉ đóng qua luồng hoàn tất phiếu (nhật ký, ảnh nghiệm
        // thu); đóng phiếu cuối cùng tự đưa drone về hoạt động (restoreAssetsAfterClose).
        if (fault && !DroneStatus.FAULT.equals(previousStatus) && createFaultReport) {
            openDroneFaultReport(saved, normalizedReason, actorUserId);
        }
        return toDroneUnit(saved);
    }

    /// #5 KTV nha quyen phu trach mot drone (de ban giao ca).
    @Transactional
    public DroneUnitResponse releaseDrone(Long id, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        requireDroneOwnership(unit, actorUserId);
        unit.setAssignedTechnicianId(null);
        DroneUnit saved = droneUnitRepository.save(unit);
        appendDroneLog(saved.getId(), "Nhả quyền phụ trách drone", actorUserId);
        return toDroneUnit(saved);
    }

    /// #4 Admin chinh sua drone: doi tu goc va/hoac doi ma.
    @Transactional
    public DroneUnitResponse updateDroneUnit(Long id, DroneUpdateRequest request) {
        return updateDroneUnit(id, request, null);
    }

    @Transactional
    public DroneUnitResponse updateDroneUnit(Long id, DroneUpdateRequest request, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        requireDroneWithoutActiveMission(unit, "edit");
        Long previousLockerId = unit.getLockerId();
        if (request.lockerId() != null && !request.lockerId().equals(unit.getLockerId())) {
            requireLandingPad(lockerRepository
                    .findById(request.lockerId())
                    .orElseThrow(() -> new NotFoundException("Locker", request.lockerId())));
            unit.setLockerId(request.lockerId());
        }
        if (StringUtils.hasText(request.code()) && !request.code().equals(unit.getCode())) {
            if (droneUnitRepository.existsByCodeAndActiveTrue(request.code())) {
                throw new BusinessException("DRONE_CODE_DUPLICATE", "Drone code already exists: " + request.code());
            }
            unit.setCode(request.code());
        }
        DroneUnit saved = droneUnitRepository.save(unit);
        if (!Objects.equals(previousLockerId, saved.getLockerId())) {
            appendDroneLog(
                    saved.getId(),
                    "Đổi trạm hoạt động: " + lockerLabel(previousLockerId) + " → " + lockerLabel(saved.getLockerId()),
                    actorUserId);
        }
        // Không cập nhật các bảng report/order/mission/schedule: các bản ghi này là lịch sử
        // và phải tiếp tục trỏ tới trạm đã được chốt tại thời điểm phát sinh.
        return toDroneUnit(saved);
    }

    /// #4 Ngung hoat dong drone (xoa mem) — an khoi danh sach van hanh, giu lich su log.
    @Transactional
    public void decommissionDrone(Long id, String reason, Long actorUserId) {
        String deleteReason = DeleteReason.require(reason);
        DroneUnit unit = findDroneUnit(id);
        if (DroneStatus.RESERVED.equals(unit.getStatus()) || DroneStatus.IN_FLIGHT.equals(unit.getStatus())) {
            throw new BusinessException(
                    "DRONE_ACTIVE_MISSION", "Không thể xóa drone " + unit.getCode() + " đang có nhiệm vụ bay");
        }
        unit.setActive(false);
        unit.setAssignedTechnicianId(null);
        unit.setStatus(DroneStatus.MAINTENANCE);
        unit.setDeletedAt(LocalDateTime.now());
        unit.setDeletedBy(actorUserId);
        unit.setDeletedReason(deleteReason);
        droneUnitRepository.save(unit);
        // Nhả bộ điều khiển để gắn sang drone khác; drone_devices.drone_id là UNIQUE.
        droneDeviceRepository.findByDroneId(id).ifPresent(device -> {
            device.setDroneId(null);
            device.setDroneCode(null);
            droneDeviceRepository.save(device);
        });
        appendDroneLog(id, "Xóa drone: " + deleteReason, actorUserId);
    }

    /// #2 Mo 1 phieu su co gan voi drone (box_id NULL) de no vao chung hang doi
    /// SLA/qua han nhu phieu o tu.
    private void openDroneFaultReport(DroneUnit unit, String reason, Long actorUserId) {
        boolean alreadyOpen =
                reportRepository
                        .findFirstByDroneUnitIdAndStatusInOrderByCreatedAtDesc(unit.getId(), OPEN_REPORT_STATUSES)
                        .isPresent();
        if (alreadyOpen) {
            return;
        }
        LockerReport report = new LockerReport();
        report.setLockerId(unit.getLockerId());
        report.setDroneUnitId(unit.getId());
        report.setCategory(ReportCategory.DRONE);
        report.setUserId(actorUserId == null ? 0L : actorUserId);
        report.setTitle("Drone " + unit.getCode() + " lỗi");
        report.setDescription(StringUtils.hasText(reason) ? reason : "Drone reported faulty");
        report.setRoutedToUserId(unit.getAssignedTechnicianId());
        notifyDroneRouted(reportRepository.save(report), actorUserId);
    }

    private static String technicianLabel(Long technicianId) {
        return technicianId == null ? "chưa phân công" : "KTV #" + technicianId;
    }

    @Transactional
    public DroneUnitResponse updateDroneBattery(Long id, Integer batteryPercent, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        requireDroneOwnership(unit, actorUserId);
        return updateDroneBattery(unit, batteryPercent, actorUserId);
    }

    @Transactional
    public DroneUnitResponse updateDroneBatteryAsAdmin(Long id, Integer batteryPercent, Long actorUserId) {
        return updateDroneBattery(findDroneUnit(id), batteryPercent, actorUserId);
    }

    private DroneUnitResponse updateDroneBattery(
            DroneUnit unit, Integer batteryPercent, Long actorUserId) {
        unit.setBatteryPercent(batteryPercent);
        if (batteryPercent == 100) {
            unit.setLastChargedAt(LocalDateTime.now());
        }
        DroneUnit saved = droneUnitRepository.save(unit);
        appendDroneLog(saved.getId(), "Cập nhật pin " + batteryPercent + "%", actorUserId);
        return toDroneUnit(saved);
    }

    /// Pin do chính drone báo về. Khác cập nhật tay: không ghi nhật ký bảo trì và không coi
    /// 100 % là "vừa sạc xong".
    @Transactional
    public DroneUnitResponse recordDroneTelemetry(String code, Integer batteryPercent) {
        return recordDroneTelemetry(new DroneTelemetryRequest(
                code, batteryPercent, null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Transactional
    public DroneUnitResponse recordDroneTelemetry(DroneTelemetryRequest request) {
        DroneUnit unit = droneUnitRepository
                .findByCodeAndActiveTrue(request.code())
                .orElseThrow(() -> new BusinessException(
                        "DRONE_NOT_FOUND", "Drone not found: " + request.code(), org.springframework.http.HttpStatus.NOT_FOUND));
        if (request.batteryPercent() != null && !request.batteryPercent().equals(unit.getBatteryPercent())) {
            unit.setBatteryPercent(request.batteryPercent());
            unit = droneUnitRepository.save(unit);
        }

        final Long finalUnitId = unit.getId();
        final String finalUnitCode = unit.getCode();
        String mac = request.macAddress() != null && !request.macAddress().isBlank()
                ? request.macAddress().trim().toUpperCase(Locale.ROOT)
                : "DRONE:" + unit.getCode();

        DroneDevice device = droneDeviceRepository.findByDroneId(finalUnitId)
                .or(() -> droneDeviceRepository.findByMacAddress(mac))
                .orElseGet(() -> {
                    DroneDevice created = new DroneDevice();
                    created.setMacAddress(mac);
                    created.setDroneId(finalUnitId);
                    created.setDroneCode(finalUnitCode);
                    return created;
                });

        device.setDroneId(unit.getId());
        device.setDroneCode(unit.getCode());
        if (request.macAddress() != null && !request.macAddress().isBlank()) {
            device.setMacAddress(request.macAddress().trim().toUpperCase(Locale.ROOT));
        }
        if (request.ipAddress() != null) {
            device.setIpAddress(request.ipAddress());
        }
        if (request.firmwareVersion() != null) {
            device.setFirmwareVersion(request.firmwareVersion());
        }
        if (request.mavlinkStatus() != null) {
            device.setMavlinkStatus(request.mavlinkStatus());
        }
        if (request.flightMode() != null) {
            device.setFlightMode(request.flightMode());
        }
        if (request.armed() != null) {
            device.setArmed(request.armed());
        }
        if (request.landedState() != null) {
            device.setLandedState(request.landedState());
        }
        if (request.gpsFixType() != null) {
            device.setGpsFixType(request.gpsFixType());
        }
        if (request.gpsSatellites() != null) {
            device.setGpsSatellites(request.gpsSatellites());
        }
        if (request.batteryPercent() != null) {
            device.setBatteryPercent(request.batteryPercent());
        }
        if (request.voltageV() != null) {
            device.setVoltageV(request.voltageV());
        }
        if (request.currentA() != null) {
            device.setCurrentA(request.currentA());
        }
        if ("DISCONNECTED".equalsIgnoreCase(request.mavlinkStatus()) || "HEARTBEAT_LOST".equalsIgnoreCase(request.mavlinkStatus())) {
            device.setArmed(null);
            device.setFlightMode(null);
            device.setLandedState(null);
            device.setGpsFixType(null);
            device.setGpsSatellites(null);
            device.setBatteryPercent(null);
            device.setVoltageV(null);
            device.setCurrentA(null);
        }
        if (request.cameraStatus() != null) {
            device.setCameraStatus(request.cameraStatus());
        }
        if (request.cameraStreamUrl() != null) {
            device.setCameraStreamUrl(request.cameraStreamUrl());
        } else if (device.getCameraStreamUrl() == null && request.ipAddress() != null) {
            device.setCameraStreamUrl("http://" + request.ipAddress() + ":8080/stream.mjpg");
        }
        boolean wasOffline = !DroneDeviceResponse.isOnline(device.getLastSeenAt());
        String oldMavlink = device.getMavlinkStatus();
        device.setLastSeenAt(LocalDateTime.now());
        droneDeviceRepository.save(device);

        if (wasOffline) {
            DroneIotLog logPi = new DroneIotLog();
            logPi.setDroneId(unit.getId());
            logPi.setDroneCode(unit.getCode());
            logPi.setComponent("PI_CONTROLLER");
            logPi.setEventType("POWER_CONNECTED");
            logPi.setResult("ONLINE");
            logPi.setActorType("SYSTEM");
            logPi.setActorId("Pi-Bootloader");
            logPi.setMessage("Bộ xử lý Raspberry Pi đã có nguồn và trực tuyến — bắt đầu nhận telemetry.");

            logPi.setCreatedAt(LocalDateTime.now());
            droneIotLogRepository.save(logPi);
        }
        if (request.mavlinkStatus() != null && !request.mavlinkStatus().equalsIgnoreCase(oldMavlink)) {
            String statusUpper = request.mavlinkStatus().toUpperCase(Locale.ROOT);
            DroneIotLog logPixhawk = new DroneIotLog();
            logPixhawk.setDroneId(unit.getId());
            logPixhawk.setDroneCode(unit.getCode());
            logPixhawk.setComponent("PIXHAWK");
            if ("CONNECTED".equals(statusUpper)) {
                logPixhawk.setEventType("PIXHAWK_CONNECTED");
                logPixhawk.setResult("CONNECTED");
                logPixhawk.setMessage("Ket noi MAVLink on dinh voi bo dieu khien Pixhawk qua UART.");
            } else {
                logPixhawk.setEventType("PIXHAWK_DISCONNECTED");
                logPixhawk.setResult("DISCONNECTED");
                logPixhawk.setMessage("Canh bao: Mat ket noi MAVLink voi Pixhawk (" + request.mavlinkStatus() + ").");
            }
            logPixhawk.setActorType("SYSTEM");
            logPixhawk.setActorId("MAVLink-Router");

            logPixhawk.setCreatedAt(LocalDateTime.now());
            droneIotLogRepository.save(logPixhawk);
        }

        return toDroneUnit(unit);
    }

    @Transactional(readOnly = true)
    public DroneDeviceResponse getDroneDevice(Long droneId) {
        if (droneId == null) return null;
        return droneDeviceRepository.findByDroneId(droneId)
                .map(this::toDroneDeviceResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<DroneDeviceResponse> listDroneDevices() {
        return droneDeviceRepository.findAllByOrderByIdAsc().stream()
                .map(this::toDroneDeviceResponse)
                .toList();
    }

    @Transactional
    public DroneDeviceResponse assignDroneDevice(Long deviceId, Long droneId) {
        return assignDroneDevice(deviceId, droneId, null, null);
    }

    @Transactional
    public DroneDeviceResponse assignDroneDevice(Long deviceId, Long droneId, Long actorUserId) {
        return assignDroneDevice(deviceId, droneId, null, actorUserId);
    }

    @Transactional
    public DroneDeviceResponse assignDroneDevice(Long deviceId, Long droneId, String ipAddress, Long actorUserId) {
        DroneDevice device = droneDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("DroneDevice", deviceId));
        DroneUnit drone = findDroneUnit(droneId);

        // Chặn gán trùng: Nếu thiết bị này đã được gán cho drone khác
        if (device.getDroneId() != null && !device.getDroneId().equals(droneId)) {
            String otherDrone = device.getDroneCode() != null ? device.getDroneCode() : ("#" + device.getDroneId());
            String piIdent = StringUtils.hasText(device.getIpAddress()) ? device.getIpAddress() : device.getMacAddress();
            throw new BusinessException(
                    "DRONE_DEVICE_ALREADY_ASSIGNED",
                    "Bộ điều khiển Raspberry Pi (IP: " + piIdent + ") đang được gán cho drone " + otherDrone + ". Không được gán trùng!",
                    HttpStatus.CONFLICT
            );
        }

        // Nếu có cập nhật IP, kiểm tra xem IP này có đang bị drone khác dùng không
        if (StringUtils.hasText(ipAddress)) {
            String trimmedIp = ipAddress.trim();
            droneDeviceRepository.findAll().stream()
                    .filter(d -> d.getDroneId() != null && !d.getDroneId().equals(droneId) && !d.getId().equals(deviceId))
                    .filter(d -> trimmedIp.equalsIgnoreCase(d.getIpAddress()))
                    .findFirst()
                    .ifPresent(other -> {
                        String otherDrone = other.getDroneCode() != null ? other.getDroneCode() : ("#" + other.getDroneId());
                        throw new BusinessException(
                                "DRONE_DEVICE_ALREADY_ASSIGNED",
                                "Bộ điều khiển Raspberry Pi (IP: " + trimmedIp + ") đang được gán cho drone " + otherDrone + ". Không được gán trùng!",
                                HttpStatus.CONFLICT
                        );
                    });
            device.setIpAddress(trimmedIp);
            if (device.getCameraStreamUrl() == null || device.getCameraStreamUrl().isBlank()) {
                device.setCameraStreamUrl("http://" + trimmedIp + ":8080/stream.mjpg");
            }
        }

        droneDeviceRepository.findByDroneId(droneId).ifPresent(old -> {
            if (!old.getId().equals(deviceId)) {
                String oldDesc = formatDroneDeviceIdentifier(old);
                old.setDroneId(null);
                old.setDroneCode(null);
                droneDeviceRepository.save(old);
                appendDroneLog(drone.getId(), "Gỡ bộ điều khiển Raspberry Pi cũ: " + oldDesc, actorUserId);
                recordDroneIotLogSilently(
                        drone.getId(),
                        drone.getCode(),
                        "PI_CONTROLLER",
                        "HARDWARE_DETACH",
                        "RELEASED",
                        actorUserId != null ? "USER#" + actorUserId : "SYSTEM",
                        "Gỡ bộ điều khiển Raspberry Pi cũ: " + oldDesc
                );
            }
        });

        device.setDroneId(drone.getId());
        device.setDroneCode(drone.getCode());
        DroneDevice saved = droneDeviceRepository.save(device);

        String deviceDesc = formatDroneDeviceIdentifier(saved);
        appendDroneLog(drone.getId(), "Gán bộ điều khiển Raspberry Pi: " + deviceDesc, actorUserId);
        recordDroneIotLogSilently(
                drone.getId(),
                drone.getCode(),
                "PI_CONTROLLER",
                "HARDWARE_ATTACH",
                "SUCCESS",
                actorUserId != null ? "USER#" + actorUserId : "SYSTEM",
                "Gán bộ điều khiển Raspberry Pi: " + deviceDesc
        );

        return toDroneDeviceResponse(saved);
    }

    @Transactional
    public DroneDeviceResponse unassignDroneDevice(Long deviceId) {
        return unassignDroneDevice(deviceId, null);
    }

    @Transactional
    public DroneDeviceResponse unassignDroneDevice(Long deviceId, Long actorUserId) {
        DroneDevice device = droneDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("DroneDevice", deviceId));
        Long previousDroneId = device.getDroneId();
        String previousDroneCode = device.getDroneCode();
        String deviceDesc = formatDroneDeviceIdentifier(device);

        device.setDroneId(null);
        device.setDroneCode(null);
        DroneDevice saved = droneDeviceRepository.save(device);

        if (previousDroneId != null) {
            appendDroneLog(previousDroneId, "Gỡ bộ điều khiển Raspberry Pi: " + deviceDesc, actorUserId);
            recordDroneIotLogSilently(
                    previousDroneId,
                    previousDroneCode != null ? previousDroneCode : "DRONE-" + previousDroneId,
                    "PI_CONTROLLER",
                    "HARDWARE_DETACH",
                    "RELEASED",
                    actorUserId != null ? "USER#" + actorUserId : "SYSTEM",
                    "Gỡ bộ điều khiển Raspberry Pi: " + deviceDesc
            );
        }

        return toDroneDeviceResponse(saved);
    }

    @Transactional
    public DroneDeviceResponse registerOrUpdateDroneDevice(Long droneId, String macAddress, String ipAddress, String cameraStreamUrl) {
        return registerOrUpdateDroneDevice(droneId, macAddress, ipAddress, cameraStreamUrl, null);
    }

    @Transactional
    public DroneDeviceResponse registerOrUpdateDroneDevice(
            Long droneId, String macAddress, String ipAddress, String cameraStreamUrl, Long actorUserId) {
        DroneUnit drone = findDroneUnit(droneId);
        String mac = macAddress != null && !macAddress.isBlank()
                ? macAddress.trim().toUpperCase(Locale.ROOT)
                : "DRONE:" + drone.getCode();
        String trimmedIp = ipAddress != null && !ipAddress.isBlank() ? ipAddress.trim() : null;

        // Chặn gán trùng: Kiểm tra xem IP hoặc MAC này có đang được gán cho một drone KHÁC hay không
        if (trimmedIp != null || (mac != null && !mac.startsWith("DRONE:"))) {
            droneDeviceRepository.findAll().stream()
                    .filter(d -> d.getDroneId() != null && !d.getDroneId().equals(drone.getId()))
                    .filter(d -> (trimmedIp != null && trimmedIp.equalsIgnoreCase(d.getIpAddress()))
                            || (mac != null && !mac.startsWith("DRONE:") && mac.equalsIgnoreCase(d.getMacAddress())))
                    .findFirst()
                    .ifPresent(other -> {
                        String otherDrone = other.getDroneCode() != null ? other.getDroneCode() : ("#" + other.getDroneId());
                        String ident = trimmedIp != null ? trimmedIp : other.getMacAddress();
                        throw new BusinessException(
                                "DRONE_DEVICE_ALREADY_ASSIGNED",
                                "Bộ điều khiển Raspberry Pi (IP: " + ident + ") đang được gán cho drone " + otherDrone + ". Không được gán trùng!",
                                HttpStatus.CONFLICT
                        );
                    });
        }

        DroneDevice device = droneDeviceRepository.findByDroneId(drone.getId())
                .or(() -> macAddress != null && !macAddress.isBlank() ? droneDeviceRepository.findByMacAddress(mac) : java.util.Optional.empty())
                .or(() -> trimmedIp != null ? droneDeviceRepository.findByIpAddress(trimmedIp) : java.util.Optional.empty())
                .orElseGet(() -> {
                    DroneDevice created = new DroneDevice();
                    created.setMacAddress(mac);
                    created.setDroneId(drone.getId());
                    created.setDroneCode(drone.getCode());
                    return created;
                });

        if (device.getDroneId() != null && !device.getDroneId().equals(drone.getId())) {
            String otherDrone = device.getDroneCode() != null ? device.getDroneCode() : ("#" + device.getDroneId());
            String ident = device.getIpAddress() != null ? device.getIpAddress() : device.getMacAddress();
            throw new BusinessException(
                    "DRONE_DEVICE_ALREADY_ASSIGNED",
                    "Bộ điều khiển Raspberry Pi (IP: " + ident + ") đang được gán cho drone " + otherDrone + ". Không được gán trùng!",
                    HttpStatus.CONFLICT
            );
        }

        device.setDroneId(drone.getId());
        device.setDroneCode(drone.getCode());
        device.setMacAddress(mac);
        if (trimmedIp != null) {
            device.setIpAddress(trimmedIp);
        }
        if (cameraStreamUrl != null && !cameraStreamUrl.isBlank()) {
            device.setCameraStreamUrl(cameraStreamUrl.trim());
        } else if (device.getIpAddress() != null && !device.getIpAddress().isBlank()) {
            device.setCameraStreamUrl("http://" + device.getIpAddress() + ":8080/stream.mjpg");
        }
        // Thao tác gán cấu hình thiết bị của Admin: KHÔNG giả lập lastSeenAt, pin 100% hay tín hiệu Pixhawk.
        // Chỉ lưu thông tin định danh phần cứng; trạng thái trực tuyến sẽ do telemetry thực tế quyết định.
        DroneDevice saved = droneDeviceRepository.save(device);

        String deviceDesc = formatDroneDeviceIdentifier(saved);
        appendDroneLog(drone.getId(), "Gán bộ điều khiển Raspberry Pi: " + deviceDesc, actorUserId);
        recordDroneIotLogSilently(
                drone.getId(),
                drone.getCode(),
                "PI_CONTROLLER",
                "HARDWARE_ATTACH",
                "SUCCESS",
                actorUserId != null ? "USER#" + actorUserId : "SYSTEM",
                "Gán bộ điều khiển Raspberry Pi: " + deviceDesc
        );

        return toDroneDeviceResponse(saved);
    }

    private String formatDroneDeviceIdentifier(DroneDevice device) {
        if (device == null) return "—";
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(device.getMacAddress())) {
            sb.append("MAC ").append(device.getMacAddress());
        }
        if (StringUtils.hasText(device.getIpAddress())) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("IP ").append(device.getIpAddress());
        }
        if (sb.length() == 0) {
            sb.append("Thiết bị #").append(device.getId());
        }
        return sb.toString();
    }

    private void recordDroneIotLogSilently(
            Long droneId,
            String droneCode,
            String component,
            String eventType,
            String result,
            String actorId,
            String message) {
        try {
            DroneIotLog log = new DroneIotLog();
            log.setDroneId(droneId);
            log.setDroneCode(droneCode);
            log.setComponent(component);
            log.setEventType(eventType);
            log.setResult(result);
            log.setActorType(actorId != null && actorId.startsWith("USER#") ? "ADMIN" : "SYSTEM");
            log.setActorId(actorId);
            log.setMessage(message);
            log.setCreatedAt(LocalDateTime.now());
            droneIotLogRepository.save(log);
        } catch (Exception ignored) {
            // Non-critical telemetry logging failure should not abort device assignment
        }
    }

    private DroneDeviceResponse toDroneDeviceResponse(DroneDevice device) {
        if (device == null) return null;
        return new DroneDeviceResponse(
                device.getId(),
                device.getMacAddress(),
                device.getDroneId(),
                device.getDroneCode(),
                device.getIpAddress(),
                device.getFirmwareVersion(),
                device.getMavlinkStatus(),
                device.getFlightMode(),
                device.getArmed(),
                device.getLandedState(),
                device.getGpsFixType(),
                device.getGpsSatellites(),
                device.getBatteryPercent(),
                device.getVoltageV(),
                device.getCurrentA(),
                device.getCameraStatus(),
                device.getCameraStreamUrl(),
                device.getLastSeenAt(),
                DroneDeviceResponse.isOnline(device.getLastSeenAt()),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    private void requireDroneWithoutActiveMission(DroneUnit unit, String action) {
        if (DroneStatus.RESERVED.equals(unit.getStatus()) || DroneStatus.IN_FLIGHT.equals(unit.getStatus())) {
            throw new BusinessException(
                    "DRONE_ACTIVE_MISSION",
                    "Cannot " + action + " a drone while it has an active mission");
        }
    }

    @Transactional(readOnly = true)
    public List<DroneIotLogResponse> getDroneIotLogs(Long droneId, String component, String eventType, String result, String search) {
        String comp = (component != null && !component.isBlank() && !"ALL".equalsIgnoreCase(component)) ? component.trim() : null;
        String evt = (eventType != null && !eventType.isBlank() && !"ALL".equalsIgnoreCase(eventType)) ? eventType.trim() : null;
        String res = (result != null && !result.isBlank() && !"ALL".equalsIgnoreCase(result)) ? result.trim() : null;
        String q = (search != null && !search.isBlank()) ? search.trim() : null;

        List<DroneIotLog> logs;
        if (comp == null && evt == null && res == null && q == null) {
            logs = droneIotLogRepository.findByDroneIdOrderByCreatedAtDesc(droneId);
        } else {
            logs = droneIotLogRepository.searchLogs(droneId, comp, evt, res, q);
        }

        return logs.stream()
                .map(this::toDroneIotLogResponse)
                .toList();
    }

    @Transactional
    public DroneIotLogResponse createDroneIotLog(CreateDroneIotLogRequest request) {
        DroneUnit drone = findDroneUnit(request.droneId());
        DroneIotLog log = new DroneIotLog();
        log.setDroneId(drone.getId());
        log.setDroneCode(drone.getCode());
        log.setComponent(request.component());
        log.setEventType(request.eventType());
        log.setResult(request.result());
        log.setActorType(request.actorType() != null ? request.actorType() : "SYSTEM");
        log.setActorId(request.actorId());
        log.setOrderId(request.orderId());







        log.setMessage(request.message());

        log.setCreatedAt(LocalDateTime.now());
        return toDroneIotLogResponse(droneIotLogRepository.save(log));
    }

    private DroneIotLogResponse toDroneIotLogResponse(DroneIotLog log) {
        return new DroneIotLogResponse(
                log.getId(),
                log.getDroneId(),
                log.getDroneCode(),
                log.getComponent(),
                log.getEventType(),
                log.getResult(),
                log.getActorType(),
                log.getActorId(),
                log.getOrderId(),







                log.getMessage(),

                log.getCreatedAt()
        );
    }

    public List<DroneMaintenanceLogResponse> droneLogs(Long droneUnitId) {
        return droneMaintenanceLogRepository.findByDroneUnitIdOrderByCreatedAtAsc(droneUnitId).stream()
                .map(this::toDroneLog)
                .toList();
    }

    @Transactional
    public DroneMaintenanceLogResponse addDroneLog(Long droneUnitId, String note, Long actorUserId) {
        DroneUnit unit = findDroneUnit(droneUnitId);
        requireDroneOwnership(unit, actorUserId);
        String normalized = normalizeText(note);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("DRONE_LOG_NOTE_REQUIRED", "Drone maintenance note is required");
        }
        return toDroneLog(appendDroneLog(droneUnitId, normalized, actorUserId));
    }

    @Transactional
    public DroneMaintenanceLogResponse addDroneLogAsAdmin(Long droneUnitId, String note, Long actorUserId) {
        findDroneUnit(droneUnitId);
        String normalized = normalizeText(note);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("DRONE_LOG_NOTE_REQUIRED", "Drone maintenance note is required");
        }
        return toDroneLog(appendDroneLog(droneUnitId, normalized, actorUserId));
    }

    private DroneMaintenanceLog appendDroneLog(Long droneUnitId, String note, Long actorUserId) {
        DroneMaintenanceLog log = new DroneMaintenanceLog();
        log.setDroneUnitId(droneUnitId);
        log.setActorUserId(actorUserId);
        log.setNote(normalizeText(note));
        return droneMaintenanceLogRepository.save(log);
    }

    private void requireDroneOwnership(DroneUnit unit, Long actorUserId) {
        if (actorUserId == null
                || unit.getAssignedTechnicianId() == null
                || !actorUserId.equals(unit.getAssignedTechnicianId())) {
            throw new BusinessException("DRONE_OWNERSHIP_REQUIRED", "You must be the assigned technician for this drone");
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public DroneUnit findDroneUnit(Long id) {
        return droneUnitRepository.findById(id)
                .filter(unit -> Boolean.TRUE.equals(unit.getActive()))
                .orElseThrow(() -> new NotFoundException("DroneUnit", id));
    }

    private DroneUnitResponse toDroneUnit(DroneUnit unit) {
        LockerUnit locker = lockerRepository.findById(unit.getLockerId()).orElse(null);
        UserSummary technician = lookupUserQuietly(unit.getAssignedTechnicianId());
        return new DroneUnitResponse(
                unit.getId(),
                unit.getLockerId(),
                locker == null ? null : locker.getCode(),
                locker == null ? null : locker.getName(),
                unit.getCode(),
                unit.getStatus(),
                unit.getBatteryPercent(),
                unit.getFaultReason(),
                unit.getAssignedTechnicianId(),
                technician == null ? null : technician.fullName(),
                unit.getLastChargedAt(),
                unit.getActive(),
                unit.getCreatedAt(),
                unit.getUpdatedAt());
    }

    private DroneMaintenanceLogResponse toDroneLog(DroneMaintenanceLog log) {
        return new DroneMaintenanceLogResponse(
                log.getId(), log.getDroneUnitId(), log.getActorUserId(), log.getNote(), log.getCreatedAt());
    }

    private LockerStatsResponse toStats(LockerUnit locker) {
        List<LockerBox> boxes = boxRepository.findByLockerId(locker.getId());
        int total = boxes.size();
        int available = (int) boxes.stream().filter(b -> "AVAILABLE".equals(b.getStatus())).count();
        int reserved = (int) boxes.stream().filter(b -> "RESERVED".equals(b.getStatus())).count();
        int occupied = (int) boxes.stream().filter(b -> "OCCUPIED".equals(b.getStatus())).count();
        int fault = (int) boxes.stream().filter(b -> "FAULT".equals(b.getStatus())).count();
        double utilization = total == 0 ? 0.0 : Math.round((reserved + occupied) * 1000.0 / total) / 10.0;
        long openReports = reportRepository.countByLockerIdAndStatusIn(locker.getId(), OPEN_REPORT_STATUSES);
        return new LockerStatsResponse(
                locker.getId(), locker.getCode(), locker.getName(), locker.getStatus(), locker.getLandingPad(),
                total, available, reserved, occupied, fault, utilization, openReports);
    }

    @Transactional(readOnly = true)
    public CellResponse getCell(Long boxId) {
        return toCell(findBox(boxId));
    }

    private LockerBox findBox(Long id) {
        return boxRepository.findById(id).orElseThrow(() -> new NotFoundException("Box", id));
    }

    /// Như {@link #findBox} nhưng khoá dòng (SELECT … FOR UPDATE) tới hết giao dịch — dùng cho
    /// reserve/occupy/release để hai luồng không cùng đọc một trạng thái cũ rồi ghi đè nhau.
    private LockerBox lockBox(Long id) {
        return boxRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Box", id));
    }

    /// API công khai (app khách, kiosk): không lộ KTV phụ trách.
    private LockerResponse toResponse(LockerUnit locker) {
        return toResponse(locker, null, gatewayOnline(locker.getId()));
    }

    /// Admin/KTV: kèm KTV phụ trách; `technicianNames` là cache tên trong một lần trả danh sách.
    private LockerResponse toStaffResponse(LockerUnit locker, Map<Long, String> technicianNames) {
        return toResponse(locker, technicianNames, gatewayOnline(locker.getId()));
    }

    /// Cờ `online` của một tủ — cùng nguồn với `layout()`: bộ điều khiển gắn với tủ còn tín hiệu.
    /// `null` khi tủ chưa gắn bộ điều khiển hoặc không tra được iot-service (chưa biết, không phải mất kết nối).
    private Boolean gatewayOnline(Long lockerId) {
        try {
            var response = iotClient.getGatewayByLockerId(lockerId);
            return response == null || response.data() == null ? null : response.data().online();
        } catch (Exception ex) {
            log.debug("Could not fetch gateway online status for locker {}: {}", lockerId, ex.getMessage());
            return null;
        }
    }

    /// Cờ `online` cho cả danh sách tủ bằng MỘT lần gọi iot-service (không gọi từng tủ).
    private Map<Long, Boolean> gatewayOnlineByLocker() {
        try {
            var response = iotClient.listGateways();
            List<IotClient.GatewayDeviceResponse> gateways =
                    response == null || response.data() == null ? List.of() : response.data();
            Map<Long, Boolean> online = new HashMap<>();
            for (IotClient.GatewayDeviceResponse gateway : gateways) {
                if (gateway != null && gateway.lockerId() != null) {
                    online.merge(gateway.lockerId(), gateway.online(), Boolean::logicalOr);
                }
            }
            return online;
        } catch (Exception ex) {
            log.debug("Could not fetch gateway list for locker online flags: {}", ex.getMessage());
            return Map.of();
        }
    }

    private List<LockerResponse> toResponses(List<LockerUnit> lockers, Map<Long, String> technicianNames) {
        if (lockers.isEmpty()) {
            return List.of();
        }
        Map<Long, Boolean> online = gatewayOnlineByLocker();
        return lockers.stream()
                // Tủ không có trong danh sách bộ điều khiển ⇒ null (chưa gắn), không phải mất kết nối.
                .map(locker -> toResponse(locker, technicianNames, online.get(locker.getId())))
                .toList();
    }

    private LockerResponse toResponse(LockerUnit locker, Map<Long, String> technicianNames, Boolean online) {
        int totalBoxes = (int) boxRepository.countByLockerId(locker.getId());
        // Tủ đang bảo trì không nhận đơn ⇒ app không được thấy còn ô trống.
        int availableBoxes = isBookable(locker)
                ? (int) boxRepository.countByLockerIdAndStatusAndActiveTrue(locker.getId(), "AVAILABLE")
                : 0;
        Long technicianId = technicianNames == null ? null : locker.getAssignedTechnicianId();
        String technicianName =
                technicianId == null ? null : technicianNames.computeIfAbsent(technicianId, this::resolveUserName);
        return new LockerResponse(
                locker.getId(), locker.getStoreId(), locker.getCode(), locker.getName(), locker.getStatus(),
                locker.getAddress(), locker.getLatitude(), locker.getLongitude(),
                locker.getLandingPad(), locker.getLandingMarkerId(), locker.getLandingPadStatus(),
                totalBoxes, availableBoxes,
                technicianId, technicianName, online);
    }

    private LockerBoxSummary toSummary(LockerBox box) {
        return new LockerBoxSummary(box.getLockerId(), box.getId(), null, box.getBoxNumber(), box.getStatus());
    }

    private CellResponse toCell(LockerBox box) {
        return toCell(box, null);
    }

    private CellResponse toCell(LockerBox box, IotClient.BoxHardwareStatus hw) {
        Boolean doorOpen = hw != null && "OPEN".equalsIgnoreCase(hw.hwState());
        String hwState = hw != null ? hw.hwState() : null;
        return new CellResponse(
                box.getId(),
                box.getBoxNumber(),
                box.getSize(),
                box.getCellType(),
                box.getRowIndex(),
                box.getColIndex(),
                box.getStatus(),
                box.getFaultReason(),
                doorOpen,
                hwState,
                box.getLockerId(),
                box.getArucoDictionary(),
                box.getArucoMarkerId(),
                box.getArucoMarkerSizeCm(),
                box.getArucoAssignedAt(),
                box.getArucoAssignedBy(),
                box.getActive());
    }

    private void publishBoxFault(LockerBox box, String reason) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.LOCKER_BOX_FAULT,
                    DomainEvent.of(
                            DomainEventNames.LOCKER_BOX_FAULT,
                            "locker-service",
                            Map.of(
                                    "lockerId", box.getLockerId(),
                                    "boxId", box.getId(),
                                    "boxNumber", box.getBoxNumber(),
                                    "reason", reason == null ? "" : reason)));
        } catch (AmqpException ex) {
            log.warn("Could not publish locker.box.fault for box {}: {}", box.getId(), ex.getMessage());
        }
    }

    private LockerReportResponse toReport(LockerReport report) {
        return toReport(
                report,
                attachmentService.byReportIds(List.of(report.getId())).getOrDefault(report.getId(), List.of()),
                new HashMap<>());
    }

    /// Danh sách phiếu: nạp ảnh của mọi phiếu bằng một truy vấn thay vì từng phiếu; tên người báo/KTV
    /// tra mỗi người một lần cho cả danh sách.
    private List<LockerReportResponse> toReports(List<LockerReport> reports) {
        Map<Long, List<ReportAttachmentResponse>> attachments =
                attachmentService.byReportIds(reports.stream().map(LockerReport::getId).toList());
        Map<Long, Optional<UserSummary>> users = new HashMap<>();
        return reports.stream()
                .map(report -> toReport(report, attachments.getOrDefault(report.getId(), List.of()), users))
                .toList();
    }

    private LockerReportResponse toReport(
            LockerReport report, List<ReportAttachmentResponse> attachments, Map<Long, Optional<UserSummary>> users) {
        LockerUnit locker = lockerRepository.findById(report.getLockerId()).orElse(null);
        LockerBox box = report.getBoxId() == null ? null : boxRepository.findById(report.getBoxId()).orElse(null);
        int slaHours = rules.slaHours();
        LocalDateTime slaDueAt = report.getSlaDueAt();
        if (slaDueAt == null && report.getCreatedAt() != null) {
            slaDueAt = report.getCreatedAt().plusHours(slaHours);
        }
        boolean overdue =
                slaDueAt != null
                        && !isClosedReport(report.getStatus())
                        && LocalDateTime.now().isAfter(slaDueAt);
        UserSummary reporter = cachedUser(report.getUserId(), users);
        UserSummary assignee = cachedUser(report.getAssignedToUserId(), users);

        return new LockerReportResponse(
                report.getId(),
                report.getLockerId(),
                report.getBoxId(),
                report.getUserId(),
                report.getTitle(),
                report.getDescription(),
                report.getStatus(),
                report.getAssignedToUserId(),
                report.getAssignedAt(),
                report.getResolvedByUserId(),
                report.getResolvedAt(),
                report.getCreatedAt(),
                locker == null ? null : locker.getCode(),
                locker == null ? null : locker.getName(),
                locker == null ? null : locker.getAddress(),
                locker == null ? null : locker.getLatitude(),
                locker == null ? null : locker.getLongitude(),
                box == null ? null : box.getBoxNumber(),
                box == null ? null : box.getCellType(),
                slaHours,
                slaDueAt,
                overdue,
                report.getSlaExtendedHours() != null ? report.getSlaExtendedHours() : 0,
                report.getSlaExtensionReason(),
                reporter == null ? null : reporter.fullName(),
                reporter == null ? null : reporter.phoneNumber(),
                attachments,
                report.getCategory(),
                Boolean.TRUE.equals(report.getBlocksLocker()),
                report.getRoutedToUserId() != null
                        ? report.getRoutedToUserId()
                        : (report.getDroneUnitId() == null
                                ? null
                                : droneUnitRepository.findById(report.getDroneUnitId()).map(DroneUnit::getAssignedTechnicianId).orElse(null)),
                report.getScheduleId(),
                report.getOrderId(),
                report.getOrderCode(),
                report.getDroneUnitId(),
                report.getDroneUnitId() == null
                        ? null
                        : droneUnitRepository.findById(report.getDroneUnitId()).map(DroneUnit::getCode).orElse(null),
                report.getExternalIncidentId(),
                report.getIncidentCode(),
                report.getTicketType(),
                assignee == null ? null : assignee.fullName(),
                report.getSlaExtensionCount() != null ? report.getSlaExtensionCount() : 0,
                rules.slaMaxExtensions());
    }

    // ---- Định tuyến phiếu cho KTV tủ ----

    /// Có `assigneeId` (KTV tủ tự báo, KTV vừa kiểm tra) ⇒ giao luôn. Còn lại phiếu giữ OPEN và
    /// được định tuyến cho KTV phụ trách tủ (null = mọi KTV tủ); KTV vẫn phải bấm nhận để chế tài
    /// SLA áp vào.
    private void applyRouting(LockerReport report, LockerUnit locker, Long assigneeId) {
        if (assigneeId != null) {
            report.setAssignedToUserId(assigneeId);
            report.setAssignedAt(LocalDateTime.now());
            report.setStatus("IN_PROGRESS");
            return;
        }
        report.setRoutedToUserId(locker == null ? null : locker.getAssignedTechnicianId());
    }

    /// Báo phiếu mới cho KTV phụ trách tủ, hoặc mọi KTV tủ khi tủ chưa có người phụ trách.
    /// Phiếu đã có người nhận (KTV tự báo) thì không báo.
    private void notifyRouted(LockerReport report, LockerUnit locker) {
        notifyRouted(report, locker, null);
    }

    /// `excludeUserId`: người vừa trả phiếu về hàng chờ — không báo lại cho chính họ.
    private void notifyRouted(LockerReport report, LockerUnit locker, Long excludeUserId) {
        if (report.getAssignedToUserId() != null) {
            return;
        }
        String message = "Phiếu RPT-" + report.getId() + " — " + report.getTitle() + " tại tủ "
                + (locker == null ? "#" + report.getLockerId() : locker.getName()) + " đang chờ KTV nhận.";
        List<Long> recipients = report.getRoutedToUserId() != null
                ? List.of(report.getRoutedToUserId())
                : lockerTechnicianIds();
        for (Long technicianId : recipients) {
            if (Objects.equals(technicianId, excludeUserId)) {
                continue;
            }
            publishStaffNotification(
                    DomainEventNames.LOCKER_REPORT_ROUTED, technicianId, report.getId(), "LOCKER_REPORT", message);
        }
    }

    /// Phiếu drone mới (hoặc vừa trả về hàng chờ) đang chờ nhận: báo KTV drone được định tuyến, hoặc mọi
    /// KTV drone khi phiếu chưa định tuyến. Không báo người vừa tạo/trả phiếu (`excludeUserId`).
    private void notifyDroneRouted(LockerReport report, Long excludeUserId) {
        if (report.getAssignedToUserId() != null) {
            return;
        }
        String message = "Phiếu RPT-" + report.getId() + " — " + report.getTitle() + " (drone "
                + droneLabel(report.getDroneUnitId()) + ") đang chờ KTV drone nhận.";
        List<Long> recipients = report.getRoutedToUserId() != null
                ? List.of(report.getRoutedToUserId())
                : technicianIds(DRONE_TECHNICIAN);
        for (Long technicianId : recipients) {
            if (Objects.equals(technicianId, excludeUserId)) {
                continue;
            }
            publishStaffNotification(DRONE_REPORT_ROUTED, technicianId, report.getId(), DRONE_REPORT_REFERENCE, message);
        }
    }

    private String droneLabel(Long droneUnitId) {
        if (droneUnitId == null) {
            return "?";
        }
        return droneUnitRepository.findById(droneUnitId).map(DroneUnit::getCode).orElse("#" + droneUnitId);
    }

    private List<Long> lockerTechnicianIds() {
        return technicianIds(LOCKER_TECHNICIAN);
    }

    private List<Long> technicianIds(String role) {
        try {
            List<UserSummary> users = userClient.listByRole(role).data();
            return users == null ? List.of() : users.stream().map(UserSummary::id).filter(Objects::nonNull).toList();
        } catch (Exception ex) {
            log.warn("Could not list {} users to notify: {}", role, ex.getMessage());
            return List.of();
        }
    }

    /// Vai trò người thao tác: `X-User-Roles` từ gateway; gọi nội bộ không có header thì tra user-service.
    private List<String> actorRoles(Long userId, String rolesHeader) {
        if (rolesHeader != null) {
            return UserRoles.parse(rolesHeader);
        }
        UserSummary user = lookupUserQuietly(userId);
        if (user == null || user.roles() == null) {
            return List.of();
        }
        return user.roles().stream().map(LockerService::normalizeRole).toList();
    }

    private static String normalizeRole(String role) {
        String upper = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper.substring(5) : upper;
    }

    /// Chỉ giao việc cho tài khoản ACTIVE có đúng vai trò KTV (hoặc ADMIN tự nhận việc).
    private void requireTechnician(Long userId, String role) {
        if (userId == null) {
            throw new BusinessException("TECHNICIAN_REQUIRED", "technicianId is required");
        }
        UserSummary user;
        try {
            user = userClient.getUser(userId).data();
        } catch (FeignException.NotFound ex) {
            throw new NotFoundException("User", userId);
        } catch (Exception ex) {
            throw new BusinessException(
                    "USER_SERVICE_UNAVAILABLE", "Chưa kiểm tra được tài khoản KTV, vui lòng thử lại",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        List<String> roles = user == null || user.roles() == null
                ? List.of()
                : user.roles().stream().map(LockerService::normalizeRole).toList();
        if (!roles.contains(role) && !roles.contains("ADMIN")) {
            throw new BusinessException(
                    "TECHNICIAN_ROLE_REQUIRED", "Tài khoản #" + userId + " không có vai trò " + role);
        }
        if (user.status() != null && !"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new BusinessException("TECHNICIAN_INACTIVE", "Tài khoản #" + userId + " đang không hoạt động");
        }
    }

    private String lockerLabel(Long lockerId) {
        return lockerRepository.findById(lockerId).map(LockerUnit::getName).orElse("#" + lockerId);
    }

    /// Thông báo in-app + push cho KTV (notification-service tạo theo `userId` trong payload, `type` = eventType).
    private void publishStaffNotification(
            String eventType, Long userId, Long referenceId, String referenceType, String message) {
        if (userId == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DRONE_EVENT_ROUTING_KEYS.getOrDefault(eventType, eventType),
                    DomainEvent.of(
                            eventType,
                            "locker-service",
                            Map.of(
                                    "userId", userId,
                                    "referenceId", referenceId,
                                    "referenceType", referenceType,
                                    "message", message)));
        } catch (AmqpException ex) {
            log.warn("Could not publish {} to user {}: {}", eventType, userId, ex.getMessage());
        }
    }

    private void notifyScheduleAssignment(MaintenanceSchedule schedule) {
        if (schedule == null || schedule.getAssignedTechnicianId() == null) {
            return;
        }
        boolean isLocker = schedule.getLockerId() != null;
        String eventType = isLocker ? DomainEventNames.LOCKER_REPORT_ASSIGNED : DRONE_REPORT_ASSIGNED;
        String target = isLocker ? "tủ " + lockerLabel(schedule.getLockerId()) : "drone " + droneLabel(schedule.getDroneUnitId());
        String msg = "Bạn được phân công lịch kiểm tra định kỳ \"" + schedule.getTitle() + "\" tại " + target + ".";
        publishStaffNotification(eventType, schedule.getAssignedTechnicianId(), schedule.getId(), "MAINTENANCE_SCHEDULE", msg);
    }

    // Best-effort: maintenance still needs to see status/SLA even if user-service
    // is briefly unreachable, so a contact lookup failure must never break the list.
    private UserSummary lookupUserQuietly(Long userId) {
        if (userId == null || userId == 0L) {
            return null;
        }
        try {
            return userClient.getUser(userId).data();
        } catch (Exception ex) {
            log.debug("Could not resolve reporter contact for user {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    /// Maintenance/admin emergency override — opens a box without the
    /// customer's PIN/QR. Delegates the physical unlock + audit log to
    /// iot-service (which owns the MQTT/access-log infrastructure).
    ///
    /// Chỉ báo `accepted:true` khi iot-service xác nhận tủ đã mở (iot-service tự gọi /open ⇒ phát
    /// sự kiện ô mở). Gọi iot lỗi/không phản hồi ⇒ `accepted:false` + `status` FAILED: không được
    /// báo admin/KTV là ô đã mở khi chưa chắc.
    public Map<String, Object> forceOpen(Long boxId, Long actorUserId) {
        return forceOpen(boxId, actorUserId, null, false);
    }

    /// Bản có body (app KTV): `reason`/`note` đi xuống iot-service để ghi vào `box_access_logs.message`;
    /// có `reportId` thì thêm một dòng nhật ký xử lý (kèm ảnh) vào phiếu. Phiếu và ảnh được kiểm/lưu
    /// TRƯỚC khi gửi lệnh (sai thì ô không bị mở), kết quả mở ghi bổ sung vào dòng nhật ký sau khi tủ
    /// trả lời. Không có body ⇒ y như trước (web admin).
    public Map<String, Object> forceOpen(Long boxId, Long actorUserId, ForceOpenRequest request, boolean admin) {
        LockerBox box = findBox(boxId);
        String reason = request == null ? null : normalizeText(request.reason());
        String note = request == null ? null : normalizeText(request.note());
        RepairLog reportLog = request == null ? null : openForceOpenLog(box, request, reason, note, actorUserId, admin);
        Map<String, Object> result = sendForceOpen(box, actorUserId, reason, note);
        if (reportLog != null) {
            completeForceOpenLog(reportLog, box, result, reason, note);
            result.put("reportId", reportLog.getReportId());
            result.put("repairLogId", reportLog.getId());
        }
        return result;
    }

    private Map<String, Object> sendForceOpen(LockerBox box, Long actorUserId, String reason, String note) {
        Map<String, Object> result;
        try {
            var response = iotClient.forceUnlock(
                    new IotClient.ForceUnlockRequest(box.getLockerId(), box.getId(), actorUserId, reason, note));
            result = response == null ? null : response.data();
        } catch (Exception ex) {
            log.warn("IoT service force unlock failed for box {}: {}", box.getId(), ex.getMessage());
            return forceOpenFailed(box, "Không gửi được lệnh mở ô tới bộ điều khiển tủ — ô chưa mở, vui lòng thử lại");
        }
        if (result == null) {
            return forceOpenFailed(box, "Bộ điều khiển tủ không xác nhận lệnh mở — ô có thể chưa mở, vui lòng kiểm tra lại");
        }
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(result);
        enriched.putIfAbsent("boxNumber", box.getBoxNumber());
        return enriched;
    }

    /// Kiểm phiếu (cùng tủ, chưa hoàn tất) + quyền gắn ảnh, rồi lưu dòng nhật ký "đang mở" kèm ảnh.
    /// Không có phiếu thì không có chỗ lưu ảnh ⇒ báo lỗi thay vì bỏ ảnh âm thầm.
    private RepairLog openForceOpenLog(
            LockerBox box, ForceOpenRequest request, String reason, String note, Long actorUserId, boolean admin) {
        boolean hasAttachments = request.attachments() != null && !request.attachments().isEmpty();
        if (request.reportId() == null) {
            if (hasAttachments) {
                throw new BusinessException(
                        "FORCE_OPEN_REPORT_REQUIRED", "Ảnh mở khẩn cấp chỉ lưu được vào một phiếu sự cố — cần reportId");
            }
            return null;
        }
        LockerReport report = reportRepository.findById(request.reportId())
                .orElseThrow(() -> new NotFoundException("LockerReport", request.reportId()));
        assertNotResolved(report);
        if (!Objects.equals(report.getLockerId(), box.getLockerId())) {
            throw new BusinessException(
                    "FORCE_OPEN_REPORT_MISMATCH", "Phiếu RPT-" + report.getId() + " không thuộc tủ của ô đang mở");
        }
        if (hasAttachments) {
            attachmentService.assertCanAttachAsStaff(report, actorUserId, admin);
        }
        RepairLog entry = new RepairLog();
        entry.setReportId(report.getId());
        entry.setActorUserId(actorUserId);
        entry.setNote(forceOpenNote(box, "đang gửi lệnh mở", reason, note));
        RepairLog saved = repairLogRepository.save(entry);
        if (hasAttachments) {
            try {
                attachmentService.attach(
                        report, AttachmentStage.PROGRESS, request.attachments(), actorUserId, saved.getId(),
                        rules.reportPhotosPerRequestStaff());
            } catch (RuntimeException ex) {
                repairLogRepository.delete(saved);
                throw ex;
            }
        }
        return saved;
    }

    private void completeForceOpenLog(
            RepairLog entry, LockerBox box, Map<String, Object> result, String reason, String note) {
        boolean opened = Boolean.TRUE.equals(result.get("accepted"));
        String outcome = opened
                ? "Đã mở ô"
                : "Không mở được ô (" + result.get("status") + "): " + result.get("message");
        entry.setNote(forceOpenNote(box, outcome, reason, note));
        try {
            repairLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Could not record force-open outcome on report {}: {}", entry.getReportId(), ex.getMessage());
        }
    }

    private static String forceOpenNote(LockerBox box, String outcome, String reason, String note) {
        String text = "[MỞ KHẨN CẤP] Ô #" + box.getBoxNumber() + " — " + outcome
                + (reason != null ? ". Lý do: " + reason : "")
                + (note != null ? ". Ghi chú: " + note : "");
        return text.length() <= 2000 ? text : text.substring(0, 2000);
    }

    private static Map<String, Object> forceOpenFailed(LockerBox box, String message) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("accepted", false);
        result.put("status", "FAILED");
        result.put("lockerId", box.getLockerId());
        result.put("boxId", box.getId());
        result.put("boxNumber", box.getBoxNumber());
        result.put("message", message);
        return result;
    }


    /// Booking → IoT sync (GAP 1): best-effort mirror of a box's new lifecycle
    /// state (RESERVED/OCCUPIED/AVAILABLE/FAULT) down to the cabinet via
    /// iot-service. Never throws — a down/slow iot-service must not break the
    /// booking/maintenance flow that just changed the box in the DB.
    private void syncBoxStateQuietly(LockerBox box, String state) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                iotClient.syncBoxState(new IotClient.BoxStateSyncRequest(box.getLockerId(), box.getId(), state, null));
            } catch (Exception ex) {
                log.debug("Box-state sync to IoT skipped for box {} ({}): {}", box.getId(), state, ex.getMessage());
            }
        });
    }

    /// Maintenance box-health: the order-driven logical box status (this service)
    /// side-by-side with the cabinet-reported hardware door state (iot-service,
    /// GAP 2), flagging doors physically open on boxes that aren't OCCUPIED. The
    /// hardware lookup is best-effort — if iot-service is down, hwState is null and
    /// the logical status still shows.
    @Transactional(readOnly = true)
    public List<BoxHealthResponse> boxHealth(Long lockerId) {
        List<LockerBox> boxes = boxRepository.findByLockerIdOrderByRowIndexAscColIndexAsc(lockerId);
        Map<Long, IotClient.BoxHardwareStatus> hwByBox = fetchHardwareStatuses(lockerId);
        return boxes.stream()
                .map(
                        box -> {
                            IotClient.BoxHardwareStatus hw = hwByBox.get(box.getId());
                            String hwState = hw == null ? null : hw.hwState();
                            boolean doorOpen = "OPEN".equalsIgnoreCase(hwState);
                            boolean needsAttention = doorOpen && !"OCCUPIED".equalsIgnoreCase(box.getStatus());
                            return new BoxHealthResponse(
                                    box.getId(),
                                    box.getBoxNumber(),
                                    box.getCellType(),
                                    box.getStatus(),
                                    hwState,
                                    hw == null ? null : hw.lastReportedAt(),
                                    doorOpen,
                                    needsAttention);
                        })
                .toList();
    }

    /// Maintenance shift overview: every box across all lockers whose cabinet
    /// reports the door physically OPEN while it isn't OCCUPIED (likely left ajar).
    /// Hardware truth comes from iot-service (best-effort — returns empty if it's
    /// down). Enriched with locker location so the technician can navigate there.
    @Transactional(readOnly = true)
    public List<BoxAnomalyResponse> boxAnomalies() {
        List<IotClient.BoxHardwareStatus> hw;
        try {
            hw = iotClient.boxStatus(null).data();
        } catch (Exception ex) {
            log.debug("Could not fetch hardware box status for anomalies: {}", ex.getMessage());
            return List.of();
        }
        if (hw == null) {
            return List.of();
        }
        List<BoxAnomalyResponse> out = new ArrayList<>();
        for (IotClient.BoxHardwareStatus h : hw) {
            if (h.boxId() == null || !"OPEN".equalsIgnoreCase(h.hwState())) {
                continue;
            }
            LockerBox box = boxRepository.findById(h.boxId()).orElse(null);
            if (box == null || "OCCUPIED".equalsIgnoreCase(box.getStatus())) {
                continue; // missing box, or door legitimately open while in use
            }
            LockerUnit locker = lockerRepository.findById(box.getLockerId()).orElse(null);
            out.add(
                    new BoxAnomalyResponse(
                            box.getLockerId(),
                            locker == null ? null : locker.getCode(),
                            locker == null ? null : locker.getName(),
                            locker == null ? null : locker.getAddress(),
                            locker == null ? null : locker.getLatitude(),
                            locker == null ? null : locker.getLongitude(),
                            box.getId(),
                            box.getBoxNumber(),
                            box.getCellType(),
                            box.getStatus(),
                            h.hwState(),
                            h.lastReportedAt()));
        }
        return out;
    }

    private Map<Long, IotClient.BoxHardwareStatus> fetchHardwareStatuses(Long lockerId) {
        try {
            List<IotClient.BoxHardwareStatus> list = iotClient.boxStatus(lockerId).data();
            if (list == null) {
                return Map.of();
            }
            return list.stream()
                    .filter(h -> h.boxId() != null)
                    .collect(java.util.stream.Collectors.toMap(IotClient.BoxHardwareStatus::boxId, h -> h, (a, b) -> a));
        } catch (Exception ex) {
            log.debug("Could not fetch hardware box status for locker {}: {}", lockerId, ex.getMessage());
            return Map.of();
        }
    }

    @Transactional
    public LockerReportRatingResponse rateReport(Long reportId, Long userId, LockerReportRatingRequest request) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        if (!report.getUserId().equals(userId)) {
            throw new BusinessException("REPORT_NOT_OWNED", "Only the reporting customer can rate this report");
        }
        if (!"RESOLVED".equalsIgnoreCase(report.getStatus())) {
            throw new BusinessException("REPORT_NOT_RESOLVED", "Only resolved reports can be rated");
        }
        LockerReportRating rating = ratingRepository.findByReportId(reportId).orElseGet(LockerReportRating::new);
        rating.setReportId(reportId);
        rating.setUserId(userId);
        rating.setRating(request.rating());
        rating.setComment(request.comment());
        LockerReportRating saved = ratingRepository.save(rating);
        if (report.getAssignedToUserId() != null) {
            boolean drone = isDroneReport(report);
            String target = drone ? "drone " + droneLabel(report.getDroneUnitId()) : "tủ " + lockerLabel(report.getLockerId());
            String commentPart = StringUtils.hasText(saved.getComment()) ? ": \"" + saved.getComment().trim() + "\"" : ".";
            String msg = "Khách hàng đã đánh giá " + saved.getRating() + "★ cho phiếu RPT-" + report.getId() + " (" + target + ")" + commentPart;
            String eventType = drone ? DRONE_REPORT_ASSIGNED : DomainEventNames.LOCKER_REPORT_ASSIGNED;
            publishStaffNotification(eventType, report.getAssignedToUserId(), report.getId(), "LOCKER_REPORT_RATING", msg);
        }
        return toRating(saved);
    }

    @Transactional(readOnly = true)
    public LockerReportRatingResponse getReportRating(Long reportId) {
        return ratingRepository
                .findByReportId(reportId)
                .map(this::toRating)
                .orElseThrow(() -> new NotFoundException("LockerReportRating", reportId));
    }

    /// Average rating + count across reports a technician has handled — lets
    /// maintenance see their own feedback without a full analytics dashboard.
    @Transactional(readOnly = true)
    public Map<String, Object> myRatingAverage(Long technicianUserId) {
        List<Long> reportIds = reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(technicianUserId).stream()
                .map(LockerReport::getId)
                .toList();
        List<LockerReportRating> ratings =
                reportIds.isEmpty() ? List.of() : ratingRepository.findByReportIdIn(reportIds);
        double average = ratings.stream().mapToInt(LockerReportRating::getRating).average().orElse(0.0);
        return Map.of("count", ratings.size(), "average", Math.round(average * 10) / 10.0);
    }

    private LockerReportRatingResponse toRating(LockerReportRating rating) {
        return new LockerReportRatingResponse(
                rating.getId(), rating.getReportId(), rating.getUserId(), rating.getRating(), rating.getComment(), rating.getCreatedAt());
    }

    private void publishBoxOpened(LockerBox box) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.LOCKER_BOX_OPENED,
                    DomainEvent.of(
                            DomainEventNames.LOCKER_BOX_OPENED,
                            "locker-service",
                            Map.of("lockerId", box.getLockerId(), "boxId", box.getId(), "boxNumber", box.getBoxNumber())));
        } catch (AmqpException ex) {
            log.warn("Could not publish locker.box.opened for box {}: {}", box.getId(), ex.getMessage());
        }
    }

    // Lets the reporting customer hear back when maintenance claims/resolves their
    // ticket — notification-service turns this into an in-app + push notification.
    private void publishReportNotification(LockerReport report, String eventType, String messageSuffix) {
        if (report.getUserId() == null || report.getUserId() == 0L) {
            return;
        }
        LockerUnit locker = lockerRepository.findById(report.getLockerId()).orElse(null);
        String lockerLabel = locker == null ? ("#" + report.getLockerId()) : locker.getName();
        String message = "Báo cáo lỗi tủ " + lockerLabel + " của bạn " + messageSuffix + ".";
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    eventType,
                    DomainEvent.of(
                            eventType,
                            "locker-service",
                            Map.of(
                                    "userId", report.getUserId(),
                                    "referenceId", report.getId(),
                                    "referenceType", "LOCKER_REPORT",
                                    "lockerId", report.getLockerId(),
                                    "message", message)));
        } catch (AmqpException ex) {
            log.warn("Could not publish {} for report {}: {}", eventType, report.getId(), ex.getMessage());
        }
    }

    /// Tủ đổi thông tin khách thấy (tên, địa chỉ, toạ độ, cửa hàng, trạng thái bảo trì) ⇒ app tải lại danh sách tủ.
    private void publishLockerCatalogChanged(Long lockerId) {
        CatalogChangeEvents.publish(rabbitTemplate, "locker-service", CatalogChangeEvents.LOCKER, lockerId);
    }

    private void publishLockerAvailabilityChanged(Long lockerId) {
        if (lockerId == null) {
            return;
        }
        // Gửi SAU commit: app nhận tin rồi tải lại phải thấy dữ liệu mới; rollback thì không báo.
        com.huynqb.laundrylocker.common.util.AfterCommit.run(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        DomainEventNames.EXCHANGE,
                        DomainEventNames.LOCKER_AVAILABILITY_CHANGED,
                        DomainEvent.of(
                                DomainEventNames.LOCKER_AVAILABILITY_CHANGED,
                                "locker-service",
                                Map.of("lockerId", lockerId)));
            } catch (AmqpException ex) {
                log.warn("Could not publish locker.availability.changed for locker {}: {}", lockerId, ex.getMessage());
            }
        });
    }

    public void publishLockerLayoutUpdated(Long lockerId, Long boxId, Integer boxNumber, String status, String message) {
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.LOCKER_LAYOUT_UPDATED,
                    DomainEvent.of(
                            DomainEventNames.LOCKER_LAYOUT_UPDATED,
                            "locker-service",
                            Map.of(
                                    "lockerId", lockerId != null ? lockerId : 0L,
                                    "boxId", boxId != null ? boxId : 0L,
                                    "boxNumber", boxNumber != null ? boxNumber : 0,
                                    "status", status != null ? status : "FAULT",
                                    "message", message != null ? message : "Sơ đồ trạm Kiosk đã được cập nhật")));
        } catch (AmqpException ex) {
            log.warn("Could not publish locker.layout.updated for locker {}: {}", lockerId, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ActiveBoxOrderDto getActiveOrderByBox(Long boxId) {
        try {
            var res = orderClient.getActiveOrderByBox(boxId);
            return res != null ? res.data() : null;
        } catch (Exception ex) {
            log.warn("Could not query active order for box {}: {}", boxId, ex.getMessage());
            return null;
        }
    }

    /// Như [#getActiveOrderByBox] nhưng không tra được đơn (order-service không trả lời) thì dừng với
    /// 503 thay vì coi ô là trống. Dùng ở chỗ quyết định nhả / khoá / đổi trạng thái ô: coi nhầm là
    /// trống thì ô còn đồ của khách bị đặt AVAILABLE và cấp cho đơn khác.
    private ActiveBoxOrderDto requireActiveOrderLookup(Long boxId) {
        try {
            var res = orderClient.getActiveOrderByBox(boxId);
            return res != null ? res.data() : null;
        } catch (Exception ex) {
            log.warn("Active order lookup unavailable for box {}: {}", boxId, ex.getMessage());
            throw new BusinessException(
                    "ORDER_LOOKUP_UNAVAILABLE",
                    "Chưa kiểm tra được đơn hàng trong ô, vui lòng thử lại sau ít phút",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static final Set<String> BOX_INCIDENT_ACTIONS =
            Set.of("QUICK_FIX", "RELOCATE", "HANDOVER", "HUB_ESCROW", "LOCK_ONLY");

    /// KTV xử lý sự cố ô tại hiện trường. Chỉ KTV đang được giao phiếu (hoặc ADMIN); ô phải thuộc tủ của
    /// phiếu; cách xử lý lạ bị từ chối thay vì coi như LOCK_ONLY; niêm phong về Hub phải có mã seal do KTV
    /// nhập (in trên túi niêm phong). OTP bàn giao trực tiếp được chuyển nguyên cho order-service kiểm.
    @Transactional
    public LockerReportResponse resolveBoxIncident(
            Long reportId, BoxIncidentResolutionRequest request, Long userId, String rolesHeader) {
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        assertNotResolved(report);
        if (!UserRoles.isAdmin(rolesHeader) && (userId == null || !userId.equals(report.getAssignedToUserId()))) {
            throw new BusinessException(
                    "REPORT_NOT_ASSIGNED", "Chỉ KTV đang được giao phiếu mới xử lý được sự cố ô",
                    HttpStatus.FORBIDDEN);
        }
        String action = request.action() == null ? "" : request.action().trim().toUpperCase(Locale.ROOT);
        if (!BOX_INCIDENT_ACTIONS.contains(action)) {
            throw new BusinessException("INCIDENT_ACTION_INVALID", "Cách xử lý sự cố không hợp lệ: " + request.action());
        }
        String sealNumber = null;
        if ("HUB_ESCROW".equals(action)) {
            if (!StringUtils.hasText(request.sealNumber())) {
                throw new BusinessException(
                        "SEAL_NUMBER_REQUIRED", "Cần nhập mã niêm phong (seal) in trên túi niêm phong");
            }
            sealNumber = request.sealNumber().trim();
        }
        LockerBox box = findBox(request.boxId());
        if (!Objects.equals(box.getLockerId(), report.getLockerId())) {
            throw new BusinessException(
                    "INCIDENT_BOX_NOT_IN_LOCKER", "Ô #" + box.getBoxNumber() + " không thuộc tủ của phiếu RPT-" + report.getId());
        }

        report.setBoxId(box.getId());
        report.setCategory(ReportCategory.BOX);

        ActiveBoxOrderDto activeOrder = requireActiveOrderLookup(box.getId());
        if (report.getOrderId() == null && activeOrder != null) {
            report.setOrderId(activeOrder.orderId());
            report.setOrderCode(activeOrder.orderCode());
        }
        boolean isQuickFix = "QUICK_FIX".equalsIgnoreCase(action);
        boolean shouldLock = request.lockBox() != null ? request.lockBox() : !isQuickFix;

        switch (action) {
            case "QUICK_FIX" -> {
                RepairLog logEntry = new RepairLog();
                logEntry.setReportId(report.getId());
                logEntry.setActorUserId(userId);
                String fixDetail = activeOrder != null
                        ? "KTV đã khắc phục sự cố tại ô #" + box.getBoxNumber() + ", giữ nguyên đồ của khách."
                        : "KTV đã kiểm tra tại Kiosk: Không có đồ trong ô hoặc đã xử lý sự cố tại chỗ.";
                String note = (StringUtils.hasText(request.reason()) && request.reason().startsWith("["))
                        ? request.reason()
                        : ("[GHI CHÚ HIỆN TRƯỜNG] " + (StringUtils.hasText(request.reason()) ? request.reason() : fixDetail));
                if (!shouldLock) {
                    note += " (Không khóa ô, giữ ô hoạt động)";
                }
                logEntry.setNote(note);
                Long logId = repairLogRepository.save(logEntry).getId();
                attachIncidentPhotos(report, request, userId, logId);
                reportRepository.save(report);

                if (activeOrder != null) {
                    try {
                        orderClient.addIncidentNote(activeOrder.orderId(),
                                "KTV đã kiểm tra & khắc phục sự cố tại ô #" + box.getBoxNumber() + ", đồ của khách được giữ an toàn");
                    } catch (Exception ex) {
                        log.warn("Could not record quick fix history on order {}: {}", activeOrder.orderId(), ex.getMessage());
                    }
                    // Sửa xong mà không khoá ô ⇒ khách lấy đồ được ngay.
                    if (!shouldLock) {
                        notifyBoxIncidentResolved(activeOrder.orderId(),
                                "KTV khắc phục sự cố tại ô #" + box.getBoxNumber());
                    }
                }

                if (shouldLock) {
                    box.setStatus("FAULT");
                    box.setPreFaultStatus(null);
                    box.setFaultReason(StringUtils.hasText(request.reason()) ? request.reason() : "Khóa bảo trì sau khi xử lý sự cố ô #" + box.getBoxNumber());
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "FAULT");
                    publishBoxFault(box, box.getFaultReason());
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "FAULT", "Đã khóa bảo trì ô #" + box.getBoxNumber());
                } else {
                    if (activeOrder == null) {
                        box.setStatus("AVAILABLE");
                        box.setFaultReason(null);
                        box.setPreFaultStatus(null);
                        box.setReservedUntil(null);
                        boxRepository.save(box);
                        syncBoxStateQuietly(box, "AVAILABLE");
                        publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "AVAILABLE", "Đã xử lý xong ô #" + box.getBoxNumber() + ", ô hoạt động bình thường");
                    } else {
                        // Sửa xong, không khoá: ô (đang giữ đồ của khách) về hoạt động ngay, không chờ nghiệm thu
                        // — để lần quét sự cố không dừng phí / khoá đơn lại.
                        if (isFaultHold(box.getStatus())) {
                            restoreBox(box);
                        }
                        publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), box.getStatus(), "Đã sửa chữa tại chỗ ô #" + box.getBoxNumber());
                    }
                }
            }
            case "RELOCATE" -> {
                if (request.targetBoxId() == null) {
                    throw new BusinessException("TARGET_BOX_REQUIRED", "Cần chọn ô trống đích để điều chuyển hàng");
                }
                LockerBox targetBox = findBox(request.targetBoxId());
                if (!"AVAILABLE".equalsIgnoreCase(targetBox.getStatus())) {
                    throw new BusinessException("TARGET_BOX_NOT_AVAILABLE", "Ô đích không còn ở trạng thái trống");
                }
                // Ô drone (nóc tủ) chỉ nhận kiện drone: hàng thuê/gửi chuyển vào đó thì khách không mở được
                // bằng luồng thường và ô bị drone ghi đè.
                if (CellType.DRONE.equalsIgnoreCase(targetBox.getCellType())
                        && !CellType.DRONE.equalsIgnoreCase(box.getCellType())) {
                    throw new BusinessException(
                            "TARGET_BOX_DRONE", "Ô #" + targetBox.getBoxNumber() + " là ô drone, hãy chọn ô thường hoặc XL");
                }
                if (activeOrder != null) {
                    try {
                        orderClient.relocateBox(activeOrder.orderId(), targetBox.getId(), targetBox.getBoxNumber());
                    } catch (Exception ex) {
                        log.error("Failed to relocate order {} to box {}: {}", activeOrder.orderId(), targetBox.getId(), ex.getMessage());
                        throw new BusinessException("ORDER_RELOCATE_FAILED", "Không thể cập nhật đơn hàng sang ô mới: " + ex.getMessage());
                    }
                }
                targetBox.setStatus("OCCUPIED");
                targetBox.setReservedUntil(null);
                boxRepository.save(targetBox);
                syncBoxStateQuietly(targetBox, "OCCUPIED");

                RepairLog logEntry = new RepairLog();
                logEntry.setReportId(report.getId());
                logEntry.setActorUserId(userId);
                String orderLabel = activeOrder != null ? ("đơn hàng " + activeOrder.orderCode()) : "đồ trong ô";
                String note = "[ĐIỀU CHUYỂN Ô] Đã chuyển " + orderLabel + " sang ô #" + targetBox.getBoxNumber() + ".";
                if (shouldLock) {
                    box.setStatus("FAULT");
                    box.setPreFaultStatus(null);
                    box.setFaultReason(StringUtils.hasText(request.reason()) ? request.reason() : "Khóa bảo trì sau khi điều chuyển hàng");
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "FAULT");
                    note += " Đã khóa bảo trì ô #" + box.getBoxNumber();
                    publishBoxFault(box, box.getFaultReason());
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "FAULT", "Đã chuyển đồ sang ô #" + targetBox.getBoxNumber() + " và khóa ô #" + box.getBoxNumber());
                } else {
                    box.setStatus("AVAILABLE");
                    box.setFaultReason(null);
                    box.setPreFaultStatus(null);
                    box.setReservedUntil(null);
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "AVAILABLE");
                    note += " Không khóa ô cũ, giữ ô #" + box.getBoxNumber() + " khả dụng";
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "AVAILABLE", "Đã chuyển đồ sang ô #" + targetBox.getBoxNumber() + ", ô #" + box.getBoxNumber() + " hoạt động bình thường");
                }
                logEntry.setNote(note);
                Long logId = repairLogRepository.save(logEntry).getId();
                attachIncidentPhotos(report, request, userId, logId);
                reportRepository.save(report);
            }
            case "HANDOVER" -> {
                if (activeOrder != null) {
                    try {
                        orderClient.directHandover(activeOrder.orderId(), request.customerOtp());
                    } catch (Exception ex) {
                        log.error("Failed direct handover for order {}: {}", activeOrder.orderId(), ex.getMessage());
                        throw new BusinessException("HANDOVER_FAILED", "Bàn giao thất bại: " + ex.getMessage());
                    }
                }

                RepairLog logEntry = new RepairLog();
                logEntry.setReportId(report.getId());
                logEntry.setActorUserId(userId);
                String orderLabel = activeOrder != null ? ("đơn hàng " + activeOrder.orderCode()) : "đồ";
                String note = "[BÀN GIAO TRỰC TIẾP] KTV đã bàn giao " + orderLabel + " cho khách hàng tại Kiosk.";
                if (shouldLock) {
                    box.setStatus("FAULT");
                    box.setPreFaultStatus(null);
                    box.setFaultReason(StringUtils.hasText(request.reason()) ? request.reason() : "Khóa bảo trì sau khi bàn giao trực tiếp cho khách");
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "FAULT");
                    note += " Đã khóa bảo trì ô #" + box.getBoxNumber();
                    publishBoxFault(box, box.getFaultReason());
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "FAULT", "Đã bàn giao đồ và khóa ô #" + box.getBoxNumber());
                } else {
                    box.setStatus("AVAILABLE");
                    box.setFaultReason(null);
                    box.setPreFaultStatus(null);
                    box.setReservedUntil(null);
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "AVAILABLE");
                    note += " Không khóa ô, giữ ô #" + box.getBoxNumber() + " hoạt động bình thường";
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "AVAILABLE", "Đã bàn giao đồ, ô #" + box.getBoxNumber() + " sẵn sàng sử dụng");
                }
                logEntry.setNote(note);
                Long logId = repairLogRepository.save(logEntry).getId();
                attachIncidentPhotos(report, request, userId, logId);
                reportRepository.save(report);
            }
            case "HUB_ESCROW" -> {
                String seal = sealNumber;
                if (activeOrder != null) {
                    try {
                        orderClient.hubEscrow(activeOrder.orderId(), seal);
                    } catch (Exception ex) {
                        log.error("Failed hub escrow for order {}: {}", activeOrder.orderId(), ex.getMessage());
                        throw new BusinessException("HUB_ESCROW_FAILED", "Niêm phong về Hub thất bại: " + ex.getMessage());
                    }
                }

                RepairLog logEntry = new RepairLog();
                logEntry.setReportId(report.getId());
                logEntry.setActorUserId(userId);
                String orderLabel = activeOrder != null ? ("đơn hàng " + activeOrder.orderCode()) : "đồ";
                String note = "[NIÊM PHONG VỀ HUB] Đã niêm phong " + orderLabel + " (Mã Seal: " + seal + ") chuyển về Hub lưu trữ.";
                if (shouldLock) {
                    box.setStatus("FAULT");
                    box.setPreFaultStatus(null);
                    box.setFaultReason(StringUtils.hasText(request.reason()) ? request.reason() : "Khóa bảo trì sau khi niêm phong về Hub (Seal: " + seal + ")");
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "FAULT");
                    note += " Đã khóa bảo trì ô #" + box.getBoxNumber();
                    publishBoxFault(box, box.getFaultReason());
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "FAULT", "Đã niêm phong về Hub và khóa ô #" + box.getBoxNumber());
                } else {
                    box.setStatus("AVAILABLE");
                    box.setFaultReason(null);
                    box.setPreFaultStatus(null);
                    box.setReservedUntil(null);
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "AVAILABLE");
                    note += " Không khóa ô, giữ ô #" + box.getBoxNumber() + " hoạt động bình thường";
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "AVAILABLE", "Đã niêm phong về Hub, ô #" + box.getBoxNumber() + " sẵn sàng sử dụng");
                }
                logEntry.setNote(note);
                Long logId = repairLogRepository.save(logEntry).getId();
                attachIncidentPhotos(report, request, userId, logId);
                reportRepository.save(report);
            }
            default -> { // LOCK_ONLY (đã kiểm hợp lệ ở trên)
                RepairLog logEntry = new RepairLog();
                logEntry.setReportId(report.getId());
                logEntry.setActorUserId(userId);
                String note;
                if (shouldLock) {
                    box.setStatus("FAULT");
                    box.setPreFaultStatus(null);
                    box.setFaultReason(StringUtils.hasText(request.reason()) ? request.reason() : "KTV xác nhận lỗi khớp mô tả khách hàng");
                    boxRepository.save(box);
                    syncBoxStateQuietly(box, "FAULT");
                    note = "[XÁC NHẬN & KHÓA Ô] KTV kiểm tra hiện trường, xác nhận lỗi và khóa bảo trì ô #" + box.getBoxNumber();
                    publishBoxFault(box, box.getFaultReason());
                    publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "FAULT", "Đã khóa bảo trì ô #" + box.getBoxNumber());
                } else {
                    boolean boxHasGoods = activeOrder != null || holdsOrder(box);
                    if (boxHasGoods) {
                        String currentStatus = StringUtils.hasText(box.getStatus()) && !isFaultHold(box.getStatus())
                                ? box.getStatus()
                                : (StringUtils.hasText(box.getPreFaultStatus()) ? box.getPreFaultStatus() : "OCCUPIED");
                        box.setStatus(currentStatus);
                        box.setFaultReason(null);
                        boxRepository.save(box);
                        syncBoxStateQuietly(box, currentStatus);
                        note = "[KIỂM TRA HIỆN TRƯỜNG] KTV kiểm tra hiện trường: Không khóa ô #" + box.getBoxNumber() + ", giữ nguyên trạng thái ô và hàng của khách (" + currentStatus + ")";
                        if (activeOrder != null) {
                            notifyBoxIncidentResolved(activeOrder.orderId(),
                                    "KTV kiểm tra ô #" + box.getBoxNumber() + ": ô hoạt động, không khoá");
                        }
                        publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), currentStatus, "Ô #" + box.getBoxNumber() + " có hàng, giữ trạng thái hoạt động");
                    } else {
                        box.setStatus("AVAILABLE");
                        box.setFaultReason(null);
                        box.setPreFaultStatus(null);
                        box.setReservedUntil(null);
                        boxRepository.save(box);
                        syncBoxStateQuietly(box, "AVAILABLE");
                        note = "[KIỂM TRA HIỆN TRƯỜNG] KTV kiểm tra hiện trường: Không phát hiện lỗi hoặc khách báo nhầm, không khóa ô #" + box.getBoxNumber();
                        publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), "AVAILABLE", "Ô #" + box.getBoxNumber() + " hoạt động bình thường");
                    }
                }
                logEntry.setNote(note);
                Long logId = repairLogRepository.save(logEntry).getId();
                attachIncidentPhotos(report, request, userId, logId);
                reportRepository.save(report);
            }
        }

        // KTV xác nhận đúng sự cố và khoá ô trong khi đồ vẫn còn trong ô ⇒ đơn chuyển sang chờ KTV (không mở
        // bằng mã). Chuyển ô / bàn giao / niêm phong đã đưa đồ ra khỏi ô nên không cần.
        if (shouldLock && activeOrder != null && ("QUICK_FIX".equals(action) || "LOCK_ONLY".equals(action))) {
            notifyBoxIncidentOpened(activeOrder.orderId(), report.getCreatedAt(), true, report.getUserId());
        }
        return toReport(report);
    }

    /// KTV tới kiểm tra thấy không đúng như người báo mô tả: đóng phiếu ở trạng thái REJECTED kèm phản hồi
    /// cho người báo, ô về trạng thái trước khi bị giữ (nếu không còn phiếu mở khác), đơn có đồ trong ô lấy đồ
    /// được và được bù thời gian chờ (phí trong lúc chờ KTV không tính). Chỉ KTV đang được giao phiếu / ADMIN.
    @Transactional
    public LockerReportResponse rejectReport(
            Long reportId, String feedback, List<ReportAttachmentRequest> attachments, Long userId, String rolesHeader) {
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        assertNotResolved(report);
        boolean admin = UserRoles.isAdmin(rolesHeader);
        if (!admin && (userId == null || !userId.equals(report.getAssignedToUserId()))) {
            throw new BusinessException(
                    "REPORT_NOT_ASSIGNED", "Chỉ KTV đang được giao phiếu mới từ chối được phiếu",
                    HttpStatus.FORBIDDEN);
        }
        if (!StringUtils.hasText(feedback)) {
            throw new BusinessException("REJECT_FEEDBACK_REQUIRED", "Cần ghi phản hồi cho người báo (vì sao không xác nhận sự cố)");
        }
        String reply = feedback.trim();
        if (reply.length() > 1000) {
            reply = reply.substring(0, 1000);
        }
        RepairLog logEntry = new RepairLog();
        logEntry.setReportId(report.getId());
        logEntry.setActorUserId(userId);
        logEntry.setNote("[KHÔNG XÁC NHẬN SỰ CỐ] " + reply);
        Long logId = repairLogRepository.save(logEntry).getId();
        if (attachments != null && !attachments.isEmpty()) {
            attachmentService.attach(
                    report, AttachmentStage.INSPECTION, attachments, userId, logId, rules.reportPhotosPerRequestStaff());
        }
        report.setStatus(REPORT_REJECTED);
        report.setResolvedByUserId(userId);
        report.setResolvedAt(LocalDateTime.now());
        LockerReport saved = reportRepository.save(report);
        restoreAssetsAfterClose(saved);
        if (saved.getBoxId() != null) {
            boxRepository.findById(saved.getBoxId()).ifPresent(box -> publishLockerLayoutUpdated(
                    box.getLockerId(), box.getId(), box.getBoxNumber(), box.getStatus(),
                    "Ô #" + box.getBoxNumber() + " được KTV kiểm tra: không có sự cố"));
        }
        if (saved.getOrderId() != null && !hasOtherOpenReportForOrder(saved)) {
            notifyBoxIncidentResolved(saved.getOrderId(), "KTV kiểm tra RPT-" + saved.getId() + ": không xác nhận sự cố");
        }
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_RESOLVED,
                "đã được KTV kiểm tra nhưng không xác nhận sự cố. Phản hồi: " + reply);
        return toReport(saved);
    }

    /// Ô chờ KTV kiểm tra quá [LockerRules#pendingCheckHoldHours] giờ mà chưa ai tới: trả ô về hoạt động để
    /// không treo ô mãi; phiếu vẫn mở (KTV vẫn phải kiểm tra). 0 giờ = không tự gỡ. Trả số ô đã gỡ.
    @Transactional
    public int releaseStalePendingChecks() {
        int hours = rules.pendingCheckHoldHours();
        if (hours <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        int released = 0;
        for (LockerBox box : boxRepository.findByStatusAndActiveTrueOrderByLockerIdAscBoxNumberAsc(PENDING_CHECK)) {
            Optional<LockerReport> open =
                    reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(box.getId(), OPEN_REPORT_STATUSES);
            if (open.isPresent() && open.get().getCreatedAt() != null && open.get().getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            restoreBox(box);
            open.ifPresent(report -> {
                RepairLog logEntry = new RepairLog();
                logEntry.setReportId(report.getId());
                logEntry.setNote("[TỰ GỠ GIỮ Ô] Quá " + hours + " giờ chưa có KTV kiểm tra — ô #" + box.getBoxNumber()
                        + " trở lại hoạt động, phiếu vẫn chờ KTV xử lý");
                repairLogRepository.save(logEntry);
            });
            publishLockerLayoutUpdated(box.getLockerId(), box.getId(), box.getBoxNumber(), box.getStatus(),
                    "Ô #" + box.getBoxNumber() + " chờ kiểm tra quá " + hours + " giờ, đã trở lại hoạt động");
            released++;
        }
        return released;
    }

    private void attachIncidentPhotos(
            LockerReport report, BoxIncidentResolutionRequest request, Long userId, Long logId) {
        attachmentService.attach(
                report, AttachmentStage.INSPECTION, request.attachments(), userId, logId, rules.reportPhotosPerRequestStaff());
        if (request.progressAttachments() != null && !request.progressAttachments().isEmpty()) {
            attachmentService.attach(
                    report, AttachmentStage.PROGRESS, request.progressAttachments(), userId, logId, rules.reportPhotosPerRequestStaff());
        }
    }

    @Transactional
    public CellResponse assignArucoMarker(Long boxId, String actorUserId) {
        LockerBox box = findBox(boxId);
        if (!CellType.DRONE.equalsIgnoreCase(box.getCellType())) {
            throw new BusinessException("BOX_NOT_DRONE_COMPATIBLE", "Chỉ ô tủ dành cho Drone mới có thể gán mã ArUco");
        }
        String dict = StringUtils.hasText(box.getArucoDictionary()) ? box.getArucoDictionary() : "DICT_4X4_50";
        if (!"DICT_4X4_50".equals(dict)) {
            dict = "DICT_4X4_50";
        }

        List<LockerBox> existing = boxRepository.findByArucoDictionaryAndArucoMarkerIdIsNotNull(dict);
        Set<Integer> usedIds = new HashSet<>();
        for (LockerBox b : existing) {
            if (!b.getId().equals(box.getId()) && b.getArucoMarkerId() != null) {
                usedIds.add(b.getArucoMarkerId());
            }
        }

        // DICT_4X4_50 has IDs 0 through 49
        int allocatedId = -1;
        for (int i = 0; i < 50; i++) {
            if (!usedIds.contains(i)) {
                allocatedId = i;
                break;
            }
        }

        if (allocatedId < 0) {
            throw new BusinessException("ARUCO_DICTIONARY_EXHAUSTED", "Tất cả 50 mã ArUco trong từ điển DICT_4X4_50 đã được sử dụng");
        }

        box.setArucoDictionary(dict);
        box.setArucoMarkerId(allocatedId);
        box.setArucoMarkerSizeCm(new BigDecimal("8.00"));
        box.setArucoAssignedAt(LocalDateTime.now());
        box.setArucoAssignedBy(actorUserId != null ? actorUserId : "ADMIN");

        LockerBox saved = boxRepository.save(box);
        log.info("Assigned ArUco marker {} (dict: {}) to box {} (locker {}) by {}",
                allocatedId, dict, boxId, box.getLockerId(), actorUserId);
        return toCell(saved);
    }

    @Transactional
    public CellResponse unassignArucoMarker(Long boxId) {
        LockerBox box = findBox(boxId);
        box.setArucoMarkerId(null);
        box.setArucoAssignedAt(null);
        box.setArucoAssignedBy(null);
        LockerBox saved = boxRepository.save(box);
        log.info("Unassigned ArUco marker from box {} (locker {})", boxId, box.getLockerId());
        return toCell(saved);
    }

}
