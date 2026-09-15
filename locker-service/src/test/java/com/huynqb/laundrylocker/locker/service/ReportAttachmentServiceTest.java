package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentRequest;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentResponse;
import com.huynqb.laundrylocker.locker.model.AttachmentStage;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.RepairLog;
import com.huynqb.laundrylocker.locker.model.ReportAttachment;
import com.huynqb.laundrylocker.locker.repository.LockerReportRepository;
import com.huynqb.laundrylocker.locker.repository.RepairLogRepository;
import com.huynqb.laundrylocker.locker.repository.ReportAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportAttachmentServiceTest {

    private static final String SECRET = "unit-secret";

    @Mock
    private ReportAttachmentRepository attachmentRepository;
    @Mock
    private LockerReportRepository reportRepository;
    @Mock
    private RepairLogRepository repairLogRepository;

    private ReportAttachmentService service;
    private CloudinaryMediaStorage storage;

    @BeforeEach
    void setUp() {
        storage = spy(new CloudinaryMediaStorage("cloudinary://key:" + SECRET + "@demo", "lockr"));
        doNothing().when(storage).deleteAfterCommit(any());
        service = new ReportAttachmentService(attachmentRepository, reportRepository, repairLogRepository, storage);
        when(attachmentRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ReportAttachment> saved = new ArrayList<>(invocation.getArgument(0));
            long id = 100;
            for (ReportAttachment attachment : saved) {
                attachment.setId(id++);
                attachment.setCreatedAt(LocalDateTime.now());
            }
            return saved;
        });
        when(repairLogRepository.save(any(RepairLog.class))).thenAnswer(invocation -> {
            RepairLog log = invocation.getArgument(0);
            log.setId(55L);
            return log;
        });
    }

    @Test
    void assignedTechnicianAddsInspectionPhotosWithLogNote() {
        LockerReport report = report(1L, "IN_PROGRESS", 7L, 42L);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        List<ReportAttachmentResponse> result = service.addByStaff(
                1L, "inspection", "Đã tới hiện trường", List.of(photo(42L, "a1")), 42L, false);

        assertEquals(1, result.size());
        ReportAttachmentResponse photo = result.get(0);
        assertEquals("INSPECTION", photo.stage());
        assertEquals(55L, photo.repairLogId());
        assertEquals(42L, photo.uploadedByUserId());
        assertEquals("https://res.cloudinary.com/demo/image/upload/v1700000000/lockr/reports/u42/a1.jpg", photo.url());
        assertTrue(photo.thumbnailUrl().contains("/c_fill,w_320,h_320/"));
        verify(repairLogRepository).save(any(RepairLog.class));
    }

    @Test
    void otherTechnicianCannotAddPhotos() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report(1L, "IN_PROGRESS", 7L, 42L)));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.addByStaff(1L, "RESOLUTION", null, List.of(photo(99L, "a1")), 99L, false));

        assertEquals("REPORT_NOT_ASSIGNED", ex.getCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(attachmentRepository, never()).saveAll(anyList());
    }

    @Test
    void technicianCannotAddPhotosToOpenReportOrUseReportStage() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report(1L, "OPEN", 7L, null)));
        assertEquals("REPORT_NOT_IN_PROGRESS", assertThrows(
                BusinessException.class,
                () -> service.addByStaff(1L, "INSPECTION", null, List.of(photo(42L, "a1")), 42L, false)).getCode());
        assertEquals("ATTACHMENT_STAGE_INVALID", assertThrows(
                BusinessException.class,
                () -> service.addByStaff(1L, "REPORT", null, List.of(photo(42L, "a1")), 42L, false)).getCode());
    }

    @Test
    void adminCanAddAnyStageRegardlessOfStatus() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report(1L, "RESOLVED", 7L, 42L)));

        List<ReportAttachmentResponse> result =
                service.addByStaff(1L, "REPORT", null, List.of(photo(1L, "admin1")), 1L, true);

        assertEquals("REPORT", result.get(0).stage());
    }

    @Test
    void photoUploadedByAnotherUserIsRejected() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report(1L, "IN_PROGRESS", 7L, 42L)));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.addByStaff(1L, "PROGRESS", null, List.of(photo(7L, "stolen")), 42L, false));

        assertEquals("MEDIA_OWNER_MISMATCH", ex.getCode());
    }

    @Test
    void reporterAddsPhotosOnlyToOwnUnresolvedReport() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report(1L, "OPEN", 7L, null)));
        assertEquals("REPORT", service.addByReporter(1L, 7L, List.of(photo(7L, "r1"))).get(0).stage());

        assertEquals("REPORT_NOT_OWNED", assertThrows(
                BusinessException.class,
                () -> service.addByReporter(1L, 8L, List.of(photo(8L, "r2")))).getCode());

        when(reportRepository.findById(2L)).thenReturn(Optional.of(report(2L, "RESOLVED", 7L, 42L)));
        assertEquals("REPORT_ALREADY_RESOLVED", assertThrows(
                BusinessException.class,
                () -> service.addByReporter(2L, 7L, List.of(photo(7L, "r3")))).getCode());
    }

    @Test
    void enforcesReporterStageAndRequestLimits() {
        LockerReport report = report(1L, "OPEN", 7L, null);
        when(attachmentRepository.countByReportIdAndStage(1L, AttachmentStage.REPORT)).thenReturn(9L);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.attach(report, AttachmentStage.REPORT,
                        List.of(photo(7L, "x1"), photo(7L, "x2")), 7L, null, 5));
        assertEquals("ATTACHMENT_LIMIT_EXCEEDED", ex.getCode());

        assertEquals("ATTACHMENT_LIMIT_EXCEEDED", assertThrows(
                BusinessException.class,
                () -> service.attach(report, AttachmentStage.REPORT,
                        Collections.nCopies(6, photo(7L, "y")), 7L, null, 5)).getCode());
    }

    @Test
    void rejectsSamePhotoTwiceInOneRequest() {
        LockerReport report = report(1L, "OPEN", 7L, null);
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.attach(report, AttachmentStage.REPORT,
                        List.of(photo(7L, "dup"), photo(7L, "dup")), 7L, null, 5));
        assertEquals("ATTACHMENT_DUPLICATE", ex.getCode());
    }

    @Test
    void uploaderDeletesBeforeResolutionAdminAnytime() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report(1L, "RESOLVED", 7L, 42L)));
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(attachment(5L, 1L, 42L)));

        assertEquals("ATTACHMENT_DELETE_FORBIDDEN", assertThrows(
                BusinessException.class, () -> service.delete(1L, 5L, 42L, false)).getCode());

        service.delete(1L, 5L, 1L, true);
        ArgumentCaptor<ReportAttachment> deleted = ArgumentCaptor.forClass(ReportAttachment.class);
        verify(attachmentRepository).delete(deleted.capture());
        assertEquals(5L, deleted.getValue().getId());
        verify(storage).deleteAfterCommit("lockr/reports/u42/p5");
    }

    @Test
    void deleteRejectsAttachmentFromAnotherReport() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report(1L, "OPEN", 7L, null)));
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(attachment(5L, 2L, 7L)));

        assertThrows(Exception.class, () -> service.delete(1L, 5L, 7L, true));
        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    void capturedAtAcceptsLocalAndOffsetIsoFormats() {
        assertEquals(LocalDateTime.of(2026, 9, 15, 8, 10), ReportAttachmentService.parseCapturedAt("2026-09-15T08:10:00"));
        assertNotNull(ReportAttachmentService.parseCapturedAt("2026-09-15T01:10:00.123Z"));
        assertNull(ReportAttachmentService.parseCapturedAt(" "));
        assertThrows(BusinessException.class, () -> ReportAttachmentService.parseCapturedAt("15/09/2026"));
    }

    private static LockerReport report(Long id, String status, Long reporterId, Long assigneeId) {
        LockerReport report = new LockerReport();
        report.setId(id);
        report.setLockerId(10L);
        report.setUserId(reporterId);
        report.setTitle("Box 3 fault");
        report.setDescription("Cửa không đóng");
        report.setStatus(status);
        report.setAssignedToUserId(assigneeId);
        return report;
    }

    private static ReportAttachment attachment(Long id, Long reportId, Long uploaderId) {
        ReportAttachment attachment = new ReportAttachment();
        attachment.setId(id);
        attachment.setReportId(reportId);
        attachment.setStage(AttachmentStage.INSPECTION);
        attachment.setPublicId("lockr/reports/u" + uploaderId + "/p" + id);
        attachment.setSecureUrl("https://res.cloudinary.com/demo/image/upload/v1/lockr/reports/u" + uploaderId + "/p" + id + ".jpg");
        attachment.setUploadedByUserId(uploaderId);
        return attachment;
    }

    /// Ảnh "đã upload" hợp lệ: chữ ký phản hồi đúng như Cloudinary ký.
    private static ReportAttachmentRequest photo(Long uploaderId, String name) {
        String publicId = "lockr/reports/u" + uploaderId + "/" + name;
        long version = 1700000000L;
        String signature = CloudinaryMediaStorage.sign(
                Map.of("public_id", publicId, "version", String.valueOf(version)), SECRET);
        return new ReportAttachmentRequest(
                publicId, version, signature, "jpg", 1024L, 800, 600, "Cửa lệch", "2026-09-15T08:10:00", 10.77, 106.7);
    }
}
