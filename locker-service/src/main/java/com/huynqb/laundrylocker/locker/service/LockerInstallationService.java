package com.huynqb.laundrylocker.locker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.dto.InstallationCheckRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationPhotosRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationResponse;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentRequest;
import com.huynqb.laundrylocker.locker.model.InstallationCheckItem;
import com.huynqb.laundrylocker.locker.model.InstallationStatus;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerInstallation;
import com.huynqb.laundrylocker.locker.model.LockerInstallationCheck;
import com.huynqb.laundrylocker.locker.model.LockerInstallationPhoto;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.repository.LockerBoxRepository;
import com.huynqb.laundrylocker.locker.repository.LockerInstallationCheckRepository;
import com.huynqb.laundrylocker.locker.repository.LockerInstallationPhotoRepository;
import com.huynqb.laundrylocker.locker.repository.LockerInstallationRepository;
import com.huynqb.laundrylocker.locker.repository.LockerUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.huynqb.laundrylocker.locker.model.InstallationStatus.APPROVED;
import static com.huynqb.laundrylocker.locker.model.InstallationStatus.CANCELLED;
import static com.huynqb.laundrylocker.locker.model.InstallationStatus.IN_PROGRESS;
import static com.huynqb.laundrylocker.locker.model.InstallationStatus.SCHEDULED;
import static com.huynqb.laundrylocker.locker.model.InstallationStatus.SUBMITTED;
import static com.huynqb.laundrylocker.locker.model.LockerInstallationCheck.FAILED;
import static com.huynqb.laundrylocker.locker.model.LockerInstallationCheck.NOT_APPLICABLE;
import static com.huynqb.laundrylocker.locker.model.LockerInstallationCheck.PASSED;
import static com.huynqb.laundrylocker.locker.model.LockerInstallationCheck.PENDING;

/// Lắp đặt & nghiệm thu tủ (F3-G12).
///
/// Admin lập hồ sơ (lịch, KTV lắp đặt, hiện trường, phần cứng) ⇒ tủ PLANNED, khách chưa thấy. KTV bắt đầu
/// ⇒ INSTALLING, làm checklist: các bước máy kiểm được (mạng, gán Pi, cấu hình phần cứng, mở thử ô, nắp) do
/// hệ thống tự xác nhận từ iot-service, KTV chỉ xác nhận các bước làm tay kèm ảnh. Gửi duyệt ⇒ COMMISSIONING;
/// admin duyệt ⇒ ACTIVE, hoặc trả lại kèm lý do / huỷ hồ sơ (tủ về trạng thái trước đó).
@Slf4j
@Service
@RequiredArgsConstructor
public class LockerInstallationService {

    static final Set<InstallationStatus> OPEN = EnumSet.of(SCHEDULED, IN_PROGRESS, SUBMITTED);
    /// Ảnh hiện trường chung (không gắn bước) tối thiểu trước khi gửi duyệt.
    static final int MIN_SITE_PHOTOS = 2;
    static final int MAX_PHOTOS = 60;
    static final String VERIFIED_BY_SYSTEM = "SYSTEM";
    static final String VERIFIED_BY_TECHNICIAN = "TECHNICIAN";
    private static final Set<String> COMMAND_OK = Set.of("ACCEPTED", "SUCCESS", "OPEN");
    private static final Set<String> BOX_ACTIONS = Set.of("BOX_OPEN", "BOX_OPEN_TEST");
    private static final Set<String> ORDER_HELD_BOX_STATUSES = Set.of("RESERVED", "OCCUPIED");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final LockerInstallationRepository installations;
    private final LockerInstallationCheckRepository checks;
    private final LockerInstallationPhotoRepository photos;
    private final LockerUnitRepository lockers;
    private final LockerBoxRepository boxes;
    private final LockerService lockerService;
    private final IotClient iotClient;
    private final CloudinaryMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

