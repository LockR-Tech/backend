package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.LockerReportRequest;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentRequest;
import com.huynqb.laundrylocker.locker.dto.ResolveReportRequest;
import com.huynqb.laundrylocker.locker.model.AttachmentStage;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.RepairLog;
import com.huynqb.laundrylocker.locker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceReportPhotoTest {

    @Mock private LockerUnitRepository lockerRepository;
    @Mock private LockerBoxRepository boxRepository;
    @Mock private LockerReportRepository reportRepository;
    @Mock private RepairLogRepository repairLogRepository;
    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private LockerReportRatingRepository ratingRepository;
    @Mock private DroneUnitRepository droneUnitRepository;
    @Mock private DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    @Mock private IotClient iotClient;
    @Mock private UserClient userClient;
    @Mock private ReportAttachmentService attachmentService;
    @Mock private RabbitTemplate rabbitTemplate;

    private LockerService service;

    @BeforeEach
    void setUp() {
        service = new LockerService(
                lockerRepository, boxRepository, reportRepository, repairLogRepository, scheduleRepository,
                ratingRepository, droneUnitRepository, droneMaintenanceLogRepository, iotClient, userClient,
                attachmentService, rabbitTemplate);
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> {
            LockerReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(1L);
            }
            return report;
        });
        when(repairLogRepository.save(any(RepairLog.class))).thenAnswer(invocation -> {
            RepairLog log = invocation.getArgument(0);
            log.setId(9L);
            return log;
        });
    }

    @Test
    void reportUsesJwtUserAndAttachesReporterPhotos() {
        List<ReportAttachmentRequest> photos = List.of(photo());

        service.report(10L, new LockerReportRequest(999L, "Ô 3 kẹt", "Cửa không mở", photos), 42L);

        ArgumentCaptor<LockerReport> saved = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository).save(saved.capture());
        assertEquals(42L, saved.getValue().getUserId());
        verify(attachmentService).attach(
                any(LockerReport.class), eq(AttachmentStage.REPORT), eq(photos), eq(42L), isNull(), eq(5));
    }

    @Test
    void resolveWithPhotosChecksAssigneeThenStoresResolutionPhotosAndNote() {
        LockerReport report = inProgress();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        List<ReportAttachmentRequest> photos = List.of(photo());

        service.resolveReportAndClearFault(1L, 42L, new ResolveReportRequest("Đã thay bản lề", photos), false);

        verify(attachmentService).assertCanAttachAsStaff(report, 42L, false);
        verify(attachmentService).attach(report, AttachmentStage.RESOLUTION, photos, 42L, 9L, 10);
        assertEquals("RESOLVED", report.getStatus());
    }

    @Test
    void requiredResolutionPhotoBlocksTechnicianButNotAdmin() {
        ReflectionTestUtils.setField(service, "requireResolutionPhoto", true);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(inProgress()));
        when(attachmentService.hasStage(1L, AttachmentStage.RESOLUTION)).thenReturn(false);

        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.resolveReportAndClearFault(1L, 42L, null, false));
        assertEquals("RESOLUTION_PHOTO_REQUIRED", ex.getCode());

        assertEquals("RESOLVED", service.resolveReportAndClearFault(1L, 1L, null, true).status());
    }

    @Test
    void repairLogWithPhotosLinksThemToTheLog() {
        LockerReport report = inProgress();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        List<ReportAttachmentRequest> photos = List.of(photo());

        service.addRepairLog(1L, "Đang tháo khoá", 42L, photos, false);

        verify(attachmentService).assertCanAttachAsStaff(report, 42L, false);
        verify(attachmentService).attach(report, AttachmentStage.PROGRESS, photos, 42L, 9L, 10);
    }

    @Test
    void legacyRepairLogWithoutPhotosSkipsAssigneeCheck() {
        when(reportRepository.findById(1L)).thenReturn(Optional.of(inProgress()));

        service.addRepairLog(1L, "Ghi chú", 77L);

        verify(attachmentService, never()).assertCanAttachAsStaff(any(), any(), anyBoolean());
    }

    private static LockerReport inProgress() {
        LockerReport report = new LockerReport();
        report.setId(1L);
        report.setLockerId(10L);
        report.setUserId(7L);
        report.setTitle("Box 3 fault");
        report.setDescription("Kẹt");
        report.setStatus("IN_PROGRESS");
        report.setAssignedToUserId(42L);
        return report;
    }

    private static ReportAttachmentRequest photo() {
        return new ReportAttachmentRequest(
                "lockr/reports/u42/a", 1L, "sig", "jpg", 1L, 1, 1, null, null, null, null);
    }
}
