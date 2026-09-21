package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
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
    @Mock private DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    @Mock private IotClient iotClient;
    @Mock private UserClient userClient;
    @Mock private ReportAttachmentService attachmentService;
    @Mock private RabbitTemplate rabbitTemplate;

    private LockerService service;
    private MaintenanceSchedule schedule;
    private LocalDateTime originalDue;

    @BeforeEach
    void setUp() {
        service = new LockerService(
                lockerRepository, boxRepository, reportRepository, repairLogRepository, scheduleRepository,
                inspectionLogRepository, ratingRepository, droneUnitRepository, droneMaintenanceLogRepository,
                iotClient, userClient, attachmentService, rabbitTemplate, TestLockerRules.defaults());
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

    // Lịch drone là việc của KTV drone ⇒ không mở phiếu tủ, vẫn dời hạn như cũ.
    @Test
    void droneScheduleFailureDoesNotOpenALockerTicket() {
        schedule.setLockerId(null);
        schedule.setDroneUnitId(8L);

        service.completeSchedule(SCHEDULE_ID, items("PASS", "FAIL", "PASS"), TECH, false);

        assertEquals("FAILED", schedule.getLastResult());
        assertNull(schedule.getPendingReportId());
        assertTrue(schedule.getNextDueAt().isAfter(LocalDateTime.now().plusDays(29)));
        verify(reportRepository, never()).save(any(LockerReport.class));
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
