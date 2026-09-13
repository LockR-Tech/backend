package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.*;
import com.huynqb.laundrylocker.locker.model.*;
import com.huynqb.laundrylocker.locker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockerService {

    private static final List<String> OPEN_REPORT_STATUSES = List.of("OPEN", "IN_PROGRESS");
    private static final List<String> SIZE_ORDER = List.of("SMALL", "MEDIUM", "LARGE", "XL");

    private final LockerUnitRepository lockerRepository;
    private final LockerBoxRepository boxRepository;
    private final LockerReportRepository reportRepository;
    private final RepairLogRepository repairLogRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final LockerReportRatingRepository ratingRepository;
    private final DroneUnitRepository droneUnitRepository;
    private final DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    private final IotClient iotClient;
    private final UserClient userClient;

    /// SLA: số giờ tối đa để xử lý một phiếu bảo trì trước khi bị coi là quá hạn.
    @Value("${app.maintenance.sla-hours:4}")
    private int slaHours;

    /// Backstop TTL cho ô RESERVED — order-service sweep mỗi 15 phút đã release
    /// ô khi auto-cancel đơn quá `app.order.auto-cancel-hours` (mặc định 24h);
    /// cửa sổ này nên >= con số đó để không bao giờ release sớm hơn order-service.
    @Value("${app.locker.reserved-ttl-hours:24}")
    private int reservedTtlHours;

    private final RabbitTemplate rabbitTemplate;

    /// #7 Nguong pin toi thieu de cho phep drone cat canh (IN_FLIGHT).
    private static final int DRONE_LOW_BATTERY_PERCENT = 20;

    /// #6 Trang thai hop le cua bai dap drone.
    private static final java.util.Set<String> LANDING_PAD_STATUSES =
            java.util.Set.of("OK", "FAULT", "MAINTENANCE");

    @Transactional
    public LockerResponse createLocker(LockerRequest request) {
        LockerUnit locker = new LockerUnit();
        locker.setStoreId(request.storeId());
        locker.setCode(request.code());
        locker.setName(request.name());
        locker.setStatus(StringUtils.hasText(request.status()) ? request.status() : "ACTIVE");
        locker.setAddress(request.address());
        locker.setLatitude(request.latitude());
        locker.setLongitude(request.longitude());
        return toResponse(lockerRepository.save(locker));
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
        locker.setStatus(StringUtils.hasText(request.status()) ? request.status() : locker.getStatus());
        locker.setAddress(request.address());
        locker.setLatitude(request.latitude());
        locker.setLongitude(request.longitude());
        return toResponse(lockerRepository.save(locker));
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
        locker.setStatus(maintenance ? "MAINTENANCE" : "ACTIVE");
        return toResponse(lockerRepository.save(locker));
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
        if (!"AVAILABLE".equalsIgnoreCase(box.getStatus())) {
            throw new BusinessException("BOX_NOT_AVAILABLE", "Box is not available");
        }
        // Ô hàng 1 (DRONE) chỉ dành cho luồng drone thả hàng; mọi kênh khác bị chặn
        if ("DRONE".equalsIgnoreCase(box.getCellType()) && !"DRONE".equalsIgnoreCase(channel)) {
            throw new BusinessException(
                    "DRONE_CELL_RESTRICTED", "This cell is reserved for drone deliveries only");
        }
        box.setStatus("RESERVED");
        box.setReservedUntil(LocalDateTime.now().plusHours(reservedTtlHours));
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
        LockerBox box = findBox(boxId);
        box.setStatus("FAULT");
        box.setFaultReason(reason);
        boxRepository.save(box);
        LockerReport report = new LockerReport();
        report.setLockerId(box.getLockerId());
        report.setBoxId(box.getId());
        report.setUserId(userId == null ? 0L : userId);
        report.setTitle("Box " + box.getBoxNumber() + " fault");
        report.setDescription(StringUtils.hasText(reason) ? reason : "Reported faulty");
        reportRepository.save(report);
        publishBoxFault(box, reason);
        syncBoxStateQuietly(box, "FAULT");
        return toCell(box);
    }

    @Transactional
    public CellResponse clearFault(Long boxId) {
        LockerBox box = findBox(boxId);
        box.setStatus("AVAILABLE");
        box.setFaultReason(null);
        LockerBox saved = boxRepository.save(box);
        syncBoxStateQuietly(saved, "AVAILABLE");
        return toCell(saved);
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

    /// #6 KTV cap nhat trang thai bao tri bai dap drone: OK / FAULT / MAINTENANCE.
    /// Khi khac OK se tao 1 phieu su co de theo doi (idempotent theo phieu dang mo).
    @Transactional
    public LockerLayoutResponse updateLandingPadStatus(Long lockerId, String status, String reason, Long actorUserId) {
        if (!LANDING_PAD_STATUSES.contains(status)) {
            throw new BusinessException("LANDING_PAD_STATUS_INVALID", "Unknown landing pad status: " + status);
        }
        LockerUnit locker =
                lockerRepository.findById(lockerId).orElseThrow(() -> new NotFoundException("Locker", lockerId));
        if (!Boolean.TRUE.equals(locker.getLandingPad())) {
            throw new BusinessException("LANDING_PAD_ABSENT", "This locker has no drone landing pad");
        }
        String previous = locker.getLandingPadStatus();
        locker.setLandingPadStatus(status);
        lockerRepository.save(locker);
        if (!"OK".equals(status) && !status.equals(previous)) {
            LockerReport report = new LockerReport();
            report.setLockerId(lockerId);
            report.setUserId(actorUserId == null ? 0L : actorUserId);
            report.setTitle("Bãi đáp drone — " + locker.getCode());
            report.setDescription(StringUtils.hasText(reason) ? reason : "Landing pad needs attention: " + status);
            reportRepository.save(report);
        }
        return layout(lockerId);
    }

    @Transactional(readOnly = true)
    public CellResponse findAvailableBox(Long lockerId, String size, String cellType) {
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

    @Transactional(readOnly = true)
    public List<LockerBoxSummary> listBoxes(Long lockerId) {
        return boxRepository.findByLockerId(lockerId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<LockerBoxSummary> listAvailableBoxes(Long lockerId) {
        return boxRepository.findByLockerIdAndStatusAndActiveTrue(lockerId, "AVAILABLE").stream().map(this::toSummary).toList();
    }

    @Transactional
    public LockerReportResponse report(Long lockerId, LockerReportRequest request) {
        LockerReport report = new LockerReport();
        report.setLockerId(lockerId);
        report.setUserId(request.userId());
        report.setTitle(request.title());
        report.setDescription(request.description());
        return toReport(reportRepository.save(report));
    }

    @Transactional
    public LockerReportResponse resolveReport(Long reportId, Long userId) {
        LockerReport report = reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        report.setStatus("RESOLVED");
        report.setResolvedByUserId(userId);
        report.setResolvedAt(java.time.LocalDateTime.now());
        return toReport(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> myReports(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toReport).toList();
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> allReports() {
        return reportRepository.findAll().stream().map(this::toReport).toList();
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
        return reportRepository.findByStatusInOrderByCreatedAtDesc(OPEN_REPORT_STATUSES).stream()
                .map(this::toReport)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LockerReportResponse> assignedReports(Long userId) {
        return reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toReport)
                .toList();
    }

    @Transactional
    public LockerReportResponse claimReport(Long reportId, Long userId) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        if (!"OPEN".equals(report.getStatus())) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "REPORT_NOT_CLAIMABLE", "Report is not open for claiming");
        }
        report.setStatus("IN_PROGRESS");
        report.setAssignedToUserId(userId);
        report.setAssignedAt(java.time.LocalDateTime.now());
        LockerReport saved = reportRepository.save(report);
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_CLAIMED,
                "đang được đội bảo trì xử lý");
        return toReport(saved);
    }

    // Resolving a report tied to a faulty cell also returns the cell to service —
    // the technician confirms the physical repair in one step.
    @Transactional
    public LockerReportResponse resolveReportAndClearFault(Long reportId, Long userId) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
        report.setStatus("RESOLVED");
        report.setResolvedByUserId(userId);
        report.setResolvedAt(java.time.LocalDateTime.now());
        if (report.getBoxId() != null) {
            boxRepository.findById(report.getBoxId())
                    .filter(box -> "FAULT".equals(box.getStatus()))
                    .ifPresent(box -> {
                        box.setStatus("AVAILABLE");
                        box.setFaultReason(null);
                        boxRepository.save(box);
                    });
        }
        LockerReport saved = reportRepository.save(report);
        publishReportNotification(saved, DomainEventNames.LOCKER_REPORT_RESOLVED,
                "đã được xử lý xong");
        return toReport(saved);
    }

    /// L5: kỹ thuật viên thêm 1 dòng nhật ký xử lý vào phiếu bảo trì.
    @Transactional
    public RepairLogResponse addRepairLog(Long reportId, String note, Long actorUserId) {
        LockerReport report =
                reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("Report", reportId));
        RepairLog log = new RepairLog();
        log.setReportId(report.getId());
        log.setActorUserId(actorUserId);
        log.setNote(note);
        return toRepairLog(repairLogRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<RepairLogResponse> repairLogs(Long reportId) {
        return repairLogRepository.findByReportIdOrderByCreatedAtAsc(reportId).stream()
                .map(this::toRepairLog)
                .toList();
    }

    private RepairLogResponse toRepairLog(RepairLog log) {
        return new RepairLogResponse(
                log.getId(), log.getReportId(), log.getActorUserId(), log.getNote(), log.getCreatedAt());
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
        schedule.setTitle(request.title());
        schedule.setIntervalDays(request.intervalDays());
        schedule.setNextDueAt(LocalDateTime.now().plusDays(request.intervalDays()));
        schedule.setActive(true);
        return toSchedule(scheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceScheduleResponse> listSchedules() {
        return scheduleRepository.findByActiveTrueOrderByNextDueAtAsc().stream()
                .map(this::toSchedule)
                .toList();
    }

    /// KTV đã kiểm tra xong lần này: dời mốc đến hạn = now + intervalDays.
    @Transactional
    public MaintenanceScheduleResponse completeSchedule(Long id) {
        MaintenanceSchedule schedule =
                scheduleRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("MaintenanceSchedule", id));
        LocalDateTime now = LocalDateTime.now();
        schedule.setLastDoneAt(now);
        schedule.setNextDueAt(now.plusDays(schedule.getIntervalDays()));
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
                due);
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
        return updateDroneStatusInternal(id, status, reason, actorUserId, true);
    }

    @Transactional
    public DroneUnitResponse updateDroneStatusInternal(Long id, String status, String reason) {
        return updateDroneStatusInternal(id, status, reason, null, false);
    }

    private DroneUnitResponse updateDroneStatusInternal(
            Long id, String status, String reason, Long actorUserId, boolean requireOwnership) {
        if (!DroneStatus.ALL.contains(status)) {
            throw new BusinessException("DRONE_STATUS_INVALID", "Unknown drone status: " + status);
        }
        boolean fault = DroneStatus.FAULT.equals(status);
        String normalizedReason = normalizeText(reason);
        if (fault && !StringUtils.hasText(normalizedReason)) {
            throw new BusinessException("DRONE_FAULT_REASON_REQUIRED", "A reason is required to mark a drone as FAULT");
        }
        DroneUnit unit = findDroneUnit(id);
        if (requireOwnership) {
            requireDroneOwnership(unit, actorUserId);
        }
        // #7 An toan: khong cho cat canh khi pin qua thap.
        if (DroneStatus.IN_FLIGHT.equals(status)
                && unit.getBatteryPercent() != null
                && unit.getBatteryPercent() <= DRONE_LOW_BATTERY_PERCENT) {
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

    private LockerResponse toResponse(LockerUnit locker) {
        int totalBoxes = (int) boxRepository.countByLockerId(locker.getId());
        int availableBoxes =
                (int) boxRepository.countByLockerIdAndStatusAndActiveTrue(locker.getId(), "AVAILABLE");
        return new LockerResponse(
                locker.getId(), locker.getStoreId(), locker.getCode(), locker.getName(), locker.getStatus(),
                locker.getAddress(), locker.getLatitude(), locker.getLongitude(),
                locker.getLandingPad(), locker.getLandingMarkerId(), totalBoxes, availableBoxes);
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
        LockerUnit locker = lockerRepository.findById(report.getLockerId()).orElse(null);
        LockerBox box = report.getBoxId() == null ? null : boxRepository.findById(report.getBoxId()).orElse(null);
        LocalDateTime slaDueAt =
                report.getCreatedAt() == null ? null : report.getCreatedAt().plusHours(slaHours);
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
                reporter == null ? null : reporter.fullName(),
                reporter == null ? null : reporter.phoneNumber());
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
