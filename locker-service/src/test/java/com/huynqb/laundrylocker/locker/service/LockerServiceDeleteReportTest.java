package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.locker.dto.BatchDeleteReportsRequest;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.MaintenanceInspectionLog;
import com.huynqb.laundrylocker.locker.model.MaintenanceSchedule;
import com.huynqb.laundrylocker.locker.model.RepairLog;
import com.huynqb.laundrylocker.locker.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceDeleteReportTest {

    @Mock private LockerReportRepository reportRepository;
    @Mock private RepairLogRepository repairLogRepository;
    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private MaintenanceInspectionLogRepository inspectionLogRepository;
    @Mock private LockerReportRatingRepository ratingRepository;
    @Mock private ReportAttachmentService attachmentService;
    @Mock private LockerBoxRepository boxRepository;
    @Mock private LockerUnitRepository lockerRepository;

    @InjectMocks private LockerService service;

    @Test
    void deleteReportThrowsNotFoundWhenReportDoesNotExist() {
        when(reportRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteReport(999L, 1L, "Phiếu tạo nhầm"));
    }

    @Test
    void deleteReportRequiresReason() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteReport(100L, 1L, "  "));
        assertEquals("DELETE_REASON_REQUIRED", error.getCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void deleteReportHidesReportButKeepsPhotosLogsAndRatings() {
        Long reportId = 100L;
        LockerReport report = new LockerReport();
        report.setId(reportId);
        report.setStatus("RESOLVED");
        report.setCategory("LOCKER");

        MaintenanceInspectionLog log = new MaintenanceInspectionLog();
        log.setId(1L);
        log.setCreatedReportId(reportId);

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setId(2L);
        schedule.setPendingReportId(reportId);

        RepairLog repairLog = new RepairLog();
        repairLog.setId(3L);
        repairLog.setReportId(reportId);

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(inspectionLogRepository.findByCreatedReportId(reportId)).thenReturn(List.of(log));
        when(scheduleRepository.findByPendingReportId(reportId)).thenReturn(List.of(schedule));
        when(repairLogRepository.findByReportIdOrderByCreatedAtAsc(reportId)).thenReturn(List.of(repairLog));
        when(ratingRepository.findByReportId(reportId)).thenReturn(Optional.empty());

        service.deleteReport(reportId, 7L, "Phiếu tạo nhầm");

        assertNull(log.getCreatedReportId());
        verify(inspectionLogRepository).save(log);

        assertNull(schedule.getPendingReportId());
        verify(scheduleRepository).save(schedule);

        // Xoá mềm: ghi người xoá, lý do; không xoá ảnh, nhật ký sửa chữa, đánh giá hay bản ghi phiếu.
        assertNotNull(report.getDeletedAt());
        assertEquals(7L, report.getDeletedBy());
        assertEquals("Phiếu tạo nhầm", report.getDeletedReason());
        verify(reportRepository).save(report);
        verify(attachmentService, never()).deleteByReportId(any());
        verify(repairLogRepository, never()).deleteAll(any());
        verify(ratingRepository, never()).delete(any());
        verify(reportRepository, never()).delete(any());
    }

    @Test
    void deleteReportsBatchByIds() {
        LockerReport r1 = new LockerReport();
        r1.setId(1L);
        r1.setStatus("RESOLVED");
        r1.setCategory("LOCKER");

        LockerReport r2 = new LockerReport();
        r2.setId(2L);
        r2.setStatus("RESOLVED");
        r2.setCategory("DRONE");

        when(reportRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(r1, r2));

        int count = service.deleteReportsBatch(new BatchDeleteReportsRequest(List.of(1L, 2L), "Dọn phiếu thử"), 7L);

        assertEquals(2, count);
        assertEquals("Dọn phiếu thử", r1.getDeletedReason());
        assertEquals("Dọn phiếu thử", r2.getDeletedReason());
        verify(reportRepository).save(r1);
        verify(reportRepository).save(r2);
        verify(reportRepository, never()).delete(any());
    }

    @Test
    void deleteReportsBatchFilteredByCategory() {
        LockerReport r1 = new LockerReport();
        r1.setId(1L);
        r1.setStatus("RESOLVED");
        r1.setCategory("LOCKER");

        LockerReport r2 = new LockerReport();
        r2.setId(2L);
        r2.setStatus("RESOLVED");
        r2.setCategory("DRONE");

        when(reportRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(r1, r2));

        int count = service.deleteReportsBatch(
                new BatchDeleteReportsRequest(List.of(1L, 2L), false, "KIOSK", "Dọn phiếu thử"), 7L);

        assertEquals(1, count);
        assertNotNull(r1.getDeletedAt());
        assertNull(r2.getDeletedAt());
        verify(reportRepository).save(r1);
        verify(reportRepository, never()).save(r2);
    }

    @Test
    void deleteReportsBatchRequiresReason() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteReportsBatch(new BatchDeleteReportsRequest(List.of(1L), null), 7L));
        assertEquals("DELETE_REASON_REQUIRED", error.getCode());
        verify(reportRepository, never()).findAllById(any());
    }
}
