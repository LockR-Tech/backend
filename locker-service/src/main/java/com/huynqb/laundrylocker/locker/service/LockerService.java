package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final List<String> SIZE_ORDER = List.of("SMALL", "MEDIUM", "LARGE", "XL");
    private static final String LOCKER_TECHNICIAN = "LOCKER_TECHNICIAN";
    private static final String DRONE_TECHNICIAN = "DRONE_TECHNICIAN";
    /// Tủ ở các trạng thái này không nhận đơn/đặt ô mới.
    private static final Set<String> NON_BOOKABLE_LOCKER_STATUSES = Set.of("MAINTENANCE", "INACTIVE");
    private static final String MAINTENANCE_SOURCE_ADMIN = "ADMIN";
    private static final String MAINTENANCE_SOURCE_TICKET = "TICKET";
    /// Trạng thái ô được nhớ lại khi báo hỏng — đơn vẫn đang giữ ô.
    private static final Set<String> ORDER_HELD_BOX_STATUSES = Set.of("RESERVED", "OCCUPIED");

    private final LockerUnitRepository lockerRepository;
    private final LockerBoxRepository boxRepository;
    private final LockerReportRepository reportRepository;
    private final RepairLogRepository repairLogRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final MaintenanceInspectionLogRepository inspectionLogRepository;
    private final LockerReportRatingRepository ratingRepository;
    private final DroneUnitRepository droneUnitRepository;
    private final DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    private final IotClient iotClient;
    private final UserClient userClient;
    private final ReportAttachmentService attachmentService;
    private final RabbitTemplate rabbitTemplate;
    /// SLA, ngưỡng chế tài KTV, giới hạn ảnh, TTL ô RESERVED, pin drone… admin cấu hình trên web
    /// (ADR-0005), không còn @Value/hằng số cứng. TTL ô RESERVED nên ≥ `app.order.auto-cancel-hours`
    /// để không bao giờ nhả ô sớm hơn order-service.
    private final LockerRules rules;

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
        return toStaffResponse(lockerRepository.save(locker), new HashMap<>());
    }

    @Transactional
    public LockerBoxSummary createBox(BoxRequest request) {
        LockerBox box = new LockerBox();
        box.setLockerId(request.lockerId());
        box.setBoxNumber(request.boxNumber());
        box.setSize(StringUtils.hasText(request.size()) ? request.size() : "MEDIUM");
        box.setStatus(StringUtils.hasText(request.status()) ? request.status() : "AVAILABLE");
        box.setCellType(StringUtils.hasText(request.cellType()) ? request.cellType().toUpperCase() : "STANDARD");
        box.setRowIndex(request.rowIndex());
        box.setColIndex(request.colIndex());
        return toSummary(boxRepository.save(box));
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

    @Transactional
    public void deleteLocker(Long id) {
        lockerRepository.delete(
                lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id)));
    }

    @Transactional
    public LockerResponse setMaintenance(Long id, boolean maintenance) {
        LockerUnit locker =
                lockerRepository.findById(id).orElseThrow(() -> new NotFoundException("Locker", id));
        // Admin bật bảo trì là ý định rõ ràng ⇒ nguồn ADMIN kể cả khi tủ đang bị phiếu chặn.
        locker.setStatus(maintenance ? "MAINTENANCE" : "ACTIVE");
        locker.setMaintenanceSource(maintenance ? MAINTENANCE_SOURCE_ADMIN : null);
        return toStaffResponse(lockerRepository.save(locker), new HashMap<>());
    }

    @Transactional
    public LockerBoxSummary updateBoxStatus(Long boxId, String status) {
        LockerBox box = findBox(boxId);
        box.setStatus(status);
        return toSummary(boxRepository.save(box));
    }

    @Transactional
    public LockerBoxSummary openBox(Long boxId) {
        LockerBox box = findBox(boxId);
        publishBoxOpened(box);
        return toSummary(box);
    }

    @Transactional
    public LockerBoxSummary reserveBox(Long boxId, String channel) {
        LockerBox box = findBox(boxId);
        assertLockerBookable(box.getLockerId());
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
    @Transactional
    public int sweepExpiredReservations() {
        List<LockerBox> expired = boxRepository.findByStatusAndReservedUntilBefore("RESERVED", LocalDateTime.now());
        for (LockerBox box : expired) {
            box.setStatus("AVAILABLE");
            box.setReservedUntil(null);
            boxRepository.save(box);
            log.warn("Released box {} stuck RESERVED past TTL (backstop sweep)", box.getId());
        }
        return expired.size();
    }

    @Transactional
    public LockerBoxSummary occupyBox(Long boxId) {
        LockerBox box = findBox(boxId);
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
        LockerBox box = findBox(boxId);
        String status = box.getStatus();
        if ("FAULT".equalsIgnoreCase(status)
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
        LockerBox box = findBox(boxId);
        // KTV tủ tự báo ⇒ tự nhận phiếu luôn.
        Long assigneeId = actorRoles(userId, rolesHeader).contains(LOCKER_TECHNICIAN) ? userId : null;
        reportBoxFault(box, reason, userId, attachments, assigneeId, null);
        return toCell(box);
    }

    /// Đưa ô về FAULT và trả về phiếu đang mở của ô: gộp vào phiếu cũ nếu có, không thì mở phiếu
    /// mới — giao luôn cho `assigneeId` hoặc định tuyến cho KTV phụ trách tủ khi null.
    private LockerReport reportBoxFault(
            LockerBox box, String reason, Long userId, List<ReportAttachmentRequest> attachments,
            Long assigneeId, String title) {
        Long boxId = box.getId();
        boolean newlyFaulted = !"FAULT".equalsIgnoreCase(box.getStatus());
        if (newlyFaulted) {
            // Nhớ trạng thái đơn đang giữ ô để sửa xong trả lại đúng, không để ô chứa hàng về AVAILABLE.
            box.setPreFaultStatus("AVAILABLE".equalsIgnoreCase(box.getStatus()) ? null : box.getStatus());
            box.setStatus("FAULT");
        }
        if (newlyFaulted || StringUtils.hasText(reason)) {
            box.setFaultReason(reason);
        }
        boxRepository.save(box);

        Optional<LockerReport> open =
                reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(boxId, OPEN_REPORT_STATUSES);
        LockerReport report;
        if (open.isPresent()) {
            // Ô đã có phiếu mở ⇒ gộp vào phiếu đó thay vì mở phiếu trùng và báo KTV lần nữa.
            report = open.get();
            mergeIntoOpenReport(report, reason, userId, attachments);
        } else {
            LockerUnit locker = lockerRepository.findById(box.getLockerId()).orElse(null);
            LockerReport created = new LockerReport();
            created.setLockerId(box.getLockerId());
            created.setBoxId(boxId);
            created.setCategory(ReportCategory.BOX);
            created.setUserId(userId == null ? 0L : userId);
            created.setTitle(title != null ? title : "Box " + box.getBoxNumber() + " fault");
            created.setDescription(StringUtils.hasText(reason) ? reason : "Reported faulty");
            applyRouting(created, locker, assigneeId);
            report = reportRepository.save(created);
            attachmentService.attach(
                    report, AttachmentStage.REPORT, attachments, userId, null,
                    rules.reportPhotosPerRequestReporter());
            notifyRouted(report, locker);
        }
        if (newlyFaulted) {
            publishBoxFault(box, reason);
            syncBoxStateQuietly(box, "FAULT");
        }
        return report;
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
        if (!"FAULT".equalsIgnoreCase(box.getStatus())) {
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
        if ("FAULT".equalsIgnoreCase(status)) {
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
        box.setStatus(target);
        box.setFaultReason(reason);
        return toCell(boxRepository.save(box));
    }

    @Transactional(readOnly = true)
    public LockerLayoutResponse layout(Long lockerId) {
        LockerUnit locker =
                lockerRepository.findById(lockerId).orElseThrow(() -> new NotFoundException("Locker", lockerId));
        List<LockerBox> boxes = boxRepository.findByLockerIdOrderByRowIndexAscColIndexAsc(lockerId);
        List<CellResponse> cells = boxes.stream().map(this::toCell).toList();
        long available = boxes.stream().filter(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus())).count();
        long fault = boxes.stream().filter(b -> "FAULT".equalsIgnoreCase(b.getStatus())).count();
        return new LockerLayoutResponse(
                locker.getId(), locker.getCode(), locker.getName(), locker.getStatus(),
                locker.getLandingPad(), locker.getLandingMarkerId(), locker.getLandingPadStatus(),
                cells.size(), available, fault, cells);
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
            return layout(lockerId);
        }
        locker.setLandingPadStatus(status);
        lockerRepository.save(locker);
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
        return lockerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LockerResponse> listLockers(Long storeId) {
        return (storeId == null ? lockerRepository.findAll() : lockerRepository.findByStoreId(storeId)).stream().map(this::toResponse).toList();
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
        Map<Long, String> technicianNames = new HashMap<>();
        return lockers.stream().map(locker -> toStaffResponse(locker, technicianNames)).toList();
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
        LockerReport report = new LockerReport();
        report.setLockerId(lockerId);
        report.setCategory(ReportCategory.LOCKER);
        report.setBlocksLocker(blocking);
        report.setUserId(userId);
        report.setTitle(request.title());
        report.setDescription(request.description());
        applyRouting(report, locker, technician ? userId : null);
        LockerReport saved = reportRepository.save(report);
        attachmentService.attach(
                saved, AttachmentStage.REPORT, request.attachments(), userId, null,
                rules.reportPhotosPerRequestReporter());
        if (blocking && "ACTIVE".equalsIgnoreCase(locker.getStatus())) {
            locker.setStatus("MAINTENANCE");
            locker.setMaintenanceSource(MAINTENANCE_SOURCE_TICKET);
            lockerRepository.save(locker);
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
        return toReports(reportRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> allReports() {
        return toReports(reportRepository.findAll());
    }

    @Transactional(readOnly = true)
    public LockerReportResponse getReport(Long reportId) {
        return toReport(
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId)));
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
        return boxRepository.findByStatusAndActiveTrueOrderByLockerIdAscBoxNumberAsc("FAULT").stream()
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
        return toReports(reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(userId));
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
        requireTechnician(
                technicianId, ReportCategory.DRONE.equals(report.getCategory()) ? DRONE_TECHNICIAN : LOCKER_TECHNICIAN);
        report.setStatus("IN_PROGRESS");
        report.setAssignedToUserId(technicianId);
        report.setAssignedAt(java.time.LocalDateTime.now());
        LockerReport saved = reportRepository.save(report);
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_CLAIMED,
                "đang được đội bảo trì xử lý");
        publishStaffNotification(
                DomainEventNames.LOCKER_REPORT_ASSIGNED, technicianId, saved.getId(), "LOCKER_REPORT",
                "Admin giao cho bạn phiếu #" + saved.getId() + " — " + saved.getTitle()
                        + " tại tủ " + lockerLabel(saved.getLockerId()) + ".");
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

    /// Admin gia hạn SLA cho phiếu sự cố
    @Transactional
    public LockerReportResponse extendSla(Long reportId, Integer extensionHours, String reason, Long actorUserId) {
        if (extensionHours == null || extensionHours < 1) {
            throw new BusinessException("INVALID_EXTENSION_HOURS", "Thời gian gia hạn phải từ 1 giờ trở lên");
        }
        LockerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("LockerReport", reportId));

        int currentExt = report.getSlaExtendedHours() != null ? report.getSlaExtendedHours() : 0;
        report.setSlaExtendedHours(currentExt + extensionHours);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentDue = report.getSlaDueAt();
        if (currentDue == null) {
            currentDue = report.getCreatedAt() != null ? report.getCreatedAt().plusHours(rules.slaHours()) : now;
        }
        // Nếu đã quá hạn thì tính mốc mới bắt đầu từ thời điểm hiện tại (now)
        LocalDateTime baseDue = now.isAfter(currentDue) ? now : currentDue;
        LocalDateTime newDue = baseDue.plusHours(extensionHours);
        report.setSlaDueAt(newDue);

        if (reason != null && !reason.isBlank()) {
            report.setSlaExtensionReason(reason.trim());
        }

        LockerReport saved = reportRepository.save(report);

        // Tự động ghi nhận kiểm toán vào nhật ký xử lý của phiếu
        try {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
            addRepairLog(
                    reportId,
                    "[GIA HẠN SLA] Hệ thống đã phê duyệt gia hạn thêm +" + extensionHours + " giờ cho sự cố này.\n"
                            + "- Hạn hoàn tất mới: " + newDue.format(dtf) + "\n"
                            + "- Lý do: " + (reason != null && !reason.isBlank() ? reason.trim() : "Admin phê duyệt gia hạn"),
                    actorUserId,
                    List.of(),
                    true);
        } catch (Exception ex) {
            log.warn("Could not log SLA extension audit for report {}: {}", reportId, ex.getMessage());
        }

        return toReport(saved);
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
                .filter(r -> !"RESOLVED".equalsIgnoreCase(r.getStatus()))
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
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_RESOLVED,
                "đã được xử lý xong");
        return saved;
    }

    /// Khôi phục ô/bãi đáp đang có phiếu mở = hoàn tất phiếu đó. Người chưa được giao phiếu nhận
    /// thông báo rõ ràng thay vì lỗi chung chung.
    private void closeReportOfAsset(LockerReport report, Long actorUserId, boolean admin) {
        if (!admin && (actorUserId == null || !actorUserId.equals(report.getAssignedToUserId()))) {
            throw new BusinessException(
                    "REPORT_OPEN",
                    "Đang có phiếu sự cố #" + report.getId() + " — nhận phiếu rồi hoàn tất phiếu để khôi phục",
                    HttpStatus.CONFLICT);
        }
        closeReport(report, actorUserId, null, admin);
    }

    private void assertNotResolved(LockerReport report) {
        if ("RESOLVED".equalsIgnoreCase(report.getStatus())) {
            throw new BusinessException("REPORT_ALREADY_RESOLVED", "Phiếu đã được hoàn tất", HttpStatus.CONFLICT);
        }
    }

    /// Đóng phiếu ⇒ trả tài sản về hoạt động nếu không còn phiếu mở nào khác giữ nó: ô về trạng
    /// thái trước khi hỏng, bãi đáp về OK, tủ do phiếu chặn về ACTIVE, lịch kiểm tra đang chờ
    /// phiếu này thì dời hạn.
    private void restoreAssetsAfterClose(LockerReport report) {
        if (report.getBoxId() != null
                && !reportRepository.existsByBoxIdAndStatusInAndIdNot(
                        report.getBoxId(), OPEN_REPORT_STATUSES, report.getId())) {
            boxRepository.findById(report.getBoxId())
                    .filter(box -> "FAULT".equalsIgnoreCase(box.getStatus()))
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
        String target = StringUtils.hasText(box.getPreFaultStatus()) ? box.getPreFaultStatus() : "AVAILABLE";
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
        return logs.stream()
                .map(log -> toRepairLog(log, attachments.getOrDefault(log.getId(), List.of())))
                .toList();
    }

    private RepairLogResponse toRepairLog(RepairLog log, List<ReportAttachmentResponse> attachments) {
        return new RepairLogResponse(
                log.getId(), log.getReportId(), log.getActorUserId(), log.getNote(), log.getCreatedAt(), attachments);
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
            log.setNote(request.note().trim());
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
        return toSchedule(scheduleRepository.save(schedule));
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

    /// Hoàn tất một lượt kiểm tra định kỳ + lưu biên bản. ĐẠT ⇒ hạn kế tiếp = now + chu kỳ.
    /// KHÔNG ĐẠT (lịch của tủ) ⇒ không dời hạn; tự mở phiếu gắn lịch, giao cho KTV vừa kiểm tra;
    /// phiếu đóng mới dời hạn. Chỉ KTV phụ trách lịch (hoặc ADMIN) được hoàn tất.
    @Transactional
    public MaintenanceScheduleResponse completeSchedule(
            Long id, CompleteScheduleRequest req, Long actorUserId, boolean admin) {
        MaintenanceSchedule schedule =
                scheduleRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("MaintenanceSchedule", id));
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
                    "Lần kiểm tra trước chưa đạt — hoàn tất phiếu #" + pendingReportId + " trước",
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
        return toSchedule(saved);
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
            for (Long technicianId : recipients) {
                publishStaffNotification(
                        DomainEventNames.LOCKER_SCHEDULE_DUE, technicianId, schedule.getId(),
                        "MAINTENANCE_SCHEDULE", message);
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
        } else if (lockerId != null) {
            logs = inspectionLogRepository.findByLockerIdOrderByCreatedAtDesc(lockerId);
        } else if (technicianId != null) {
            logs = inspectionLogRepository.findByTechnicianIdOrderByCreatedAtDesc(technicianId);
        } else {
            logs = inspectionLogRepository.findAllByOrderByCreatedAtDesc();
        }
        return logs.stream().map(this::toInspectionLogResponse).toList();
    }

    private MaintenanceInspectionLogResponse toInspectionLogResponse(MaintenanceInspectionLog log) {
        LockerUnit locker = log.getLockerId() == null ? null : lockerRepository.findById(log.getLockerId()).orElse(null);
        DroneUnit drone = log.getDroneUnitId() == null ? null : droneUnitRepository.findById(log.getDroneUnitId()).orElse(null);
        List<String> photos = StringUtils.hasText(log.getPhotoUrls())
                ? java.util.Arrays.asList(log.getPhotoUrls().split(","))
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
        return toSchedule(scheduleRepository.save(schedule));
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
        boolean due =
                Boolean.TRUE.equals(s.getActive())
                        && s.getNextDueAt() != null
                        && !LocalDateTime.now().isBefore(s.getNextDueAt());
        String techName = s.getAssignedTechnicianId() == null
                ? null
                : technicianNames.computeIfAbsent(s.getAssignedTechnicianId(), this::resolveUserName);
        return new MaintenanceScheduleResponse(
                s.getId(),
                s.getLockerId(),
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
                s.getPendingReportId());
    }

    // ---- Drone fleet (thiết bị bay vật lý, khác ô tủ cellType=DRONE) ----

    @Transactional
    public DroneUnitResponse createDroneUnit(DroneUnitRequest request) {
        lockerRepository
                .findById(request.lockerId())
                .orElseThrow(() -> new NotFoundException("Locker", request.lockerId()));
        if (droneUnitRepository.existsByCode(request.code())) {
            throw new BusinessException("DRONE_CODE_DUPLICATE", "Drone code already exists: " + request.code());
        }
        DroneUnit unit = new DroneUnit();
        unit.setLockerId(request.lockerId());
        unit.setCode(request.code());
        return toDroneUnit(droneUnitRepository.save(unit));
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

    @Transactional
    public DroneUnitResponse updateDroneStatus(Long id, String status, String reason, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        requireDroneOwnership(unit, actorUserId);
        if (DroneStatus.RESERVED.equals(status) || DroneStatus.IN_FLIGHT.equals(status)) {
            throw new BusinessException(
                    "DRONE_STATUS_WORKFLOW_MANAGED",
                    status + " is managed by the dispatch workflow");
        }
        if ((DroneStatus.RESERVED.equals(unit.getStatus()) || DroneStatus.IN_FLIGHT.equals(unit.getStatus()))
                && !DroneStatus.FAULT.equals(status)) {
            throw new BusinessException(
                    "DRONE_ACTIVE_MISSION",
                    "Only FAULT can be reported manually while a drone has an active mission");
        }
        return updateDroneStatusInternal(unit, status, reason, actorUserId, false);
    }

    @Transactional
    public DroneUnitResponse updateDroneStatusInternal(Long id, String status, String reason) {
        return updateDroneStatusInternal(id, status, reason, null, false);
    }

    @Transactional
    public DroneUnitResponse transitionDroneStatusInternal(
            Long id, String expectedStatus, String status, String reason) {
        DroneUnit unit = droneUnitRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("DroneUnit", id));
        if (!expectedStatus.equals(unit.getStatus())) {
            throw new BusinessException(
                    "DRONE_STATUS_CONFLICT",
                    "Drone status changed; expected " + expectedStatus + " but was " + unit.getStatus());
        }
        return updateDroneStatusInternal(unit, status, reason, null, false);
    }

    private DroneUnitResponse updateDroneStatusInternal(
            Long id, String status, String reason, Long actorUserId, boolean requireOwnership) {
        return updateDroneStatusInternal(findDroneUnit(id), status, reason, actorUserId, requireOwnership);
    }

    private DroneUnitResponse updateDroneStatusInternal(
            DroneUnit unit, String status, String reason, Long actorUserId, boolean requireOwnership) {
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
        // #2 Dong bo voi hang doi phieu su co: FAULT mo phieu, hoi phuc thi dong phieu.
        if (fault && !DroneStatus.FAULT.equals(previousStatus)) {
            openDroneFaultReport(saved, normalizedReason, actorUserId);
        } else if (DroneStatus.FAULT.equals(previousStatus) && !fault) {
            resolveDroneFaultReport(saved.getId(), actorUserId);
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
        DroneUnit unit = findDroneUnit(id);
        if (request.lockerId() != null && !request.lockerId().equals(unit.getLockerId())) {
            lockerRepository
                    .findById(request.lockerId())
                    .orElseThrow(() -> new NotFoundException("Locker", request.lockerId()));
            unit.setLockerId(request.lockerId());
        }
        if (StringUtils.hasText(request.code()) && !request.code().equals(unit.getCode())) {
            if (droneUnitRepository.existsByCode(request.code())) {
                throw new BusinessException("DRONE_CODE_DUPLICATE", "Drone code already exists: " + request.code());
            }
            unit.setCode(request.code());
        }
        return toDroneUnit(droneUnitRepository.save(unit));
    }

    /// #4 Ngung hoat dong drone (xoa mem) — an khoi danh sach van hanh, giu lich su log.
    @Transactional
    public void decommissionDrone(Long id, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        unit.setActive(false);
        unit.setAssignedTechnicianId(null);
        unit.setStatus(DroneStatus.MAINTENANCE);
        droneUnitRepository.save(unit);
        appendDroneLog(id, "Drone ngừng hoạt động (decommission)", actorUserId);
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
        reportRepository.save(report);
    }

    /// #2 Khi drone tro lai binh thuong, dong phieu su co dang mo cua no.
    private void resolveDroneFaultReport(Long droneUnitId, Long actorUserId) {
        reportRepository
                .findFirstByDroneUnitIdAndStatusInOrderByCreatedAtDesc(droneUnitId, OPEN_REPORT_STATUSES)
                .ifPresent(
                        report -> {
                            report.setStatus("RESOLVED");
                            report.setResolvedByUserId(actorUserId);
                            report.setResolvedAt(LocalDateTime.now());
                            reportRepository.save(report);
                        });
    }

    @Transactional
    public DroneUnitResponse updateDroneBattery(Long id, Integer batteryPercent, Long actorUserId) {
        DroneUnit unit = findDroneUnit(id);
        requireDroneOwnership(unit, actorUserId);
        unit.setBatteryPercent(batteryPercent);
        if (batteryPercent == 100) {
            unit.setLastChargedAt(LocalDateTime.now());
        }
        DroneUnit saved = droneUnitRepository.save(unit);
        appendDroneLog(saved.getId(), "Cập nhật pin " + batteryPercent + "%", actorUserId);
        return toDroneUnit(saved);
    }

    @Transactional(readOnly = true)
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

    private DroneUnit findDroneUnit(Long id) {
        return droneUnitRepository.findById(id).orElseThrow(() -> new NotFoundException("DroneUnit", id));
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

    /// API công khai (app khách, kiosk): không lộ KTV phụ trách.
    private LockerResponse toResponse(LockerUnit locker) {
        return toResponse(locker, null);
    }

    /// Admin/KTV: kèm KTV phụ trách; `technicianNames` là cache tên trong một lần trả danh sách.
    private LockerResponse toStaffResponse(LockerUnit locker, Map<Long, String> technicianNames) {
        return toResponse(locker, technicianNames);
    }

    private LockerResponse toResponse(LockerUnit locker, Map<Long, String> technicianNames) {
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
                locker.getLandingPad(), locker.getLandingMarkerId(), totalBoxes, availableBoxes,
                technicianId, technicianName);
    }

    private LockerBoxSummary toSummary(LockerBox box) {
        return new LockerBoxSummary(box.getLockerId(), box.getId(), null, box.getBoxNumber(), box.getStatus());
    }

    private CellResponse toCell(LockerBox box) {
        return new CellResponse(
                box.getId(),
                box.getBoxNumber(),
                box.getSize(),
                box.getCellType(),
                box.getRowIndex(),
                box.getColIndex(),
                box.getStatus(),
                box.getFaultReason());
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
                attachmentService.byReportIds(List.of(report.getId())).getOrDefault(report.getId(), List.of()));
    }

    /// Danh sách phiếu: nạp ảnh của mọi phiếu bằng một truy vấn thay vì từng phiếu.
    private List<LockerReportResponse> toReports(List<LockerReport> reports) {
        Map<Long, List<ReportAttachmentResponse>> attachments =
                attachmentService.byReportIds(reports.stream().map(LockerReport::getId).toList());
        return reports.stream()
                .map(report -> toReport(report, attachments.getOrDefault(report.getId(), List.of())))
                .toList();
    }

    private LockerReportResponse toReport(LockerReport report, List<ReportAttachmentResponse> attachments) {
        LockerUnit locker = lockerRepository.findById(report.getLockerId()).orElse(null);
        LockerBox box = report.getBoxId() == null ? null : boxRepository.findById(report.getBoxId()).orElse(null);
        int slaHours = rules.slaHours();
        LocalDateTime slaDueAt = report.getSlaDueAt();
        if (slaDueAt == null && report.getCreatedAt() != null) {
            slaDueAt = report.getCreatedAt().plusHours(slaHours);
        }
        boolean overdue =
                slaDueAt != null
                        && !"RESOLVED".equalsIgnoreCase(report.getStatus())
                        && LocalDateTime.now().isAfter(slaDueAt);
        UserSummary reporter = lookupUserQuietly(report.getUserId());

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
                report.getRoutedToUserId(),
                report.getScheduleId());
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
        if (report.getAssignedToUserId() != null) {
            return;
        }
        String message = "Phiếu #" + report.getId() + " — " + report.getTitle() + " tại tủ "
                + (locker == null ? "#" + report.getLockerId() : locker.getName()) + " đang chờ KTV nhận.";
        List<Long> recipients = report.getRoutedToUserId() != null
                ? List.of(report.getRoutedToUserId())
                : lockerTechnicianIds();
        for (Long technicianId : recipients) {
            publishStaffNotification(
                    DomainEventNames.LOCKER_REPORT_ROUTED, technicianId, report.getId(), "LOCKER_REPORT", message);
        }
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

    /// Thông báo in-app + push cho KTV (notification-service tạo theo `userId` trong payload).
    private void publishStaffNotification(
            String eventType, Long userId, Long referenceId, String referenceType, String message) {
        if (userId == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    eventType,
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
    public Map<String, Object> forceOpen(Long boxId, Long actorUserId) {
        LockerBox box = findBox(boxId);
        var result = iotClient.forceUnlock(new IotClient.ForceUnlockRequest(box.getLockerId(), boxId, actorUserId));
        return result.data();
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
        return toRating(ratingRepository.save(rating));
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
}
