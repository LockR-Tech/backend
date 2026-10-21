package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.OrderClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.DroneMaintenanceLogResponse;
import com.huynqb.laundrylocker.locker.dto.CreateIncidentTicketsRequest;
import com.huynqb.laundrylocker.locker.dto.DroneUpdateRequest;
import com.huynqb.laundrylocker.locker.dto.DroneUnitResponse;
import com.huynqb.laundrylocker.locker.dto.IncidentTicketBundleResponse;
import com.huynqb.laundrylocker.locker.dto.LockerReportResponse;
import com.huynqb.laundrylocker.locker.model.DroneMaintenanceLog;
import com.huynqb.laundrylocker.locker.model.DroneStatus;
import com.huynqb.laundrylocker.locker.model.DroneUnit;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.model.MaintenanceSchedule;
import com.huynqb.laundrylocker.locker.model.ReportCategory;
import com.huynqb.laundrylocker.locker.repository.*;
import com.huynqb.laundrylocker.locker.settings.LockerRules;
import com.huynqb.laundrylocker.locker.settings.TestLockerRules;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceDroneFleetTest {

    @Mock
    private LockerUnitRepository lockerRepository;
    @Mock
    private LockerBoxRepository boxRepository;
    @Mock
    private LockerReportRepository reportRepository;
    @Mock
    private RepairLogRepository repairLogRepository;
    @Mock
    private MaintenanceScheduleRepository scheduleRepository;
    @Mock
    private MaintenanceInspectionLogRepository inspectionLogRepository;
    @Mock
    private LockerReportRatingRepository ratingRepository;
    @Mock
    private DroneUnitRepository droneUnitRepository;
    @Mock
    private DroneDeviceRepository droneDeviceRepository;
    @Mock
    private DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    @Mock
    private DroneIotLogRepository droneIotLogRepository;
    @Mock
    private IotClient iotClient;
    @Mock
    private UserClient userClient;
    @Mock
    private OrderClient orderClient;
    @Mock
    private ReportAttachmentService attachmentService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private TechnicianPresenceRepository presenceRepository;

    private LockerService service;

    private BusinessSettings settings;

    @BeforeEach
    void setUp() {
        settings = TestLockerRules.settings(Map.of());
        service =
                new LockerService(
                        lockerRepository,
                        boxRepository,
                        reportRepository,
                        repairLogRepository,
                        scheduleRepository,
                        inspectionLogRepository,
                        ratingRepository,
                        droneUnitRepository,
                        droneDeviceRepository,
                        droneMaintenanceLogRepository,
                        droneIotLogRepository,
                        iotClient,
                        userClient,
                        orderClient,
                        attachmentService,
                        rabbitTemplate,
                        new LockerRules(settings),
                        presenceRepository);

        when(droneUnitRepository.save(any(DroneUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, DroneUnit.class));
        when(droneMaintenanceLogRepository.save(any(DroneMaintenanceLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, DroneMaintenanceLog.class));
        when(lockerRepository.findById(10L)).thenReturn(Optional.of(locker(10L, "CAB-DEMO-01")));
        when(lockerRepository.findById(11L)).thenReturn(Optional.of(locker(11L, "CAB-DEMO-02")));
        when(userClient.getUser(42L))
                .thenReturn(ApiResponse.ok(new UserSummary(42L, "tech@test", "0909", "Tech A", "ACTIVE")));
    }

    @Test
    void claimDroneRejectsOverwritingAnotherTechnician() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.IDLE, 80)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.claimDrone(1L, 84L));

        assertEquals("DRONE_ALREADY_ASSIGNED", ex.getCode());
        verifyNoInteractions(droneMaintenanceLogRepository);
    }

    @Test
    void releaseDroneRequiresAssignedTechnicianOwnership() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.IDLE, 80)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.releaseDrone(1L, 84L));

        assertEquals("DRONE_OWNERSHIP_REQUIRED", ex.getCode());
        verifyNoInteractions(droneMaintenanceLogRepository);
    }

    @Test
    void updateDroneBatteryRequiresOwnershipAndAppendsAuditLog() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.CHARGING, 80)));

        DroneUnitResponse response = service.updateDroneBattery(1L, 100, 42L);

        assertEquals(100, response.batteryPercent());
        assertEquals(42L, response.assignedTechnicianId());
        assertNotNull(response.lastChargedAt());

        ArgumentCaptor<DroneMaintenanceLog> logCaptor = ArgumentCaptor.forClass(DroneMaintenanceLog.class);
        verify(droneMaintenanceLogRepository).save(logCaptor.capture());
        assertEquals("Cập nhật pin 100%", logCaptor.getValue().getNote());
        assertEquals(42L, logCaptor.getValue().getActorUserId());
        assertEquals(1L, logCaptor.getValue().getDroneUnitId());
    }

    @Test
    void updateDroneStatusRejectsActorWhoDoesNotOwnDrone() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.IDLE, 80)));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.updateDroneStatus(1L, DroneStatus.CHARGING, null, 84L));

        assertEquals("DRONE_OWNERSHIP_REQUIRED", ex.getCode());
        verifyNoInteractions(droneMaintenanceLogRepository);
    }

    @Test
    void adminCanUpdateStatusWithoutTakingTechnicianOwnership() {
        when(droneUnitRepository.findById(1L))
                .thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.IDLE, 80)));

        DroneUnitResponse response =
                service.updateDroneStatusAsAdmin(1L, DroneStatus.CHARGING, null, 7L);

        assertEquals(DroneStatus.CHARGING, response.status());
        assertEquals(42L, response.assignedTechnicianId());
        ArgumentCaptor<DroneMaintenanceLog> logCaptor = ArgumentCaptor.forClass(DroneMaintenanceLog.class);
        verify(droneMaintenanceLogRepository).save(logCaptor.capture());
        assertEquals(7L, logCaptor.getValue().getActorUserId());
    }

    @Test
    void adminBatteryUpdateKeepsTechnicianAssignmentAndWritesAuditLog() {
        when(droneUnitRepository.findById(1L))
                .thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.CHARGING, 40)));

        DroneUnitResponse response = service.updateDroneBatteryAsAdmin(1L, 95, 7L);

        assertEquals(95, response.batteryPercent());
        assertEquals(42L, response.assignedTechnicianId());
        ArgumentCaptor<DroneMaintenanceLog> logCaptor = ArgumentCaptor.forClass(DroneMaintenanceLog.class);
        verify(droneMaintenanceLogRepository).save(logCaptor.capture());
        assertEquals("Cập nhật pin 95%", logCaptor.getValue().getNote());
        assertEquals(7L, logCaptor.getValue().getActorUserId());
    }

    @Test
    void takeOffBatteryThresholdFollowsAdminSetting() {
        when(droneUnitRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.RESERVED, 80)));
        settings.update(Map.of("app.locker.drone-low-battery-percent", 85), null);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.transitionDroneStatusInternal(
                                1L, DroneStatus.RESERVED, DroneStatus.IN_FLIGHT, null));

        assertEquals("DRONE_BATTERY_TOO_LOW", ex.getCode());

        settings.update(Map.of("app.locker.drone-low-battery-percent", 50), null);
        assertEquals(
                DroneStatus.IN_FLIGHT,
                service.transitionDroneStatusInternal(
                                1L, DroneStatus.RESERVED, DroneStatus.IN_FLIGHT, null)
                        .status());
    }

    @Test
    void operatorCannotOverrideWorkflowManagedStatuses() {
        when(droneUnitRepository.findById(1L))
                .thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.RESERVED, 80)));

        BusinessException releaseError = assertThrows(
                BusinessException.class,
                () -> service.updateDroneStatus(1L, DroneStatus.IDLE, null, 42L));
        BusinessException launchError = assertThrows(
                BusinessException.class,
                () -> service.updateDroneStatus(1L, DroneStatus.IN_FLIGHT, null, 42L));

        assertEquals("DRONE_ACTIVE_MISSION", releaseError.getCode());
        assertEquals("DRONE_STATUS_WORKFLOW_MANAGED", launchError.getCode());
        verify(droneUnitRepository, never()).save(any());
    }

    @Test
    void adminCannotOverrideWorkflowManagedStatuses() {
        when(droneUnitRepository.findById(1L))
                .thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.RESERVED, 80)));

        BusinessException releaseError = assertThrows(
                BusinessException.class,
                () -> service.updateDroneStatusAsAdmin(1L, DroneStatus.IDLE, null, 7L));
        BusinessException launchError = assertThrows(
                BusinessException.class,
                () -> service.updateDroneStatusAsAdmin(1L, DroneStatus.IN_FLIGHT, null, 7L));

        assertEquals("DRONE_ACTIVE_MISSION", releaseError.getCode());
        assertEquals("DRONE_STATUS_WORKFLOW_MANAGED", launchError.getCode());
        verify(droneUnitRepository, never()).save(any());
    }

    @Test
    void adminCannotEditOrDecommissionDroneWithActiveMission() {
        when(droneUnitRepository.findById(1L))
                .thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.IN_FLIGHT, 80)));

        BusinessException editError = assertThrows(
                BusinessException.class,
                () -> service.updateDroneUnit(1L, new DroneUpdateRequest(10L, "DRONE-02")));
        BusinessException decommissionError = assertThrows(
                BusinessException.class,
                () -> service.decommissionDrone(1L, 7L));

        assertEquals("DRONE_ACTIVE_MISSION", editError.getCode());
        assertEquals("DRONE_ACTIVE_MISSION", decommissionError.getCode());
        verify(droneUnitRepository, never()).save(any());
    }

    @Test
    void changingDroneTechnicianDoesNotRewriteExistingReportsOrdersOrSchedules() {
        DroneUnit unit = droneUnit(1L, 42L, DroneStatus.IDLE, 80);
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(userClient.getUser(84L))
                .thenReturn(ApiResponse.ok(new UserSummary(
                        84L, "drone-tech@test", "0908", "Drone Tech", "ACTIVE", Set.of("DRONE_TECHNICIAN"))));

        DroneUnitResponse response = service.assignDroneTechnician(1L, 84L, 7L);

        assertEquals(84L, response.assignedTechnicianId());
        verifyNoInteractions(reportRepository, scheduleRepository, orderClient);
    }

    @Test
    void changingDroneStationDoesNotRewriteExistingReportsOrdersOrSchedules() {
        DroneUnit unit = droneUnit(1L, 42L, DroneStatus.IDLE, 80);
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(unit));

        DroneUnitResponse response = service.updateDroneUnit(1L, new DroneUpdateRequest(11L, null), 7L);

        assertEquals(11L, response.lockerId());
        verifyNoInteractions(reportRepository, scheduleRepository, orderClient);
    }

    @Test
    void assignedDroneReportsIncludesLegacyReportLinkedByDroneUnit() {
        LockerReport report = droneReport(24L, 3L);
        report.setCategory(null);
        when(reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(3L))
                .thenReturn(List.of(report));
        when(attachmentService.byReportIds(any())).thenReturn(Map.of());
        when(droneUnitRepository.findById(7L))
                .thenReturn(Optional.of(droneUnit(7L, 3L, DroneStatus.FAULT, 40)));

        List<LockerReportResponse> reports = service.assignedDroneReports(3L);

        assertEquals(1, reports.size());
        assertEquals(24L, reports.getFirst().id());
        assertEquals(7L, reports.getFirst().droneUnitId());
        assertEquals(3L, reports.getFirst().assignedToUserId());
    }

    @Test
    void allDroneReportsExcludesKioskReports() {
        LockerReport drone = droneReport(24L, 3L);
        LockerReport kiosk = new LockerReport();
        kiosk.setId(25L);
        kiosk.setLockerId(10L);
        kiosk.setUserId(1L);
        kiosk.setTitle("Kiosk fault");
        kiosk.setDescription("Door fault");
        kiosk.setStatus("OPEN");
        kiosk.setCategory(ReportCategory.BOX);
        kiosk.setCreatedAt(LocalDateTime.now());
        when(reportRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(kiosk, drone));
        when(attachmentService.byReportIds(any())).thenReturn(Map.of());
        when(droneUnitRepository.findById(7L))
                .thenReturn(Optional.of(droneUnit(7L, 3L, DroneStatus.FAULT, 40)));

        List<LockerReportResponse> reports = service.allDroneReports();

        assertEquals(List.of(24L), reports.stream().map(LockerReportResponse::id).toList());
    }

    @Test
    void internalReservationUsesLockedCompareAndSet() {
        when(droneUnitRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(droneUnit(1L, null, DroneStatus.IDLE, 80)));

        DroneUnitResponse response = service.transitionDroneStatusInternal(
                1L, DroneStatus.IDLE, DroneStatus.RESERVED, "Reserved for order ORD-21");

        assertEquals(DroneStatus.RESERVED, response.status());
        verify(droneUnitRepository).findByIdForUpdate(1L);
    }

    @Test
    void internalReservationRejectsStaleExpectedStatus() {
        when(droneUnitRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(droneUnit(1L, null, DroneStatus.RESERVED, 80)));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.transitionDroneStatusInternal(
                        1L, DroneStatus.IDLE, DroneStatus.RESERVED, "Reserved for another order"));

        assertEquals("DRONE_STATUS_CONFLICT", error.getCode());
        verify(droneUnitRepository, never()).save(any());
    }

    @Test
    void parcelDropCreatesOneDroneInspectionAndSelectsNearestLockerRecoveryTechnician() {
        DroneUnit drone = droneUnit(7L, 42L, DroneStatus.FAULT, 70);
        LockerUnit fpt = locker(10L, "CAB-DEMO-01");
        fpt.setStatus("ACTIVE");
        fpt.setName("Tủ FPT University HCMC");
        fpt.setLatitude(10.8411);
        fpt.setLongitude(106.8097);
        fpt.setAssignedTechnicianId(84L);
        LockerUnit farther = locker(11L, "CAB-VGP-01");
        farther.setStatus("ACTIVE");
        farther.setLatitude(10.8429);
        farther.setLongitude(106.8361);
        farther.setAssignedTechnicianId(85L);
        when(droneUnitRepository.findById(7L)).thenReturn(Optional.of(drone));
        when(lockerRepository.findAll()).thenReturn(List.of(farther, fpt));
        when(reportRepository.findByExternalIncidentIdAndTicketType(any(), any()))
                .thenReturn(Optional.empty());
        when(reportRepository.findByAssignedToUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(userClient.getUser(42L)).thenReturn(ApiResponse.ok(
                new UserSummary(42L, "drone@test", "0901", "KTV Drone", "ACTIVE", Set.of("DRONE_TECHNICIAN"))));
        when(userClient.getUser(84L)).thenReturn(ApiResponse.ok(
                new UserSummary(84L, "locker@test", "0902", "KTV Tủ FPT", "ACTIVE", Set.of("LOCKER_TECHNICIAN"))));
        when(userClient.getUser(85L)).thenReturn(ApiResponse.ok(
                new UserSummary(85L, "far@test", "0903", "KTV Tủ Xa", "ACTIVE", Set.of("LOCKER_TECHNICIAN"))));
        AtomicLong ids = new AtomicLong(100);
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> {
            LockerReport report = invocation.getArgument(0);
            report.setId(ids.incrementAndGet());
            return report;
        });

        IncidentTicketBundleResponse result = service.createIncidentTickets(new CreateIncidentTicketsRequest(
                89L, "DPI-89", 89L, "ORD-89", 7L, "DRONE-01", 3L,
                "Kiện rơi khỏi khoang", 10.8411, 106.8097, null, null));

        ArgumentCaptor<LockerReport> reports = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository).save(reports.capture());
        LockerReport inspection = reports.getValue();
        assertEquals("DRONE_INSPECTION", inspection.getTicketType());
        assertEquals("DPI-89", inspection.getIncidentCode());
        assertEquals(42L, inspection.getAssignedToUserId());
        assertEquals(ReportCategory.DRONE, inspection.getCategory());
        assertEquals(84L, result.recoveryTechnicianId());
        assertEquals(10L, result.recoveryLockerId());
    }

    @Test
    void parcelDropFaultTransitionCanSuppressTheGenericDroneFaultReport() {
        when(droneUnitRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(droneUnit(7L, 42L, DroneStatus.IN_FLIGHT, 70)));

        service.transitionDroneStatusInternal(
                7L, DroneStatus.IN_FLIGHT, DroneStatus.FAULT, "Parcel drop incident DPI-89", false);

        verify(reportRepository, never()).save(any(LockerReport.class));
    }

    // ─── Quy tắc đổi trạng thái tay ───

    @Test
    void leavingFaultWhileReportIsOpenIsRejected() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.FAULT, 80)));
        LockerReport open = droneReport(24L, 42L);
        when(reportRepository.findFirstByDroneUnitIdAndStatusInOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(Optional.of(open));

        BusinessException idle = assertThrows(BusinessException.class,
                () -> service.updateDroneStatus(1L, DroneStatus.IDLE, null, 42L));
        BusinessException charging = assertThrows(BusinessException.class,
                () -> service.updateDroneStatusAsAdmin(1L, DroneStatus.CHARGING, null, 7L));

        assertEquals("DRONE_HAS_OPEN_REPORT", idle.getCode());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, idle.getStatus());
        assertEquals("DRONE_HAS_OPEN_REPORT", charging.getCode());
        assertEquals("IN_PROGRESS", open.getStatus());
        verify(droneUnitRepository, never()).save(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void leavingFaultWithoutOpenReportDoesNotTouchReports() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.FAULT, 80)));

        DroneUnitResponse response = service.updateDroneStatus(1L, DroneStatus.IDLE, null, 42L);

        assertEquals(DroneStatus.IDLE, response.status());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void markingInFlightDroneFaultyMustUseTheOrderFailFlow() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.IN_FLIGHT, 80)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateDroneStatusAsAdmin(1L, DroneStatus.FAULT, "Mất tín hiệu", 7L));

        assertEquals("DRONE_IN_FLIGHT_USE_FAIL", ex.getCode());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        verify(droneUnitRepository, never()).save(any());
    }

    // ─── Thông báo cho KTV drone ───

    @Test
    void faultReportNotifiesRoutedDroneTechnicianWithDroneType() {
        stubReportSave();
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 84L, DroneStatus.IDLE, 80)));

        service.updateDroneStatusAsAdmin(1L, DroneStatus.FAULT, "Cánh quạt nứt", 7L);

        List<DomainEvent> events = staffEvents(DomainEventNames.LOCKER_REPORT_ROUTED);
        assertEquals(1, events.size());
        assertEquals(LockerService.DRONE_REPORT_ROUTED, events.getFirst().type());
        assertEquals(84L, events.getFirst().payload().get("userId"));
        assertEquals(500L, events.getFirst().payload().get("referenceId"));
        assertEquals("DRONE_REPORT", events.getFirst().payload().get("referenceType"));
    }

    @Test
    void unroutedFaultReportNotifiesEveryDroneTechnicianExceptTheReporter() {
        stubReportSave();
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, null, DroneStatus.IDLE, 80)));
        when(userClient.listByRole("DRONE_TECHNICIAN")).thenReturn(ApiResponse.ok(List.of(
                new UserSummary(84L, "a@test", "0901", "A", "ACTIVE"),
                new UserSummary(85L, "b@test", "0902", "B", "ACTIVE"),
                new UserSummary(7L, "admin@test", "0903", "Admin", "ACTIVE"))));

        service.updateDroneStatusAsAdmin(1L, DroneStatus.FAULT, "Pin phồng", 7L);

        assertEquals(Set.of(84L, 85L), Set.copyOf(staffEvents(DomainEventNames.LOCKER_REPORT_ROUTED).stream()
                .map(event -> event.payload().get("userId")).toList()));
    }

    @Test
    void adminAssigningDroneReportUsesDroneAssignedType() {
        LockerReport report = droneReport(24L, null);
        report.setStatus("OPEN");
        when(reportRepository.findById(24L)).thenReturn(Optional.of(report));
        stubReportSave();
        when(attachmentService.byReportIds(any())).thenReturn(Map.of());
        when(droneUnitRepository.findById(7L)).thenReturn(Optional.of(droneUnit(7L, null, DroneStatus.FAULT, 40)));
        when(userClient.getUser(84L)).thenReturn(ApiResponse.ok(new UserSummary(
                84L, "drone-tech@test", "0908", "Drone Tech", "ACTIVE", Set.of("DRONE_TECHNICIAN"))));

        service.assignReport(24L, 84L);

        List<DomainEvent> events = staffEvents(DomainEventNames.LOCKER_REPORT_ASSIGNED);
        assertEquals(1, events.size());
        assertEquals(LockerService.DRONE_REPORT_ASSIGNED, events.getFirst().type());
        assertEquals(24L, events.getFirst().payload().get("referenceId"));
    }

    @Test
    void droneScheduleReminderUsesDroneScheduleType() {
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setId(31L);
        schedule.setDroneUnitId(1L);
        schedule.setTitle("Kiểm tra cánh quạt");
        schedule.setAssignedTechnicianId(84L);
        schedule.setNextDueAt(LocalDateTime.now().minusHours(1));
        when(scheduleRepository.findByActiveTrueAndLastDueNotifiedAtIsNullAndNextDueAtBefore(any()))
                .thenReturn(List.of(schedule));
        when(scheduleRepository.markDueNotified(eq(31L), any())).thenReturn(1);
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 84L, DroneStatus.IDLE, 80)));

        assertEquals(1, service.remindDueSchedules());

        List<DomainEvent> events = staffEvents(DomainEventNames.LOCKER_SCHEDULE_DUE);
        assertEquals(1, events.size());
        assertEquals(LockerService.DRONE_SCHEDULE_DUE, events.getFirst().type());
        assertEquals(31L, events.getFirst().payload().get("referenceId"));
    }

    private void stubReportSave() {
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> {
            LockerReport report = invocation.getArgument(0, LockerReport.class);
            if (report.getId() == null) {
                report.setId(500L);
            }
            return report;
        });
    }

    private List<DomainEvent> staffEvents(String routingKey) {
        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(rabbitTemplate, atLeast(0)).convertAndSend(eq(DomainEventNames.EXCHANGE), eq(routingKey), events.capture());
        return events.getAllValues();
    }

    @Test
    void addDroneLogTrimsNoteAndRejectsBlankInput() {
        when(droneUnitRepository.findById(1L)).thenReturn(Optional.of(droneUnit(1L, 42L, DroneStatus.MAINTENANCE, 80)));

        DroneMaintenanceLogResponse saved = service.addDroneLog(1L, "  Da thay canh quat  ", 42L);

        assertEquals("Da thay canh quat", saved.note());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.addDroneLog(1L, "   ", 42L));

        assertEquals("DRONE_LOG_NOTE_REQUIRED", ex.getCode());
    }

    private DroneUnit droneUnit(Long id, Long assignedTechnicianId, String status, int batteryPercent) {
        DroneUnit unit = new DroneUnit();
        unit.setId(id);
        unit.setLockerId(10L);
        unit.setCode("DRONE-01");
        unit.setAssignedTechnicianId(assignedTechnicianId);
        unit.setStatus(status);
        unit.setBatteryPercent(batteryPercent);
        unit.setActive(true);
        return unit;
    }

    private LockerReport droneReport(Long id, Long assignedTechnicianId) {
        LockerReport report = new LockerReport();
        report.setId(id);
        report.setLockerId(10L);
        report.setDroneUnitId(7L);
        report.setUserId(1L);
        report.setTitle("Drone motor fault");
        report.setDescription("Motor is weak");
        report.setStatus("IN_PROGRESS");
        report.setCategory(ReportCategory.DRONE);
        report.setAssignedToUserId(assignedTechnicianId);
        report.setAssignedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        return report;
    }

    private LockerUnit locker(Long id, String code) {
        LockerUnit locker = new LockerUnit();
        locker.setId(id);
        locker.setCode(code);
        locker.setName("Demo locker");
        locker.setLandingPad(true);
        return locker;
    }
}
