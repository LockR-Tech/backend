package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentRequest;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentResponse;
import com.huynqb.laundrylocker.locker.model.AttachmentStage;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.RepairLog;
import com.huynqb.laundrylocker.locker.model.ReportAttachment;
import com.huynqb.laundrylocker.locker.repository.LockerReportRepository;
import com.huynqb.laundrylocker.locker.repository.RepairLogRepository;
import com.huynqb.laundrylocker.locker.repository.ReportAttachmentRepository;
import com.huynqb.laundrylocker.locker.settings.LockerRules;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// Ảnh hiện trường / xác nhận / quá trình / nghiệm thu của phiếu sự cố (ADR-0004).
@Service
@RequiredArgsConstructor
public class ReportAttachmentService {

    private final ReportAttachmentRepository attachmentRepository;
    private final LockerReportRepository reportRepository;
    private final RepairLogRepository repairLogRepository;
    private final CloudinaryMediaStorage mediaStorage;
    /// Giới hạn số ảnh mỗi lần gửi / mỗi phiếu do admin cấu hình (ADR-0005).
    private final LockerRules rules;

    // ---- Dùng bên trong transaction của LockerService ----

    /// Xác minh + lưu ảnh cho một phiếu. Không kiểm quyền — người gọi tự kiểm.
    List<ReportAttachmentResponse> attach(
            LockerReport report,
            AttachmentStage stage,
            List<ReportAttachmentRequest> requests,
            Long actorUserId,
            Long repairLogId,
            int perRequestLimit) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() > perRequestLimit) {
            throw limitExceeded("At most " + perRequestLimit + " images per request");
        }
        int maxPerReport = rules.reportPhotosTotal();
        long total = attachmentRepository.countByReportId(report.getId());
        if (total + requests.size() > maxPerReport) {
            throw limitExceeded("A report can hold at most " + maxPerReport + " images");
        }
        int maxReporterPerReport = rules.reportPhotosReporterTotal();
        if (stage == AttachmentStage.REPORT
                && attachmentRepository.countByReportIdAndStage(report.getId(), stage) + requests.size()
                        > maxReporterPerReport) {
            throw limitExceeded("A report can hold at most " + maxReporterPerReport + " reporter images");
        }
        Set<String> seen = new HashSet<>();
        List<ReportAttachment> entities = new ArrayList<>(requests.size());
        for (ReportAttachmentRequest request : requests) {
            if (request == null) {
                continue;
            }
            VerifiedMedia media = mediaStorage.verify(request.media(), MediaPurpose.REPORT_EVIDENCE, actorUserId);
            if (!seen.add(media.publicId()) || attachmentRepository.existsByPublicId(media.publicId())) {
                throw new BusinessException(
                        "ATTACHMENT_DUPLICATE", "Image is already attached: " + media.publicId(), HttpStatus.CONFLICT);
            }
            ReportAttachment attachment = new ReportAttachment();
            attachment.setReportId(report.getId());
            attachment.setRepairLogId(repairLogId);
            attachment.setStage(stage);
            attachment.setPublicId(media.publicId());
            attachment.setSecureUrl(media.secureUrl());
            attachment.setFormat(media.format());
            attachment.setBytes(media.bytes());
            attachment.setWidth(media.width());
            attachment.setHeight(media.height());
            attachment.setCaption(StringUtils.hasText(request.caption()) ? request.caption().trim() : null);
            attachment.setLatitude(request.latitude());
            attachment.setLongitude(request.longitude());
            attachment.setCapturedAt(parseCapturedAt(request.capturedAt()));
            attachment.setUploadedByUserId(actorUserId);
            entities.add(attachment);
        }
        return attachmentRepository.saveAll(entities).stream().map(this::toResponse).toList();
    }

    /// KTV chỉ gắn ảnh vào phiếu mình đang xử lý; ADMIN gắn được mọi lúc.
    void assertCanAttachAsStaff(LockerReport report, Long actorUserId, boolean admin) {
        if (admin) {
            return;
        }
        if (!"IN_PROGRESS".equalsIgnoreCase(report.getStatus())) {
            throw new BusinessException(
                    "REPORT_NOT_IN_PROGRESS", "Photos can only be added while the report is in progress");
        }
        if (actorUserId == null || !actorUserId.equals(report.getAssignedToUserId())) {
            throw new BusinessException(
                    "REPORT_NOT_ASSIGNED", "Only the assigned technician can add photos", HttpStatus.FORBIDDEN);
        }
    }

    boolean hasStage(Long reportId, AttachmentStage stage) {
        return attachmentRepository.countByReportIdAndStage(reportId, stage) > 0;
    }

    Map<Long, List<ReportAttachmentResponse>> byReportIds(Collection<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findByReportIdInOrderByCreatedAtAscIdAsc(reportIds).stream()
                .map(this::toResponse)
                .collect(Collectors.groupingBy(ReportAttachmentResponse::reportId));
    }

    Map<Long, List<ReportAttachmentResponse>> byRepairLogIds(Collection<Long> repairLogIds) {
        if (repairLogIds == null || repairLogIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findByRepairLogIdInOrderByCreatedAtAscIdAsc(repairLogIds).stream()
                .map(this::toResponse)
                .collect(Collectors.groupingBy(ReportAttachmentResponse::repairLogId));
    }

    // ---- API công khai ----

    @Transactional(readOnly = true)
    public List<ReportAttachmentResponse> list(Long reportId, String stage) {
        findReport(reportId);
        List<ReportAttachment> attachments = StringUtils.hasText(stage)
                ? attachmentRepository.findByReportIdAndStageOrderByCreatedAtAscIdAsc(reportId, parseStage(stage))
                : attachmentRepository.findByReportIdOrderByCreatedAtAscIdAsc(reportId);
        return attachments.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReportAttachmentResponse> listForReporter(Long reportId, Long userId) {
        assertReporter(findReport(reportId), userId);
        return list(reportId, null);
    }

    /// Người báo bổ sung ảnh hiện trường khi phiếu chưa được đóng.
    @Transactional
    public List<ReportAttachmentResponse> addByReporter(Long reportId, Long userId, List<ReportAttachmentRequest> requests) {
        LockerReport report = findReport(reportId);
        assertReporter(report, userId);
        if ("RESOLVED".equalsIgnoreCase(report.getStatus())) {
            throw new BusinessException("REPORT_ALREADY_RESOLVED", "Report is already resolved");
        }
        return attach(report, AttachmentStage.REPORT, requests, userId, null, rules.reportPhotosPerRequestReporter());
    }

    /// KTV (người được giao) hoặc ADMIN thêm ảnh xác nhận / quá trình / nghiệm thu.
    /// `note` có giá trị ⇒ tạo một dòng nhật ký và gắn ảnh vào dòng đó.
    @Transactional
    public List<ReportAttachmentResponse> addByStaff(
            Long reportId,
            String stage,
            String note,
            List<ReportAttachmentRequest> requests,
            Long actorUserId,
            boolean admin) {
        LockerReport report = findReport(reportId);
        AttachmentStage parsed = parseStage(stage);
        if (parsed == AttachmentStage.REPORT && !admin) {
            throw new BusinessException(
                    "ATTACHMENT_STAGE_INVALID", "Technicians add INSPECTION, PROGRESS or RESOLUTION photos");
        }
        assertCanAttachAsStaff(report, actorUserId, admin);
        Long repairLogId = null;
        if (StringUtils.hasText(note)) {
            RepairLog log = new RepairLog();
            log.setReportId(report.getId());
            log.setActorUserId(actorUserId);
            log.setNote(note.trim());
            repairLogId = repairLogRepository.save(log).getId();
        }
        return attach(report, parsed, requests, actorUserId, repairLogId, rules.reportPhotosPerRequestStaff());
    }

    /// Người upload xoá được khi phiếu chưa đóng; ADMIN xoá được mọi lúc (ảnh nhạy cảm, nhầm phiếu).
    @Transactional
    public void delete(Long reportId, Long attachmentId, Long actorUserId, boolean admin) {
        LockerReport report = findReport(reportId);
        ReportAttachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getReportId().equals(reportId))
                .orElseThrow(() -> new NotFoundException("ReportAttachment", attachmentId));
        boolean ownOpenReport = actorUserId != null
                && actorUserId.equals(attachment.getUploadedByUserId())
                && !"RESOLVED".equalsIgnoreCase(report.getStatus());
        if (!admin && !ownOpenReport) {
            throw new BusinessException(
                    "ATTACHMENT_DELETE_FORBIDDEN",
                    "Only the uploader (before resolution) or an admin can delete this image",
                    HttpStatus.FORBIDDEN);
        }
        attachmentRepository.delete(attachment);
        mediaStorage.deleteAfterCommit(attachment.getPublicId());
    }

    ReportAttachmentResponse toResponse(ReportAttachment attachment) {
        return new ReportAttachmentResponse(
                attachment.getId(),
                attachment.getReportId(),
                attachment.getRepairLogId(),
                attachment.getStage().name(),
                attachment.getSecureUrl(),
                mediaStorage.thumbnailUrl(attachment.getSecureUrl()),
                attachment.getPublicId(),
                attachment.getFormat(),
                attachment.getBytes(),
                attachment.getWidth(),
                attachment.getHeight(),
                attachment.getCaption(),
                attachment.getLatitude(),
                attachment.getLongitude(),
                attachment.getCapturedAt(),
                attachment.getUploadedByUserId(),
                attachment.getCreatedAt());
    }

    private LockerReport findReport(Long reportId) {
        return reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("LockerReport", reportId));
    }

    private static void assertReporter(LockerReport report, Long userId) {
        if (userId == null || !userId.equals(report.getUserId())) {
            throw new BusinessException(
                    "REPORT_NOT_OWNED", "Only the reporter can access these photos", HttpStatus.FORBIDDEN);
        }
    }

    private static AttachmentStage parseStage(String stage) {
        if (!StringUtils.hasText(stage)) {
            throw new BusinessException("ATTACHMENT_STAGE_INVALID", "stage is required");
        }
        try {
            return AttachmentStage.valueOf(stage.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ATTACHMENT_STAGE_INVALID", "Unsupported stage: " + stage);
        }
    }

    private static BusinessException limitExceeded(String message) {
        return new BusinessException("ATTACHMENT_LIMIT_EXCEEDED", message);
    }

    /// Client mobile gửi giờ địa phương không múi giờ, trình duyệt gửi `…Z` — nhận cả hai.
    static LocalDateTime parseCapturedAt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // thử dạng có múi giờ bên dưới
        }
        try {
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new BusinessException("CAPTURED_AT_INVALID", "capturedAt must be an ISO-8601 date-time");
        }
    }
}
