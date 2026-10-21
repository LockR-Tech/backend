package com.huynqb.laundrylocker.locker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.OrderClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.ExtendSlaRequest;
import com.huynqb.laundrylocker.locker.dto.LockerReportResponse;
import com.huynqb.laundrylocker.locker.dto.RepairLogResponse;
import com.huynqb.laundrylocker.locker.model.DroneUnit;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.model.RepairLog;
import com.huynqb.laundrylocker.locker.model.ReportCategory;
import com.huynqb.laundrylocker.locker.repository.*;
import com.huynqb.laundrylocker.locker.settings.LockerRules;
import com.huynqb.laundrylocker.locker.settings.LockerSettingsCatalog;
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
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/// Gia hạn SLA của KTV (giới hạn theo cấu hình nghiệp vụ), trả phiếu về hàng chờ, tên KTV/người ghi nhật ký.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceSlaAndQueueTest {

    private static final long LOCKER_ID = 10L;
    private static final long TECH = 42L;
    private static final long OTHER_TECH = 43L;
    private static final long ADMIN = 1L;

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
    private BusinessSettings settings;
    private LockerUnit locker;
    private LockerReport report;

    @BeforeEach
    void setUp() {
        settings = TestLockerRules.settings(Map.of());
        service = new LockerService(
                lockerRepository, boxRepository, reportRepository, repairLogRepository, scheduleRepository,
                inspectionLogRepository, ratingRepository, droneUnitRepository, droneDeviceRepository, droneMaintenanceLogRepository,
                droneIotLogRepository,
                iotClient, userClient, orderClient, attachmentService, rabbitTemplate, new LockerRules(settings),
                presenceRepository);
        locker = new LockerUnit();
        locker.setId(LOCKER_ID);
        locker.setName("Tủ A");
        locker.setCode("CAB-A");
        report = new LockerReport();
        report.setId(100L);
        report.setLockerId(LOCKER_ID);
        report.setUserId(7L);
        report.setTitle("Ô #3 báo hỏng");
        report.setDescription("Cửa kẹt");
        report.setCategory(ReportCategory.BOX);
        report.setStatus("IN_PROGRESS");
        report.setAssignedToUserId(TECH);
        report.setCreatedAt(LocalDateTime.now().minusHours(1));
        report.setSlaDueAt(LocalDateTime.now().plusHours(3));
        when(lockerRepository.findById(LOCKER_ID)).thenReturn(Optional.of(locker));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairLogRepository.save(any(RepairLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentService.byReportIds(any())).thenReturn(Map.of());
        when(attachmentService.byRepairLogIds(any())).thenReturn(Map.of());
        when(userClient.getUser(TECH)).thenReturn(ApiResponse.ok(user(TECH, "Nguyễn Văn KTV")));
        when(userClient.getUser(OTHER_TECH)).thenReturn(ApiResponse.ok(user(OTHER_TECH, "Trần Thị KTV")));
        when(userClient.getUser(7L)).thenReturn(ApiResponse.ok(user(7L, "Khách A")));
    }

    // ─── Gia hạn SLA ───

    @Test
    void assignedTechnicianExtendsWithinLimitAndCountIsRecorded() {
        LocalDateTime due = report.getSlaDueAt();

        LockerReportResponse response = service.extendSlaAsTechnician(100L, 6, "Chờ linh kiện thay thế", TECH);

        assertEquals(due.plusHours(6), report.getSlaDueAt());
        assertEquals(1, report.getSlaExtensionCount());
        assertEquals(1, response.slaExtensionsUsed());
        assertEquals(2, response.slaExtensionsMax());
        assertEquals("Chờ linh kiện thay thế", report.getSlaExtensionReason());
        RepairLog log = savedLog();
        assertTrue(log.getNote().startsWith("[GIA HẠN SLA] KTV gia hạn thêm +6 giờ (lần 1/2)."));
        assertFalse(log.getNote().contains("phê duyệt"));
        assertEquals(TECH, log.getActorUserId());
    }

    @Test
    void onlyTheAssigneeMayExtend() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.extendSlaAsTechnician(100L, 2, "Lý do", OTHER_TECH));

        assertEquals("SLA_EXTENSION_NOT_ASSIGNEE", ex.getCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void reasonIsRequired() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.extendSlaAsTechnician(100L, 2, "  ", TECH));

        assertEquals("SLA_EXTENSION_REASON_REQUIRED", ex.getCode());
    }

    @Test
    void hoursPerExtensionFollowAdminSetting() {
        settings.update(Map.of(LockerSettingsCatalog.SLA_MAX_HOURS_PER_EXTENSION, 8), null);
        LocalDateTime due = report.getSlaDueAt();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.extendSlaAsTechnician(100L, 9, "Lý do", TECH));

        assertEquals("SLA_EXTENSION_HOURS_EXCEEDED", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(due, report.getSlaDueAt());
        assertEquals(due.plusHours(8), service.extendSlaAsTechnician(100L, 8, "Lý do", TECH).slaDueAt());
    }

    @Test
    void extensionCountIsCappedByAdminSetting() {
        report.setSlaExtensionCount(2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.extendSlaAsTechnician(100L, 2, "Lý do", TECH));

        assertEquals("SLA_EXTENSION_LIMIT_REACHED", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());

        settings.update(Map.of(LockerSettingsCatalog.SLA_MAX_EXTENSIONS, 3), null);
        assertEquals(3, service.extendSlaAsTechnician(100L, 2, "Lý do", TECH).slaExtensionsUsed());
    }

    @Test
    void adminExtensionDoesNotUseTechnicianQuota() {
        LockerReportResponse response = service.extendSla(100L, 48, null, ADMIN);

        assertEquals(0, response.slaExtensionsUsed());
        assertTrue(savedLog().getNote().startsWith("[GIA HẠN SLA] Admin gia hạn thêm +48 giờ."));
    }

    @Test
    void settingsCatalogExposesExtensionLimitsWithDefaults() {
        LockerRules rules = TestLockerRules.defaults();

        assertEquals(2, rules.slaMaxExtensions());
        assertEquals(24, rules.slaMaxHoursPerExtension());
    }

    @Test
    void extendRequestAcceptsHoursAndLegacyExtensionHours() throws Exception {
        ObjectMapper json = new ObjectMapper();

        assertEquals(5, json.readValue("{\"hours\":5,\"reason\":\"x\"}", ExtendSlaRequest.class).hours());
        assertEquals(7, json.readValue("{\"extensionHours\":7}", ExtendSlaRequest.class).hours());
    }

    // ─── Trả phiếu về hàng chờ ───

    @Test
    void assigneeReleasesTicketBackToQueueWithReason() {
        locker.setAssignedTechnicianId(OTHER_TECH);

        LockerReportResponse response = service.releaseReport(100L, TECH, "Cần KTV có dụng cụ chuyên dụng", false);

        assertEquals("OPEN", response.status());
        assertNull(report.getAssignedToUserId());
        assertNull(report.getAssignedAt());
        assertEquals(OTHER_TECH, report.getRoutedToUserId());
        assertTrue(savedLog().getNote().contains("Cần KTV có dụng cụ chuyên dụng"));
        verify(rabbitTemplate).convertAndSend(
                eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.LOCKER_REPORT_ROUTED), any(DomainEvent.class));
    }

    @Test
    void releasingTechnicianIsNotRoutedTheTicketAgain() {
        locker.setAssignedTechnicianId(TECH);
        when(userClient.listByRole("LOCKER_TECHNICIAN"))
                .thenReturn(ApiResponse.ok(List.of(user(TECH, "A"), user(OTHER_TECH, "B"))));

        service.releaseReport(100L, TECH, "Hết ca", false);

        assertNull(report.getRoutedToUserId());
        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.LOCKER_REPORT_ROUTED),
                events.capture());
        assertEquals(OTHER_TECH, events.getValue().payload().get("userId"));
    }

    @Test
    void releaseRequiresAssigneeAndReason() {
        BusinessException notMine = assertThrows(BusinessException.class,
                () -> service.releaseReport(100L, OTHER_TECH, "Lý do", false));
        assertEquals("REPORT_NOT_ASSIGNED", notMine.getCode());

        BusinessException noReason = assertThrows(BusinessException.class,
                () -> service.releaseReport(100L, TECH, " ", false));
        assertEquals("REPORT_RELEASE_REASON_REQUIRED", noReason.getCode());
        assertEquals(TECH, report.getAssignedToUserId());
    }

    @Test
    void droneTicketReleaseNotifiesDroneTeamWithDroneType() {
        report.setCategory(ReportCategory.DRONE);
        report.setDroneUnitId(5L);
        DroneUnit drone = new DroneUnit();
        drone.setId(5L);
        drone.setCode("DRONE-05");
        drone.setLockerId(LOCKER_ID);
        drone.setAssignedTechnicianId(TECH);
        when(droneUnitRepository.findById(5L)).thenReturn(Optional.of(drone));
        when(userClient.listByRole("DRONE_TECHNICIAN")).thenReturn(ApiResponse.ok(List.of(user(OTHER_TECH, "B"))));

        service.releaseReport(100L, TECH, "Không có linh kiện", false);

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.LOCKER_REPORT_ROUTED),
                events.capture());
        assertEquals(LockerService.DRONE_REPORT_ROUTED, events.getValue().type());
        assertEquals(100L, events.getValue().payload().get("referenceId"));
        assertEquals(OTHER_TECH, events.getValue().payload().get("userId"));
    }

    // ─── Tên KTV / người ghi nhật ký ───

    @Test
    void reportCarriesAssignedTechnicianName() {
        LockerReportResponse response = service.getReport(100L);

        assertEquals("Nguyễn Văn KTV", response.assignedToTechnicianName());
        assertEquals("Khách A", response.reporterName());
    }

    @Test
    void repairLogsCarryAuthorNamesResolvedOncePerUser() {
        when(repairLogRepository.findByReportIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(
                log(1L, TECH), log(2L, TECH), log(3L, OTHER_TECH), log(4L, null)));

        List<RepairLogResponse> logs = service.repairLogs(100L);

        assertEquals("Nguyễn Văn KTV", logs.get(0).actorName());
        assertEquals("Nguyễn Văn KTV", logs.get(1).actorName());
        assertEquals("Trần Thị KTV", logs.get(2).actorName());
        assertNull(logs.get(3).actorName());
        verify(userClient, times(1)).getUser(TECH);
    }

    private RepairLog savedLog() {
        ArgumentCaptor<RepairLog> saved = ArgumentCaptor.forClass(RepairLog.class);
        verify(repairLogRepository).save(saved.capture());
        return saved.getValue();
    }

    private static RepairLog log(Long id, Long actor) {
        RepairLog log = new RepairLog();
        log.setId(id);
        log.setReportId(100L);
        log.setActorUserId(actor);
        log.setNote("Ghi chú " + id);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private static UserSummary user(Long id, String name) {
        return new UserSummary(id, "u" + id + "@test", "090" + id, name, "ACTIVE", Set.of("LOCKER_TECHNICIAN"));
    }
}
