package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.OrderClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.CompleteScheduleRequest;
import com.huynqb.laundrylocker.locker.dto.InspectionItemResult;
import com.huynqb.laundrylocker.locker.model.*;
import com.huynqb.laundrylocker.locker.repository.*;
import com.huynqb.laundrylocker.locker.settings.TestLockerRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Luồng 4b: kiểm tra định kỳ ĐẠT/KHÔNG ĐẠT theo từng mục, phiếu tự sinh và nhắc hạn.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceInspectionTest {

    private static final long LOCKER_ID = 10L;
    private static final long SCHEDULE_ID = 3L;
    private static final long TECH = 42L;
    private static final long OTHER_TECH = 43L;
    private static final String CHECKLIST = "Khoá điện; Cảm biến cửa\nMàn hình";

    @Mock private LockerUnitRepository lockerRepository;
    @Mock private LockerBoxRepository boxRepository;
    @Mock private LockerReportRepository reportRepository;
    @Mock private RepairLogRepository repairLogRepository;
    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private MaintenanceInspectionLogRepository inspectionLogRepository;
    @Mock private LockerReportRatingRepository ratingRepository;
    @Mock private DroneUnitRepository droneUnitRepository;
    @Mock private DroneDeviceRepository droneDeviceRepository;
    @Mock private DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    @Mock private DroneIotLogRepository droneIotLogRepository;
    @Mock private IotClient iotClient;
    @Mock private UserClient userClient;
    @Mock private OrderClient orderClient;
    @Mock private ReportAttachmentService attachmentService;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private TechnicianPresenceRepository presenceRepository;

    private LockerService service;
    private MaintenanceSchedule schedule;
    private LocalDateTime originalDue;

    @BeforeEach
    void setUp() {
        service = new LockerService(
                lockerRepository, boxRepository, reportRepository, repairLogRepository, scheduleRepository,
                inspectionLogRepository, ratingRepository, droneUnitRepository, droneDeviceRepository, droneMaintenanceLogRepository,
                droneIotLogRepository,
                iotClient, userClient, orderClient, attachmentService, rabbitTemplate, TestLockerRules.defaults(),
                presenceRepository);
        LockerUnit locker = new LockerUnit();
        locker.setId(LOCKER_ID);
        locker.setName("Tủ A");
        locker.setStatus("ACTIVE");
        when(lockerRepository.findById(LOCKER_ID)).thenReturn(Optional.of(locker));

        originalDue = LocalDateTime.now().minusHours(2);
        schedule = new MaintenanceSchedule();
        schedule.setId(SCHEDULE_ID);
        schedule.setLockerId(LOCKER_ID);
        schedule.setTitle("Kiểm tra tháng");
        schedule.setIntervalDays(30);
        schedule.setNextDueAt(originalDue);
        schedule.setChecklist(CHECKLIST);
        schedule.setAssignedTechnicianId(TECH);
        schedule.setActive(true);
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any(MaintenanceSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> {
            LockerReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(200L);
            }
            return report;
        });
        when(userClient.getUser(TECH)).thenReturn(ApiResponse.ok(user(TECH, "LOCKER_TECHNICIAN")));
    }

    @Test
    void checklistIsSplitByLinesOrSemicolons() {
        assertEquals(List.of("Khoá điện", "Cảm biến cửa", "Màn hình"), LockerService.checklistItems(CHECKLIST));
        assertEquals(List.of(), LockerService.checklistItems("  "));
    }

    @Test
    void droneTechnicianCannotReadOrCompleteLockerSchedule() {
        BusinessException readError = assertThrows(
                BusinessException.class,
                () -> service.requireScheduleTarget(SCHEDULE_ID, "DRONE"));
        assertEquals("SCHEDULE_TARGET_FORBIDDEN", readError.getCode());

        BusinessException completeError = assertThrows(
                BusinessException.class,
                () -> service.completeSchedule(
                        SCHEDULE_ID, items("PASS", "PASS", "PASS"), TECH, false, "DRONE"));
        assertEquals("SCHEDULE_TARGET_FORBIDDEN", completeError.getCode());
        verify(scheduleRepository, never()).save(any(MaintenanceSchedule.class));
    }

    @Test
    void allItemsPassingAdvancesTheDueDate() {
        var response = service.completeSchedule(
                SCHEDULE_ID, items("PASS", "PASS", "NA"), TECH, false);

        assertEquals("PASSED", schedule.getLastResult());
        assertTrue(schedule.getNextDueAt().isAfter(LocalDateTime.now().plusDays(29)));
        assertNull(schedule.getPendingReportId());
        verify(reportRepository, never()).save(any(LockerReport.class));
        assertEquals(List.of("Khoá điện", "Cảm biến cửa", "Màn hình"), response.checklistItems());
        MaintenanceInspectionLog log = savedLog();
        assertEquals("PASSED", log.getStatus());
        assertTrue(log.getChecklistResults().contains("\"result\":\"NA\""));
    }

    @Test
    void aFailingItemKeepsTheDueDateAndOpensATicketForTheInspector() {
        service.completeSchedule(SCHEDULE_ID, items("PASS", "FAIL", "PASS"), TECH, false);

        assertEquals("FAILED", schedule.getLastResult());
        assertEquals(originalDue, schedule.getNextDueAt());
        assertEquals(200L, schedule.getPendingReportId());
        ArgumentCaptor<LockerReport> report = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository, atLeastOnce()).save(report.capture());
        LockerReport ticket = report.getValue();
        assertEquals(ReportCategory.LOCKER, ticket.getCategory());
        assertEquals(TECH, ticket.getAssignedToUserId());
        assertEquals("IN_PROGRESS", ticket.getStatus());
        assertEquals(SCHEDULE_ID, ticket.getScheduleId());
        assertTrue(ticket.getDescription().contains("Cảm biến cửa"));
        assertEquals(200L, savedLog().getCreatedReportId());
    }

    @Test
    void failingWithAFaultyBoxMarksThatBoxThroughTheFaultFlow() {
        LockerBox box = new LockerBox();
        box.setId(5L);
        box.setLockerId(LOCKER_ID);
        box.setBoxNumber(2);
        box.setStatus("AVAILABLE");
        when(boxRepository.findById(5L)).thenReturn(Optional.of(box));

        service.completeSchedule(SCHEDULE_ID, items(5L, "FAIL", "PASS", "PASS"), TECH, false);

        assertEquals("FAULT", box.getStatus());
        ArgumentCaptor<LockerReport> report = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository, atLeastOnce()).save(report.capture());
        assertEquals(ReportCategory.BOX, report.getValue().getCategory());
        assertEquals(5L, report.getValue().getBoxId());

        LockerBox elsewhere = new LockerBox();
        elsewhere.setId(6L);
        elsewhere.setLockerId(99L);
        when(boxRepository.findById(6L)).thenReturn(Optional.of(elsewhere));
        schedule.setPendingReportId(null);
        assertEquals(
                "FAULT_BOX_NOT_IN_LOCKER",
                assertThrows(BusinessException.class,
                        () -> service.completeSchedule(SCHEDULE_ID, items(6L, "FAIL", "PASS", "PASS"), TECH, false))
                        .getCode());
    }

    @Test
    void itemsMustMatchTheSchedulesChecklist() {
        CompleteScheduleRequest missing = request(null, List.of(
                new InspectionItemResult("Khoá điện", "PASS", null)));
        assertEquals("CHECKLIST_INCOMPLETE",
                assertThrows(BusinessException.class,
                        () -> service.completeSchedule(SCHEDULE_ID, missing, TECH, false)).getCode());

        CompleteScheduleRequest unknown = request(null, List.of(
                new InspectionItemResult("Khoá điện", "PASS", null),
                new InspectionItemResult("Cảm biến cửa", "PASS", null),
                new InspectionItemResult("Màn hình", "PASS", null),
                new InspectionItemResult("Bánh xe", "PASS", null)));
        assertEquals("CHECKLIST_ITEM_UNKNOWN",
                assertThrows(BusinessException.class,
                        () -> service.completeSchedule(SCHEDULE_ID, unknown, TECH, false)).getCode());

        assertEquals("INSPECTION_ITEM_RESULT_INVALID",
                assertThrows(BusinessException.class,
                        () -> service.completeSchedule(SCHEDULE_ID, items("PASS", "OK", "PASS"), TECH, false))
                        .getCode());
        assertEquals(originalDue, schedule.getNextDueAt());
    }

    @Test
    void onlyTheAssignedTechnicianOrAnAdminCompletes() {
        assertEquals("SCHEDULE_NOT_ASSIGNED",
                assertThrows(BusinessException.class,
                        () -> service.completeSchedule(SCHEDULE_ID, items("PASS", "PASS", "PASS"), OTHER_TECH, false))
                        .getCode());

        service.completeSchedule(SCHEDULE_ID, items("PASS", "PASS", "PASS"), 1L, true);
        assertEquals("PASSED", schedule.getLastResult());
    }

    // Admin ghi hộ trên web: phiếu giao cho KTV phụ trách lịch, không giao cho tài khoản admin.
    @Test
    void adminRecordedFailureGoesToTheSchedulesTechnicianNotTheAdmin() {
        service.completeSchedule(SCHEDULE_ID, items("FAIL", "PASS", "PASS"), 1L, true);

        ArgumentCaptor<LockerReport> report = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository, atLeastOnce()).save(report.capture());
        assertEquals(TECH, report.getValue().getAssignedToUserId());
        assertEquals(TECH, savedLog().getTechnicianId());

        schedule.setPendingReportId(null);
        schedule.setAssignedTechnicianId(null);
        clearInvocations(reportRepository);
        service.completeSchedule(SCHEDULE_ID, items("FAIL", "PASS", "PASS"), 1L, true);

        verify(reportRepository, atLeastOnce()).save(report.capture());
        assertNull(report.getValue().getAssignedToUserId());
        assertEquals("OPEN", report.getValue().getStatus());
    }

    @Test
    void aScheduleWaitingOnItsTicketCannotBeReinspected() {
        schedule.setPendingReportId(200L);
        LockerReport pending = new LockerReport();
        pending.setId(200L);
        pending.setStatus("IN_PROGRESS");
        when(reportRepository.findById(200L)).thenReturn(Optional.of(pending));

        assertEquals("SCHEDULE_PENDING_REPORT",
                assertThrows(BusinessException.class,
                        () -> service.completeSchedule(SCHEDULE_ID, items("PASS", "PASS", "PASS"), TECH, false))
                        .getCode());
    }

    @Test
    void resolvingThePendingTicketAdvancesTheDueDate() {
        schedule.setPendingReportId(200L);
        schedule.setLastDueNotifiedAt(LocalDateTime.now());
        LockerReport pending = new LockerReport();
        pending.setId(200L);
        pending.setLockerId(LOCKER_ID);
        pending.setUserId(TECH);
        pending.setTitle("Kiểm tra định kỳ không đạt");
        pending.setStatus("IN_PROGRESS");
        pending.setAssignedToUserId(TECH);
        pending.setScheduleId(SCHEDULE_ID);
        when(reportRepository.findById(200L)).thenReturn(Optional.of(pending));
        when(scheduleRepository.findByPendingReportId(200L)).thenReturn(List.of(schedule));

        service.resolveReportAndClearFault(200L, TECH, null, false);

        assertNull(schedule.getPendingReportId());
        assertNull(schedule.getLastDueNotifiedAt());
        assertTrue(schedule.getNextDueAt().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void legacyClientsStillSendAStatus() {
        CompleteScheduleRequest attention = new CompleteScheduleRequest(
                null, null, "ATTENTION", "Bụi nhiều", null, "Khoá điện", false, null, null);
        service.completeSchedule(SCHEDULE_ID, attention, TECH, false);
        assertEquals("PASSED", schedule.getLastResult());
        assertEquals("ATTENTION", savedLog().getStatus());

        schedule.setNextDueAt(originalDue);
        // Kết quả không đạt luôn mở phiếu, kể cả khi client cũ không bật autoCreateReport.
        CompleteScheduleRequest defect = new CompleteScheduleRequest(
                null, null, "DEFECT_DETECTED", "Khoá lỏng", null, null, false, null, null);
        service.completeSchedule(SCHEDULE_ID, defect, TECH, false);
        assertEquals("FAILED", schedule.getLastResult());
        assertEquals(originalDue, schedule.getNextDueAt());
        assertEquals(200L, schedule.getPendingReportId());

        schedule.setPendingReportId(null);
        CompleteScheduleRequest invalid = new CompleteScheduleRequest(
                null, null, "MAYBE", null, null, null, null, null, null);
        assertEquals("INSPECTION_STATUS_INVALID",
                assertThrows(BusinessException.class,
                        () -> service.completeSchedule(SCHEDULE_ID, invalid, TECH, false)).getCode());
    }

    // BUG-WF04-01 (WF04-026, WF04-027): lịch drone FAIL mở phiếu drone gắn scheduleId,
    // giữ nguyên hạn, set pendingReportId & createdReportId, chặn hoàn tất lại cho tới khi đóng phiếu.
    @Test
    void droneScheduleFailureLinksPendingReportKeepsDueDateAndBlocksReinspection() {
        schedule.setLockerId(null);
        schedule.setDroneUnitId(8L);
        DroneUnit drone = new DroneUnit();
        drone.setId(8L);
        drone.setLockerId(LOCKER_ID);
        drone.setCode("DRONE-08");
        drone.setStatus(DroneStatus.CHARGING);
        when(droneUnitRepository.findById(8L)).thenReturn(Optional.of(drone));
        when(droneUnitRepository.save(any(DroneUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteScheduleRequest failWithPhoto = new CompleteScheduleRequest(
                null, null, null, null,
                List.of("https://res.cloudinary.com/demo/image/upload/v1795849714/lockr/reports/u42/propeller.jpg"),
                null, null, null, "Cánh quạt lệch",
                List.of(
                        new InspectionItemResult("Khoá điện", "PASS", null),
                        new InspectionItemResult("Cảm biến cửa", "FAIL", "Cánh quạt lệch"),
                        new InspectionItemResult("Màn hình", "PASS", null)));

        service.completeSchedule(SCHEDULE_ID, failWithPhoto, TECH, false);

        assertEquals("FAILED", schedule.getLastResult());
        assertEquals(originalDue, schedule.getNextDueAt());
        assertEquals(200L, schedule.getPendingReportId());
        assertEquals(DroneStatus.FAULT, drone.getStatus());
        assertNotNull(drone.getLastChargedAt());
        assertEquals(200L, savedLog().getCreatedReportId());

        ArgumentCaptor<LockerReport> reportCaptor = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository, atLeastOnce()).save(reportCaptor.capture());
        LockerReport opened = reportCaptor.getValue();
        assertEquals(ReportCategory.DRONE, opened.getCategory());
        assertEquals(8L, opened.getDroneUnitId());
        assertEquals(SCHEDULE_ID, opened.getScheduleId());
        assertEquals(TECH, opened.getAssignedToUserId());
        assertEquals("IN_PROGRESS", opened.getStatus());
        assertNull(opened.getBoxId());
        verify(attachmentService).attachFromInspectionUrls(
                eq(opened), eq(failWithPhoto.photoUrls()), eq(TECH));

        // Hoàn tất lại khi RPT-200 còn mở phải bị chặn 409 SCHEDULE_PENDING_REPORT
        when(reportRepository.findById(200L)).thenReturn(Optional.of(opened));
        BusinessException pendingEx = assertThrows(BusinessException.class,
                () -> service.completeSchedule(SCHEDULE_ID, items("PASS", "PASS", "PASS"), TECH, false));
        assertEquals("SCHEDULE_PENDING_REPORT", pendingEx.getCode());

        // Đóng phiếu RPT-200 -> dời hạn kế tiếp, xóa pendingReportId, đưa drone về IDLE và gửi thông báo đúng tên drone
        when(scheduleRepository.findByPendingReportId(200L)).thenReturn(List.of(schedule));
        service.resolveReportAndClearFault(200L, TECH, null, false);

        assertNull(schedule.getPendingReportId());
        assertTrue(schedule.getNextDueAt().isAfter(LocalDateTime.now().plusDays(29)));
        assertEquals(DroneStatus.IDLE, drone.getStatus());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(
                eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.LOCKER_REPORT_RESOLVED), events.capture());
        String resolvedMsg = String.valueOf(((DomainEvent) events.getValue()).payload().get("message"));
        assertTrue(resolvedMsg.contains("Báo cáo sự cố drone DRONE-08"), "Expected drone wording, got: " + resolvedMsg);
        assertFalse(resolvedMsg.contains("Báo cáo lỗi tủ"), "Must not mention locker wording for drone report");
    }

    // BUG-WF04-02 (WF04-041): KTV tủ không được thấy hay thao tác (claim/get/resolve) phiếu sự cố Drone.
    @Test
    void kioskReportEndpointsExcludeAndForbidDroneReports() {
        LockerReport kioskOpen = new LockerReport();
        kioskOpen.setId(301L);
        kioskOpen.setLockerId(LOCKER_ID);
        kioskOpen.setCategory(ReportCategory.BOX);
        kioskOpen.setStatus("OPEN");
        kioskOpen.setTitle("Ô hỏng");

        LockerReport droneOpen = new LockerReport();
        droneOpen.setId(302L);
        droneOpen.setLockerId(LOCKER_ID);
        droneOpen.setDroneUnitId(8L);
        droneOpen.setCategory(ReportCategory.DRONE);
        droneOpen.setStatus("OPEN");
        droneOpen.setTitle("Drone lỗi");

        when(reportRepository.findByStatusInOrderByCreatedAtDesc(anyList()))
                .thenReturn(List.of(kioskOpen, droneOpen));
        when(reportRepository.findById(302L)).thenReturn(Optional.of(droneOpen));

        List<Long> visibleIds = service.openKioskReports().stream().map(r -> r.id()).toList();
        assertEquals(List.of(301L), visibleIds);

        BusinessException claimEx = assertThrows(BusinessException.class,
                () -> service.claimKioskReport(302L, TECH));
        assertEquals("LOCKER_REPORT_REQUIRED", claimEx.getCode());
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, claimEx.getStatus());

        BusinessException getEx = assertThrows(BusinessException.class,
                () -> service.getKioskReport(302L));
        assertEquals("LOCKER_REPORT_REQUIRED", getEx.getCode());
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, getEx.getStatus());
    }

    // BUG-WF04-04 (WF04-040): Nhật ký gia hạn SLA hiển thị theo giờ Việt Nam (Asia/Ho_Chi_Minh).
    @Test
    void slaExtensionLogFormatsDueTimeInVietnamTimezone() {
        LockerReport rpt = new LockerReport();
        rpt.setId(401L);
        rpt.setLockerId(LOCKER_ID);
        rpt.setStatus("IN_PROGRESS");
        rpt.setAssignedToUserId(TECH);
        LocalDateTime baseDue = LocalDateTime.now().plusHours(2);
        rpt.setSlaDueAt(baseDue);
        when(reportRepository.findById(401L)).thenReturn(Optional.of(rpt));
        when(repairLogRepository.save(any(RepairLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.extendSlaAsTechnician(401L, 2, "Chờ linh kiện", TECH);

        ArgumentCaptor<RepairLog> logCaptor = ArgumentCaptor.forClass(RepairLog.class);
        verify(repairLogRepository).save(logCaptor.capture());
        LocalDateTime expectedVn = baseDue.plusHours(2)
                .atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .toLocalDateTime();
        String expectedFormatted = expectedVn.format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
        assertTrue(logCaptor.getValue().getNote().contains(expectedFormatted),
                "Expected VN due time " + expectedFormatted + " in note: " + logCaptor.getValue().getNote());
    }

    @Test
    void dueSchedulesAreRemindedOncePerPeriod() {
        MaintenanceSchedule unassigned = new MaintenanceSchedule();
        unassigned.setId(4L);
        unassigned.setLockerId(LOCKER_ID);
        unassigned.setTitle("Vệ sinh");
        unassigned.setNextDueAt(LocalDateTime.now().plusHours(3));
        MaintenanceSchedule waitingOnTicket = new MaintenanceSchedule();
        waitingOnTicket.setId(5L);
        waitingOnTicket.setLockerId(LOCKER_ID);
        waitingOnTicket.setNextDueAt(originalDue);
        waitingOnTicket.setPendingReportId(200L);
        MaintenanceSchedule alreadyTaken = new MaintenanceSchedule();
        alreadyTaken.setId(6L);
        alreadyTaken.setLockerId(LOCKER_ID);
        alreadyTaken.setAssignedTechnicianId(TECH);
        alreadyTaken.setNextDueAt(originalDue);
        when(scheduleRepository.findByActiveTrueAndLastDueNotifiedAtIsNullAndNextDueAtBefore(any()))
                .thenReturn(List.of(schedule, unassigned, waitingOnTicket, alreadyTaken));
        when(scheduleRepository.markDueNotified(eq(SCHEDULE_ID), any())).thenReturn(1);
        when(scheduleRepository.markDueNotified(eq(4L), any())).thenReturn(1);
        when(scheduleRepository.markDueNotified(eq(6L), any())).thenReturn(0);
        when(userClient.listByRole("LOCKER_TECHNICIAN"))
                .thenReturn(ApiResponse.ok(List.of(user(TECH, "LOCKER_TECHNICIAN"), user(OTHER_TECH, "LOCKER_TECHNICIAN"))));

        assertEquals(2, service.remindDueSchedules());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(3)).convertAndSend(
                eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.LOCKER_SCHEDULE_DUE), events.capture());
        List<Object> recipients = events.getAllValues().stream()
                .map(event -> ((DomainEvent) event).payload().get("userId"))
                .toList();
        assertEquals(List.of(TECH, TECH, OTHER_TECH), recipients);
        verify(scheduleRepository, never()).markDueNotified(eq(5L), any());
    }

    @Test
    void oneTimeLockerMaintenanceSchedulePutsLockerInMaintenanceAndRestoresOnPass() {
        LockerUnit locker = lockerRepository.findById(LOCKER_ID).orElseThrow();
        locker.setAssignedTechnicianId(TECH);
        when(scheduleRepository.save(any(MaintenanceSchedule.class))).thenAnswer(inv -> {
            MaintenanceSchedule s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(77L);
            }
            return s;
        });

        var created = service.createSchedule(
                new com.huynqb.laundrylocker.locker.dto.MaintenanceScheduleRequest(
                        LOCKER_ID, null, "Bảo trì tủ A", 0, null, "HIGH",
                        "Vệ sinh và kiểm tra nguồn", null, null, null, null, true),
                TECH,
                "LOCKER_TECHNICIAN");

        assertEquals(77L, created.id());
        assertEquals(TECH, created.assignedTechnicianId());
        assertEquals("MAINTENANCE", locker.getStatus());
        assertEquals("SCHEDULE", locker.getMaintenanceSource());
        verify(reportRepository, never()).save(any(LockerReport.class));

        MaintenanceSchedule adHoc = new MaintenanceSchedule();
        adHoc.setId(77L);
        adHoc.setLockerId(LOCKER_ID);
        adHoc.setTitle("Bảo trì tủ A");
        adHoc.setIntervalDays(0);
        adHoc.setAssignedTechnicianId(TECH);
        adHoc.setActive(true);
        when(scheduleRepository.findById(77L)).thenReturn(Optional.of(adHoc));

        var completed = service.completeSchedule(
                77L,
                new CompleteScheduleRequest(null, null, "PASSED", "Hoàn tất", null, null, null, null, null, null),
                TECH,
                false);

        assertEquals("PASSED", completed.lastResult());
        assertFalse(Boolean.TRUE.equals(adHoc.getActive()));
        assertEquals("ACTIVE", locker.getStatus());
        assertNull(locker.getMaintenanceSource());
    }

    @Test
    void oneTimeLockerMaintenanceScheduleOpensBlockingTicketOnFailAndRestoresWhenResolved() {
        LockerUnit locker = lockerRepository.findById(LOCKER_ID).orElseThrow();
        locker.setStatus("MAINTENANCE");
        locker.setMaintenanceSource("SCHEDULE");
        MaintenanceSchedule adHoc = new MaintenanceSchedule();
        adHoc.setId(88L);
        adHoc.setLockerId(LOCKER_ID);
        adHoc.setTitle("Bảo trì nguồn Kiosk");
        adHoc.setIntervalDays(0);
        adHoc.setAssignedTechnicianId(TECH);
        adHoc.setActive(true);
        when(scheduleRepository.findById(88L)).thenReturn(Optional.of(adHoc));

        service.completeSchedule(
                88L,
                new CompleteScheduleRequest(null, null, "FAILED", "Hỏng bộ nguồn", null, null, null, null, null, null),
                TECH,
                false);

        assertEquals(200L, adHoc.getPendingReportId());
        assertEquals("MAINTENANCE", locker.getStatus());
        ArgumentCaptor<LockerReport> reportCaptor = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository, atLeastOnce()).save(reportCaptor.capture());
        LockerReport opened = reportCaptor.getValue();
        assertTrue(Boolean.TRUE.equals(opened.getBlocksLocker()));

        opened.setStatus("IN_PROGRESS");
        opened.setAssignedToUserId(TECH);
        when(reportRepository.findById(200L)).thenReturn(Optional.of(opened));
        when(scheduleRepository.findByPendingReportId(200L)).thenReturn(List.of(adHoc));

        service.resolveReportAndClearFault(200L, TECH, null, false);

        assertNull(adHoc.getPendingReportId());
        assertFalse(Boolean.TRUE.equals(adHoc.getActive()));
        assertEquals("ACTIVE", locker.getStatus());
        assertNull(locker.getMaintenanceSource());
    }

    private MaintenanceInspectionLog savedLog() {
        ArgumentCaptor<MaintenanceInspectionLog> log = ArgumentCaptor.forClass(MaintenanceInspectionLog.class);
        verify(inspectionLogRepository, atLeastOnce()).save(log.capture());
        return log.getValue();
    }

    private static CompleteScheduleRequest items(String lock, String sensor, String screen) {
        return items(null, lock, sensor, screen);
    }

    private static CompleteScheduleRequest items(Long faultBoxId, String lock, String sensor, String screen) {
        return request(faultBoxId, List.of(
                new InspectionItemResult("Khoá điện", lock, null),
                new InspectionItemResult("Cảm biến cửa", sensor, "FAIL".equals(sensor) ? "Không nhận cửa" : null),
                new InspectionItemResult("Màn hình", screen, null)));
    }

    private static CompleteScheduleRequest request(Long faultBoxId, List<InspectionItemResult> items) {
        return new CompleteScheduleRequest(
                null, null, null, null, null, null, null, faultBoxId, null, items);
    }

    private static UserSummary user(Long id, String role) {
        return new UserSummary(id, null, null, "User " + id, "ACTIVE", Set.of(role));
    }
}
