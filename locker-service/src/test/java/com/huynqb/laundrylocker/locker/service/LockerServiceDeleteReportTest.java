package com.huynqb.laundrylocker.locker.service;

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

        assertThrows(NotFoundException.class, () -> service.deleteReport(999L));
    }

    @Test
    void deleteReportSuccessCleansUpDependencies() {
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

        service.deleteReport(reportId);

        assertNull(log.getCreatedReportId());
        verify(inspectionLogRepository).save(log);

        assertNull(schedule.getPendingReportId());
        verify(scheduleRepository).save(schedule);

        verify(attachmentService).deleteByReportId(reportId);
        verify(repairLogRepository).deleteAll(List.of(repairLog));
        verify(reportRepository).delete(report);
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

        int count = service.deleteReportsBatch(new BatchDeleteReportsRequest(List.of(1L, 2L)));

        assertEquals(2, count);
        verify(reportRepository).delete(r1);
        verify(reportRepository).delete(r2);
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

        int count = service.deleteReportsBatch(new BatchDeleteReportsRequest(List.of(1L, 2L), false, "KIOSK"));

        assertEquals(1, count);
        verify(reportRepository).delete(r1);
        verify(reportRepository, never()).delete(r2);
    }
}
