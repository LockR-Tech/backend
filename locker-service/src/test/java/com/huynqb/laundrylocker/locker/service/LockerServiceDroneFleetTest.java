package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.OrderClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.DroneMaintenanceLogResponse;
import com.huynqb.laundrylocker.locker.dto.DroneUpdateRequest;
import com.huynqb.laundrylocker.locker.dto.DroneUnitResponse;
import com.huynqb.laundrylocker.locker.dto.LockerReportResponse;
import com.huynqb.laundrylocker.locker.model.DroneMaintenanceLog;
import com.huynqb.laundrylocker.locker.model.DroneStatus;
import com.huynqb.laundrylocker.locker.model.DroneUnit;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private DroneMaintenanceLogRepository droneMaintenanceLogRepository;
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
                        droneMaintenanceLogRepository,
                        iotClient,
                        userClient,
                        orderClient,
                        attachmentService,
                        rabbitTemplate,
                        new LockerRules(settings));

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
        return locker;
    }
}
