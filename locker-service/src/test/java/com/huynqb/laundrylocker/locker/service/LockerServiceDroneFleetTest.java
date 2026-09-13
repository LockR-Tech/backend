package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.DroneMaintenanceLogResponse;
import com.huynqb.laundrylocker.locker.dto.DroneUnitResponse;
import com.huynqb.laundrylocker.locker.model.DroneMaintenanceLog;
import com.huynqb.laundrylocker.locker.model.DroneStatus;
import com.huynqb.laundrylocker.locker.model.DroneUnit;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
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

import java.util.Optional;

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
    private RabbitTemplate rabbitTemplate;

    private LockerService service;

    @BeforeEach
    void setUp() {
        service =
                new LockerService(
                        lockerRepository,
                        boxRepository,
                        reportRepository,
                        repairLogRepository,
                        scheduleRepository,
                        ratingRepository,
                        droneUnitRepository,
                        droneMaintenanceLogRepository,
                        iotClient,
                        userClient,
                        rabbitTemplate);

        when(droneUnitRepository.save(any(DroneUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, DroneUnit.class));
        when(droneMaintenanceLogRepository.save(any(DroneMaintenanceLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, DroneMaintenanceLog.class));
        when(lockerRepository.findById(10L)).thenReturn(Optional.of(locker(10L, "CAB-DEMO-01")));
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

    private LockerUnit locker(Long id, String code) {
        LockerUnit locker = new LockerUnit();
        locker.setId(id);
        locker.setCode(code);
        locker.setName("Demo locker");
        return locker;
    }
}