    // ─── Admin ───────────────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public InstallationResponse create(Long lockerId, InstallationRequest request, Long adminId) {
        LockerUnit locker = lockers.findById(lockerId).orElseThrow(() -> new NotFoundException("Locker", lockerId));
        if (installations.existsByLockerIdAndStatusIn(lockerId, OPEN)) {
            throw new BusinessException("INSTALLATION_ALREADY_OPEN",
                    "Tủ này đang có hồ sơ lắp đặt chưa đóng", HttpStatus.CONFLICT);
        }
        List<String> held = boxes.findByLockerId(lockerId).stream()
                .filter(box -> box.getStatus() != null
                        && ORDER_HELD_BOX_STATUSES.contains(box.getStatus().toUpperCase(Locale.ROOT)))
                .map(box -> "#" + box.getBoxNumber())
                .toList();
        if (!held.isEmpty()) {
            throw new BusinessException("INSTALLATION_LOCKER_IN_USE",
                    "Tủ đang có ô giữ hàng / giữ chỗ (" + String.join(", ", held)
                            + ") — xử lý xong các đơn rồi mới lập hồ sơ lắp đặt",
                    HttpStatus.CONFLICT);
        }
        if (!Objects.equals(locker.getAssignedTechnicianId(), request.technicianId())) {
            // Kiểm vai trò LOCKER_TECHNICIAN; KTV lắp đặt thành KTV phụ trách để điều khiển được tủ.
            lockerService.assignLockerTechnician(lockerId, request.technicianId());
        }
        LockerInstallation installation = new LockerInstallation();
        installation.setLockerId(lockerId);
        installation.setStatus(SCHEDULED);
        installation.setPreviousLockerStatus(
                LockerService.isInstallationStatus(locker.getStatus()) ? "INACTIVE" : locker.getStatus());
        installation.setCreatedBy(adminId);
        applyRequest(installation, request);
        LockerInstallation saved = installations.save(installation);
        List<LockerInstallationCheck> rows = Arrays.stream(InstallationCheckItem.values()).map(item -> {
            LockerInstallationCheck check = new LockerInstallationCheck();
            check.setInstallationId(saved.getId());
            check.setItem(item);
            check.setStatus(PENDING);
            return check;
        }).toList();
        checks.saveAll(rows);
        lockerService.applyInstallationStatus(lockerId, SCHEDULED.lockerStatus());
        notifyAssigned(saved, locker);
        return toResponse(saved, null, true);
    }

    @Transactional
    public InstallationResponse update(Long id, InstallationRequest request, Long adminId) {
        LockerInstallation installation = load(id);
        requireOpen(installation);
        Long previousTechnician = installation.getTechnicianId();
        if (!Objects.equals(previousTechnician, request.technicianId())) {
            lockerService.assignLockerTechnician(installation.getLockerId(), request.technicianId());
        }
        applyRequest(installation, request);
        LockerInstallation saved = installations.save(installation);
        if (!Objects.equals(previousTechnician, request.technicianId())) {
            notifyAssigned(saved, lockers.findById(saved.getLockerId()).orElse(null));
        }
        return toResponse(saved, null, true);
    }

    @Transactional
    public InstallationResponse approve(Long id, Long adminId) {
        LockerInstallation installation = load(id);
        requireStatus(installation, SUBMITTED, "Chỉ duyệt được hồ sơ đã gửi duyệt");
        IotClient.CommissioningStatus status = refreshAuto(installation);
        if (status == null) {
            throw iotUnavailable();
        }
        List<String> missing = missing(installation);
        if (!missing.isEmpty()) {
            throw new BusinessException("INSTALLATION_INCOMPLETE",
                    "Chưa duyệt được: " + String.join("; ", missing), HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        installation.setStatus(APPROVED);
        installation.setReviewedBy(adminId);
        installation.setReviewedAt(now);
        installation.setApprovedAt(now);
        LockerInstallation saved = installations.save(installation);
        lockerService.applyInstallationStatus(saved.getLockerId(), APPROVED.lockerStatus());
        notifyReviewed(saved, "Hồ sơ lắp đặt tủ " + lockerName(saved) + " đã được duyệt — tủ bắt đầu hoạt động.");
        return toResponse(saved, status, true);
    }

    @Transactional
    public InstallationResponse returnToTechnician(Long id, String reason, Long adminId) {
        LockerInstallation installation = load(id);
        requireStatus(installation, SUBMITTED, "Chỉ trả lại được hồ sơ đã gửi duyệt");
        installation.setStatus(IN_PROGRESS);
        installation.setReturnReason(reason.trim());
        installation.setReviewedBy(adminId);
        installation.setReviewedAt(LocalDateTime.now());
        LockerInstallation saved = installations.save(installation);
        lockerService.applyInstallationStatus(saved.getLockerId(), IN_PROGRESS.lockerStatus());
        notifyReviewed(saved, "Hồ sơ lắp đặt tủ " + lockerName(saved) + " cần bổ sung: " + reason.trim());
        return toResponse(saved, null, true);
    }

    @Transactional
    public InstallationResponse cancel(Long id, String reason, Long adminId) {
        LockerInstallation installation = load(id);
        requireOpen(installation);
        LocalDateTime now = LocalDateTime.now();
        installation.setStatus(CANCELLED);
        installation.setCancelReason(reason.trim());
        installation.setCancelledAt(now);
        installation.setReviewedBy(adminId);
        installation.setReviewedAt(now);
        LockerInstallation saved = installations.save(installation);
        String restore = saved.getPreviousLockerStatus();
        if (!StringUtils.hasText(restore) || LockerService.isInstallationStatus(restore)) {
            restore = "INACTIVE";
        }
        lockerService.applyInstallationStatus(saved.getLockerId(), restore);
        notifyReviewed(saved, "Hồ sơ lắp đặt tủ " + lockerName(saved) + " đã bị huỷ: " + reason.trim());
        return toResponse(saved, null, true);
    }

    @Transactional(readOnly = true)
    public List<InstallationResponse> listForAdmin(String status) {
        List<LockerInstallation> rows;
        if (!StringUtils.hasText(status)) {
            rows = installations.findAllByOrderByIdDesc();
        } else {
            Set<InstallationStatus> wanted = Arrays.stream(status.split(","))
                    .map(String::trim).filter(StringUtils::hasText)
                    .map(value -> parseStatus(value))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(InstallationStatus.class)));
            rows = installations.findByStatusInOrderByIdDesc(wanted);
        }
        return rows.stream().map(row -> toResponse(row, null, true)).toList();
    }

    /// Hồ sơ mới nhất của tủ (đang mở hoặc đã đóng); chưa từng lập thì null.
    @Transactional
    public InstallationResponse latestForLocker(Long lockerId) {
        return installations.findFirstByLockerIdOrderByIdDesc(lockerId)
                .map(installation -> detail(installation.getId(), null, true))
                .orElse(null);
    }

    // ─── KTV lắp đặt ───────────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InstallationResponse> listForTechnician(Long technicianId) {
        return installations.findByTechnicianIdAndStatusInOrderByScheduledAtAscIdAsc(technicianId, OPEN).stream()
                .map(row -> toResponse(row, null, true))
                .toList();
    }

    /// Chi tiết hồ sơ; đang lắp / chờ duyệt thì đọc lại dữ liệu tủ để cập nhật các bước tự động.
    @Transactional
    public InstallationResponse detail(Long id, Long actorId, boolean admin) {
        LockerInstallation installation = load(id);
        requireAssigneeOrAdmin(installation, actorId, admin);
        return openView(installation);
    }

    /// Hồ sơ kèm dữ liệu tủ (bộ điều khiển, online) khi đang lắp / chờ duyệt. Dùng cho mọi phản hồi của thao tác
    /// KTV: app thay cả hồ sơ bằng phản hồi, thiếu bộ điều khiển là app tưởng tủ chưa gán Pi (ẩn nút mở thử ô).
    private InstallationResponse openView(LockerInstallation installation) {
        if (installation.getStatus() == IN_PROGRESS || installation.getStatus() == SUBMITTED) {
            IotClient.CommissioningStatus status = refreshAuto(installation);
            return toResponse(installation, status, status != null);
        }
        return toResponse(installation, null, true);
    }

    @Transactional
    public InstallationResponse start(Long id, Long actorId, boolean admin) {
        LockerInstallation installation = load(id);
        requireAssigneeOrAdmin(installation, actorId, admin);
        if (installation.getStatus() == IN_PROGRESS) {
            return detail(id, actorId, admin);
        }
        requireStatus(installation, SCHEDULED, "Hồ sơ không ở trạng thái chờ lắp đặt");
        installation.setStatus(IN_PROGRESS);
        installation.setStartedAt(LocalDateTime.now());
        LockerInstallation saved = installations.save(installation);
        lockerService.applyInstallationStatus(saved.getLockerId(), IN_PROGRESS.lockerStatus());
        IotClient.CommissioningStatus status = refreshAuto(saved);
        return toResponse(saved, status, status != null);
    }

    /// KTV xác nhận một bước làm tay (hoặc đánh dấu "không áp dụng" ở bước cho phép).
    @Transactional
    public InstallationResponse updateCheck(Long id, String itemValue, InstallationCheckRequest request,
                                            Long actorId, boolean admin) {
        LockerInstallation installation = load(id);
        requireAssigneeOrAdmin(installation, actorId, admin);
        requireStatus(installation, IN_PROGRESS, "Chỉ cập nhật checklist khi đang lắp đặt");
        InstallationCheckItem item = parseItem(itemValue);
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        String note = StringUtils.hasText(request.note()) ? request.note().trim() : null;
        if (!Set.of(PASSED, FAILED, NOT_APPLICABLE, PENDING).contains(status)) {
            throw new BusinessException("INSTALLATION_CHECK_STATUS_INVALID", "Trạng thái bước không hợp lệ: " + status);
        }
        if (item.auto() && !(item.notApplicableAllowed() && (NOT_APPLICABLE.equals(status) || PENDING.equals(status)))) {
            throw new BusinessException("INSTALLATION_CHECK_AUTO",
                    "Bước \"" + item.label() + "\" do hệ thống tự xác nhận từ dữ liệu của tủ", HttpStatus.CONFLICT);
        }
        if (NOT_APPLICABLE.equals(status) && !item.notApplicableAllowed()) {
            throw new BusinessException("INSTALLATION_CHECK_NA_NOT_ALLOWED",
                    "Bước \"" + item.label() + "\" bắt buộc, không đánh dấu không áp dụng được");
        }
        if ((FAILED.equals(status) || NOT_APPLICABLE.equals(status)) && note == null) {
            throw new BusinessException("INSTALLATION_CHECK_NOTE_REQUIRED", "Nhập lý do cho bước \"" + item.label() + "\"");
        }
        if (PASSED.equals(status) && item.photoRequired() && photoCount(installation.getId(), item) == 0) {
            throw new BusinessException("INSTALLATION_PHOTO_REQUIRED",
                    "Chụp ít nhất 1 ảnh cho bước \"" + item.label() + "\" trước khi xác nhận");
        }
        LockerInstallationCheck check = checksOf(installation.getId()).get(item);
        check.setStatus(status);
        check.setNote(note);
        check.setVerifiedBy(PENDING.equals(status) ? null : VERIFIED_BY_TECHNICIAN);
        check.setCheckedBy(PENDING.equals(status) ? null : actorId);
        check.setCheckedAt(PENDING.equals(status) ? null : LocalDateTime.now());
        checks.save(check);
        return openView(installation);
    }

    @Transactional
    public InstallationResponse addPhotos(Long id, InstallationPhotosRequest request, Long actorId, boolean admin) {
        LockerInstallation installation = load(id);
        requireAssigneeOrAdmin(installation, actorId, admin);
        if (admin) {
            requireOpen(installation);
        } else {
            requireStatus(installation, IN_PROGRESS, "Chỉ thêm ảnh khi đang lắp đặt");
        }
        InstallationCheckItem item = StringUtils.hasText(request.item()) ? parseItem(request.item()) : null;
        List<ReportAttachmentRequest> uploads = request.photos().stream().filter(Objects::nonNull).toList();
        if (photos.countByInstallationId(id) + uploads.size() > MAX_PHOTOS) {
            throw new BusinessException("ATTACHMENT_LIMIT_EXCEEDED",
                    "Một hồ sơ lắp đặt có tối đa " + MAX_PHOTOS + " ảnh");
        }
        Set<String> seen = new HashSet<>();
        List<LockerInstallationPhoto> rows = new ArrayList<>();
        for (ReportAttachmentRequest upload : uploads) {
            VerifiedMedia media = mediaStorage.verify(upload.media(), MediaPurpose.INSTALLATION_PHOTO, actorId);
            if (!seen.add(media.publicId()) || photos.existsByPublicId(media.publicId())) {
                throw new BusinessException("ATTACHMENT_DUPLICATE",
                        "Ảnh đã được gắn: " + media.publicId(), HttpStatus.CONFLICT);
            }
            LockerInstallationPhoto photo = new LockerInstallationPhoto();
            photo.setInstallationId(id);
            photo.setItem(item);
            photo.setPublicId(media.publicId());
            photo.setSecureUrl(media.secureUrl());
            photo.setFormat(media.format());
            photo.setBytes(media.bytes());
            photo.setWidth(media.width());
            photo.setHeight(media.height());
            photo.setCaption(StringUtils.hasText(upload.caption()) ? upload.caption().trim() : null);
            photo.setLatitude(upload.latitude());
            photo.setLongitude(upload.longitude());
            photo.setCapturedAt(ReportAttachmentService.parseCapturedAt(upload.capturedAt()));
            photo.setUploadedByUserId(actorId);
            rows.add(photo);
        }
        photos.saveAll(rows);
        return openView(installation);
    }

    @Transactional
    public InstallationResponse deletePhoto(Long id, Long photoId, Long actorId, boolean admin) {
        LockerInstallation installation = load(id);
        requireAssigneeOrAdmin(installation, actorId, admin);
        LockerInstallationPhoto photo = photos.findById(photoId)
                .filter(p -> p.getInstallationId().equals(id))
                .orElseThrow(() -> new NotFoundException("InstallationPhoto", photoId));
        if (admin) {
            requireOpen(installation);
        } else {
            requireStatus(installation, IN_PROGRESS, "Chỉ xoá ảnh khi đang lắp đặt");
            if (!Objects.equals(photo.getUploadedByUserId(), actorId)) {
                throw new BusinessException("ATTACHMENT_DELETE_FORBIDDEN",
                        "Chỉ xoá được ảnh mình chụp", HttpStatus.FORBIDDEN);
            }
        }
        photos.delete(photo);
        mediaStorage.deleteAfterCommit(photo.getPublicId());
        InstallationCheckItem item = photo.getItem();
        if (item != null && item.photoRequired() && photoCount(id, item) == 0) {
            // Bước bắt buộc ảnh không còn ảnh ⇒ không còn được tính là đạt.
            LockerInstallationCheck check = checksOf(id).get(item);
            if (PASSED.equals(check.getStatus())) {
                check.setStatus(PENDING);
                check.setVerifiedBy(null);
                check.setCheckedBy(null);
                check.setCheckedAt(null);
                checks.save(check);
            }
        }
        return openView(installation);
    }

    @Transactional
    public InstallationResponse submit(Long id, Long actorId, boolean admin) {
        LockerInstallation installation = load(id);
        requireAssigneeOrAdmin(installation, actorId, admin);
        requireStatus(installation, IN_PROGRESS, "Chỉ gửi duyệt được hồ sơ đang lắp đặt");
        IotClient.CommissioningStatus status = refreshAuto(installation);
        if (status == null) {
            throw iotUnavailable();
        }
        List<String> missing = missing(installation);
        if (!missing.isEmpty()) {
            throw new BusinessException("INSTALLATION_INCOMPLETE",
                    "Chưa gửi duyệt được: " + String.join("; ", missing), HttpStatus.CONFLICT);
        }
        installation.setStatus(SUBMITTED);
        installation.setSubmittedAt(LocalDateTime.now());
        installation.setReturnReason(null);
        LockerInstallation saved = installations.save(installation);
        lockerService.applyInstallationStatus(saved.getLockerId(), SUBMITTED.lockerStatus());
        return toResponse(saved, status, true);
    }

    // ─── Hệ thống tự xác nhận các bước máy kiểm được ───────────────────────────────────────────────────────

    /// Đọc dữ liệu tủ từ iot-service và cập nhật các bước tự động. Không đọc được thì giữ kết quả cũ, trả null.
    IotClient.CommissioningStatus refreshAuto(LockerInstallation installation) {
        IotClient.CommissioningStatus status;
        try {
            var response = iotClient.commissioning(installation.getLockerId(),
                    installation.getStartedAt() == null ? null : installation.getStartedAt().toString());
            status = response == null ? null : response.data();
        } catch (Exception ex) {
            log.warn("Could not read commissioning data of locker {}: {}", installation.getLockerId(), ex.getMessage());
            return null;
        }
        if (status == null) {
            return null;
        }
        Map<InstallationCheckItem, LockerInstallationCheck> byItem = checksOf(installation.getId());
        List<LockerInstallationCheck> changed = new ArrayList<>();
        IotClient.GatewayDeviceResponse controller = status.controller();
        String mac = controller == null ? null : controller.macAddress();

        // Mạng: bộ điều khiển của tủ gửi heartbeat trong cửa sổ online.
        if (controller == null) {
            setAuto(byItem.get(InstallationCheckItem.NETWORK), PENDING,
                    "Chưa gán bộ điều khiển cho tủ nên chưa kiểm được kết nối", null, changed);
        } else if (controller.online()) {
            setAuto(byItem.get(InstallationCheckItem.NETWORK), PASSED, "Bộ điều khiển " + mac + " đang online", null, changed);
        } else {
            setAuto(byItem.get(InstallationCheckItem.NETWORK), PENDING,
                    "Bộ điều khiển " + mac + " đang mất kết nối — kiểm tra nguồn và mạng", null, changed);
        }

        // Gán Pi: Pi báo đã cài sơ đồ ô (và mở thử từng ô) thành công.
        String setup = upper(controller == null ? null : controller.setupStatus());
        if (controller == null) {
            setAuto(byItem.get(InstallationCheckItem.CONTROLLER), PENDING,
                    "Chưa gán bộ điều khiển — quét QR trên màn kiosk của tủ", null, changed);
        } else if ("COMPLETED".equals(setup)) {
            setAuto(byItem.get(InstallationCheckItem.CONTROLLER), PASSED, "Đã cài sơ đồ ô vào " + mac, null, changed);
        } else if ("PARTIAL".equals(setup) || "FAILED".equals(setup)) {
            setAuto(byItem.get(InstallationCheckItem.CONTROLLER), FAILED,
                    "Cài sơ đồ ô " + setup + " — xem kết quả từng ô rồi gán lại", null, changed);
        } else {
            setAuto(byItem.get(InstallationCheckItem.CONTROLLER), PENDING,
                    "Đang cài sơ đồ ô vào " + mac + (setup == null ? "" : " (" + setup + ")"), null, changed);
        }

        // Cấu hình phần cứng: admin áp từ web, tủ xác nhận.
        String hardware = upper(status.hardwareApplyStatus());
        if (hardware == null) {
            setAuto(byItem.get(InstallationCheckItem.HARDWARE_CONFIG), PENDING,
                    "Chờ admin áp cấu hình phần cứng (web admin › Điều khiển tủ)", null, changed);
        } else if (COMMAND_OK.contains(hardware)) {
            setAuto(byItem.get(InstallationCheckItem.HARDWARE_CONFIG), PASSED,
                    "Tủ đã nhận cấu hình" + atStorageTime(status.hardwareAppliedAt(), ZoneId.systemDefault()),
                    null, changed);
        } else {
            setAuto(byItem.get(InstallationCheckItem.HARDWARE_CONFIG), FAILED,
                    "Áp cấu hình " + hardware + (StringUtils.hasText(status.hardwareApplyMessage())
                            ? ": " + status.hardwareApplyMessage() : ""), null, changed);
        }

        refreshBoxTest(installation, status, byItem.get(InstallationCheckItem.BOX_TEST), changed);
        refreshLid(status, byItem.get(InstallationCheckItem.LID), changed);
        if (!changed.isEmpty()) {
            checks.saveAll(changed);
        }
        return status;
    }

    private void refreshBoxTest(LockerInstallation installation, IotClient.CommissioningStatus status,
                                LockerInstallationCheck check, List<LockerInstallationCheck> changed) {
        List<LockerBox> active = boxes.findByLockerId(installation.getLockerId()).stream()
                .filter(box -> !Boolean.FALSE.equals(box.getActive()))
                .sorted(Comparator.comparing(LockerBox::getBoxNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (active.isEmpty()) {
            setAuto(check, FAILED, "Tủ chưa có ô nào — tạo ô trên web admin trước", null, changed);
            return;
        }
        Map<Long, IotClient.CommandResult> latest = new LinkedHashMap<>();
        for (IotClient.CommandResult command : commands(status)) {
            if (command.boxId() != null && BOX_ACTIONS.contains(upper(command.action()))) {
                latest.put(command.boxId(), command);   // danh sách cũ → mới: lệnh sau đè lệnh trước
            }
        }
        List<Map<String, Object>> detail = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int passed = 0;
        for (LockerBox box : active) {
            IotClient.CommandResult command = latest.get(box.getId());
            String result = command == null ? PENDING : COMMAND_OK.contains(upper(command.status())) ? PASSED : FAILED;
            if (PASSED.equals(result)) {
                passed++;
            } else if (FAILED.equals(result)) {
                failed.add("#" + box.getBoxNumber());
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("boxId", box.getId());
            row.put("boxNumber", box.getBoxNumber());
            row.put("status", result);
            row.put("command", command == null ? null : upper(command.status()));
            row.put("message", command == null ? null : command.message());
            row.put("testedAt", command == null || command.occurredAt() == null ? null : command.occurredAt().toString());
            detail.add(row);
        }
        String json = toJson(detail);
        if (!failed.isEmpty()) {
            setAuto(check, FAILED, "Mở thử lỗi ở ô " + String.join(", ", failed) + " — sửa rồi mở thử lại", json, changed);
        } else if (passed == active.size()) {
            setAuto(check, PASSED, "Đã mở thử thành công " + passed + " ô", json, changed);
        } else {
            setAuto(check, PENDING, "Đã mở thử " + passed + "/" + active.size() + " ô", json, changed);
        }
    }

    private void refreshLid(IotClient.CommissioningStatus status, LockerInstallationCheck check,
                            List<LockerInstallationCheck> changed) {
        if (NOT_APPLICABLE.equals(check.getStatus()) && VERIFIED_BY_TECHNICIAN.equals(check.getVerifiedBy())) {
            return;   // KTV đã ghi tủ không có nắp / bãi đáp
        }
        IotClient.CommandResult open = null;
        IotClient.CommandResult close = null;
        for (IotClient.CommandResult command : commands(status)) {
            String action = upper(command.action());
            if ("LID_OPEN".equals(action)) {
                open = command;
            } else if ("LID_CLOSE".equals(action)) {
                close = command;
            }
        }
        boolean openOk = open != null && COMMAND_OK.contains(upper(open.status()));
        boolean closeOk = close != null && COMMAND_OK.contains(upper(close.status()));
        if (openOk && closeOk) {
            setAuto(check, PASSED, "Nắp mở và đóng thành công", null, changed);
        } else if ((open != null && !openOk) || (close != null && !closeOk)) {
            // Kèm lý do tủ báo (vd. "Trục 1 chưa bật trên Pi") — trước đây KTV chỉ thấy "FAILED".
            setAuto(check, FAILED, "Lệnh nắp lỗi (mở: " + lidResult(open) + "; đóng: " + lidResult(close) + ")",
                    null, changed);
        } else {
            setAuto(check, PENDING, "Thử mở rồi đóng nắp trên app (tủ không có nắp thì đánh dấu không áp dụng)",
                    null, changed);
        }
    }

    /// "SUCCESS", "FAILED — Trục 1 chưa bật trên Pi" hoặc "chưa thử".
    private static String lidResult(IotClient.CommandResult command) {
        if (command == null) {
            return "chưa thử";
        }
        String status = Objects.toString(upper(command.status()), "?");
        String message = command.message() == null ? "" : command.message().trim();
        return COMMAND_OK.contains(status) || message.isEmpty() ? status : status + " — " + message;
    }

    private static List<IotClient.CommandResult> commands(IotClient.CommissioningStatus status) {
        return status.commands() == null ? List.of() : status.commands();
    }

    private static void setAuto(LockerInstallationCheck check, String status, String note, String detail,
                                List<LockerInstallationCheck> changed) {
        if (Objects.equals(check.getStatus(), status) && Objects.equals(check.getNote(), note)
                && Objects.equals(check.getDetail(), detail) && VERIFIED_BY_SYSTEM.equals(check.getVerifiedBy())) {
            return;
        }
        check.setStatus(status);
        check.setNote(note);
        check.setDetail(detail);
        check.setVerifiedBy(VERIFIED_BY_SYSTEM);
        check.setCheckedBy(null);
        check.setCheckedAt(LocalDateTime.now());
        changed.add(check);
    }

    /// Những gì còn thiếu để gửi duyệt / duyệt.
    List<String> missing(LockerInstallation installation) {
        List<String> missing = new ArrayList<>();
        Map<InstallationCheckItem, LockerInstallationCheck> byItem = checksOf(installation.getId());
        for (InstallationCheckItem item : InstallationCheckItem.values()) {
            LockerInstallationCheck check = byItem.get(item);
            String status = check == null ? PENDING : check.getStatus();
            if (!PASSED.equals(status) && !NOT_APPLICABLE.equals(status)) {
                missing.add("bước \"" + item.label() + "\" chưa đạt");
            } else if (PASSED.equals(status) && item.photoRequired() && photoCount(installation.getId(), item) == 0) {
                missing.add("bước \"" + item.label() + "\" thiếu ảnh");
            }
        }
        long sitePhotos = photos.findByInstallationIdOrderByIdAsc(installation.getId()).stream()
                .filter(photo -> photo.getItem() == null)
                .count();
        if (sitePhotos < MIN_SITE_PHOTOS) {
            missing.add("cần ít nhất " + MIN_SITE_PHOTOS + " ảnh hiện trường (đang có " + sitePhotos + ")");
        }
        return missing;
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────────────────────────────────

    private LockerInstallation load(Long id) {
        return installations.findById(id).orElseThrow(() -> new NotFoundException("LockerInstallation", id));
    }

    private Map<InstallationCheckItem, LockerInstallationCheck> checksOf(Long installationId) {
        Map<InstallationCheckItem, LockerInstallationCheck> byItem = new EnumMap<>(InstallationCheckItem.class);
        for (LockerInstallationCheck check : checks.findByInstallationIdOrderByIdAsc(installationId)) {
            byItem.put(check.getItem(), check);
        }
        for (InstallationCheckItem item : InstallationCheckItem.values()) {
            byItem.computeIfAbsent(item, missingItem -> {
                LockerInstallationCheck check = new LockerInstallationCheck();
                check.setInstallationId(installationId);
                check.setItem(missingItem);
                check.setStatus(PENDING);
                return check;
            });
        }
        return byItem;
    }

    private long photoCount(Long installationId, InstallationCheckItem item) {
        return photos.findByInstallationIdOrderByIdAsc(installationId).stream()
                .filter(photo -> photo.getItem() == item)
                .count();
    }

    private static void requireAssigneeOrAdmin(LockerInstallation installation, Long actorId, boolean admin) {
        if (!admin && !Objects.equals(installation.getTechnicianId(), actorId)) {
            throw new BusinessException("INSTALLATION_NOT_ASSIGNED",
                    "Bạn không được giao lắp đặt tủ này", HttpStatus.FORBIDDEN);
        }
    }

    private static void requireOpen(LockerInstallation installation) {
        if (!installation.getStatus().isOpen()) {
            throw new BusinessException("INSTALLATION_CLOSED",
                    "Hồ sơ lắp đặt đã " + (installation.getStatus() == APPROVED ? "duyệt" : "huỷ"), HttpStatus.CONFLICT);
        }
    }

    private static void requireStatus(LockerInstallation installation, InstallationStatus expected, String message) {
        if (installation.getStatus() != expected) {
            throw new BusinessException("INSTALLATION_STATUS_INVALID", message, HttpStatus.CONFLICT);
        }
    }

    private static BusinessException iotUnavailable() {
        return new BusinessException("IOT_SERVICE_UNAVAILABLE",
                "Chưa đọc được dữ liệu của tủ để kiểm tra, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static InstallationCheckItem parseItem(String value) {
        try {
            return InstallationCheckItem.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException("INSTALLATION_CHECK_ITEM_INVALID", "Bước nghiệm thu không hợp lệ: " + value);
        }
    }

    private static InstallationStatus parseStatus(String value) {
        try {
            return InstallationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException("INSTALLATION_STATUS_INVALID", "Trạng thái hồ sơ không hợp lệ: " + value);
        }
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /// Mốc đã theo giờ Việt Nam (lịch lắp admin nhập).
    private static String at(LocalDateTime time) {
        return time == null ? "" : " lúc " + time.format(TIME);
    }

    /// Mốc server tự ghi bằng `LocalDateTime.now()` theo giờ JVM (UTC trên container) ⇒ đổi sang giờ Việt Nam
    /// trước khi đưa vào câu chữ (trước đây ghi "lúc 16:25" cho 23:25).
    static String atStorageTime(LocalDateTime time, ZoneId storageZone) {
        return time == null ? "" : at(time.atZone(storageZone)
                .withZoneSameInstant(BusinessTime.DEFAULT_BUSINESS_ZONE).toLocalDateTime());
    }

    private static void applyRequest(LockerInstallation installation, InstallationRequest request) {
        installation.setTechnicianId(request.technicianId());
        installation.setScheduledAt(request.scheduledAt());
        installation.setSiteContactName(trim(request.siteContactName()));
        installation.setSiteContactPhone(trim(request.siteContactPhone()));
        installation.setPowerSource(trim(request.powerSource()));
        installation.setNetworkType(StringUtils.hasText(request.networkType())
                ? request.networkType().trim().toUpperCase(Locale.ROOT) : null);
        installation.setNetworkNote(trim(request.networkNote()));
        installation.setCabinetModel(trim(request.cabinetModel()));
        installation.setCabinetSerial(trim(request.cabinetSerial()));
        installation.setNotes(trim(request.notes()));
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String lockerName(LockerInstallation installation) {
        return lockers.findById(installation.getLockerId()).map(LockerUnit::getName)
                .orElse("#" + installation.getLockerId());
    }

    private void notifyAssigned(LockerInstallation installation, LockerUnit locker) {
        String name = locker == null ? "#" + installation.getLockerId() : locker.getName();
        lockerService.notifyInstallation(DomainEventNames.LOCKER_INSTALLATION_ASSIGNED,
                installation.getTechnicianId(), installation.getId(),
                "Bạn được giao lắp đặt tủ " + name + at(installation.getScheduledAt()) + ".");
    }

    private void notifyReviewed(LockerInstallation installation, String message) {
        lockerService.notifyInstallation(DomainEventNames.LOCKER_INSTALLATION_REVIEWED,
                installation.getTechnicianId(), installation.getId(), message);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private JsonNode readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private InstallationResponse toResponse(LockerInstallation installation, IotClient.CommissioningStatus status,
                                            boolean iotAvailable) {
        LockerUnit locker = lockers.findById(installation.getLockerId()).orElse(null);
        List<LockerInstallationPhoto> photoRows = photos.findByInstallationIdOrderByIdAsc(installation.getId());
        Map<InstallationCheckItem, LockerInstallationCheck> byItem = checksOf(installation.getId());
        List<InstallationResponse.Check> checkViews = Arrays.stream(InstallationCheckItem.values()).map(item -> {
            LockerInstallationCheck check = byItem.get(item);
            int count = (int) photoRows.stream().filter(photo -> photo.getItem() == item).count();
            return new InstallationResponse.Check(item.name(), item.label(), check.getStatus(), item.auto(),
                    item.photoRequired(), item.notApplicableAllowed(), check.getVerifiedBy(), check.getNote(),
                    readJson(check.getDetail()), check.getCheckedBy(), check.getCheckedAt(), count);
        }).toList();
        List<InstallationResponse.Photo> photoViews = photoRows.stream()
                .map(photo -> new InstallationResponse.Photo(photo.getId(),
                        photo.getItem() == null ? null : photo.getItem().name(), photo.getSecureUrl(),
                        mediaStorage.thumbnailUrl(photo.getSecureUrl()), photo.getCaption(), photo.getLatitude(),
                        photo.getLongitude(), photo.getCapturedAt(), photo.getUploadedByUserId(), photo.getCreatedAt()))
                .toList();
        List<String> missing = installation.getStatus().isOpen() ? missing(installation) : List.of();
        IotClient.GatewayDeviceResponse controller = status == null ? null : status.controller();
        return new InstallationResponse(
                installation.getId(), installation.getLockerId(),
                locker == null ? null : locker.getCode(), locker == null ? null : locker.getName(),
                locker == null ? null : locker.getStatus(), installation.getStatus().name(),
                installation.getTechnicianId(), installation.getScheduledAt(), installation.getSiteContactName(),
                installation.getSiteContactPhone(), installation.getPowerSource(), installation.getNetworkType(),
                installation.getNetworkNote(), installation.getCabinetModel(), installation.getCabinetSerial(),
                installation.getNotes(), installation.getReturnReason(), installation.getCancelReason(),
                installation.getCreatedBy(), installation.getCreatedAt(), installation.getStartedAt(),
                installation.getSubmittedAt(), installation.getReviewedBy(), installation.getReviewedAt(),
                installation.getApprovedAt(), installation.getCancelledAt(),
                controller == null ? null : controller.macAddress(),
                controller == null ? null : controller.online(),
                iotAvailable,
                installation.getStatus() == IN_PROGRESS && missing.isEmpty(),
                missing, checkViews, photoViews);
    }
}
