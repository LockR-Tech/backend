package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.dto.AssignGatewayRequest;
import com.huynqb.laundrylocker.iot.dto.CommissioningStatusResponse;
import com.huynqb.laundrylocker.iot.dto.GatewayDeviceResponse;
import com.huynqb.laundrylocker.iot.model.CabinetControlLog;
import com.huynqb.laundrylocker.iot.model.CabinetHardwareConfig;
import com.huynqb.laundrylocker.iot.model.GatewayDevice;
import com.huynqb.laundrylocker.iot.repository.CabinetControlLogRepository;
import com.huynqb.laundrylocker.iot.repository.CabinetHardwareConfigRepository;
import com.huynqb.laundrylocker.iot.repository.GatewayDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CabinetCommissioningServiceTest {

    private static final Long LOCKER = 7L;
    private static final Long TECH = 42L;
    private static final String MAC = "B8:27:EB:12:34:56";

    private GatewayDeviceRepository gateways;
    private GatewayProvisioningService provisioning;
    private CabinetHardwareConfigRepository hardwareConfigs;
    private CabinetControlLogRepository controlLogs;
    private CabinetControlService control;
    private CabinetCommissioningService service;

    @BeforeEach
    void setUp() {
        gateways = mock(GatewayDeviceRepository.class);
        provisioning = mock(GatewayProvisioningService.class);
        hardwareConfigs = mock(CabinetHardwareConfigRepository.class);
        controlLogs = mock(CabinetControlLogRepository.class);
        control = mock(CabinetControlService.class);
        service = new CabinetCommissioningService(gateways, provisioning, hardwareConfigs, controlLogs, control);
    }

    private static GatewayDevice device(Long id, Long lockerId) {
        GatewayDevice device = new GatewayDevice();
        device.setId(id);
        device.setMacAddress(MAC);
        device.setLockerId(lockerId);
        return device;
    }

    private static GatewayDeviceResponse view(Long id, Long lockerId, boolean online) {
        return new GatewayDeviceResponse(id, MAC, "GPIO", "1.0", 1, 7, null, lockerId, online,
                "NONE", null, null, null, null, LocalDateTime.now());
    }

    @Test
    void qrFromTheKioskBindsTheControllerAndStartsTheSetupWithDoorTest() {
        GatewayDevice device = device(3L, null);
        when(gateways.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        when(provisioning.toResponse(device)).thenReturn(view(3L, null, true));
        when(provisioning.assign(eq(3L), any())).thenReturn(view(3L, LOCKER, true));

        service.bind(LOCKER, "lockr-pi:b8-27-eb-12-34-56", TECH, "LOCKER_TECHNICIAN");

        verify(control).requireLockerInScope(LOCKER, TECH, "LOCKER_TECHNICIAN");
        verify(provisioning).assign(3L, new AssignGatewayRequest(LOCKER, true));
    }

    @Test
    void invalidMacIsRejectedBeforeLookingUpDevices() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(LOCKER, "LOCKR-PI:not-a-mac", TECH, "LOCKER_TECHNICIAN"));
        assertEquals("CONTROLLER_MAC_INVALID", ex.getCode());
        verify(gateways, never()).findByMacAddress(any());
    }

    @Test
    void controllerNeverDiscoveredCannotBeBound() {
        when(gateways.findByMacAddress(MAC)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(LOCKER, MAC, TECH, "LOCKER_TECHNICIAN"));
        assertEquals("CONTROLLER_NOT_DISCOVERED", ex.getCode());
    }

    @Test
    void controllerOfAnotherLockerIsNotTakenOver() {
        GatewayDevice device = device(3L, 99L);
        when(gateways.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(LOCKER, MAC, TECH, "LOCKER_TECHNICIAN"));
        assertEquals("CONTROLLER_ASSIGNED_ELSEWHERE", ex.getCode());
        verify(provisioning, never()).assign(anyLong(), any());
    }

    @Test
    void offlineControllerIsNotBound() {
        GatewayDevice device = device(3L, null);
        when(gateways.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        when(provisioning.toResponse(device)).thenReturn(view(3L, null, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(LOCKER, MAC, TECH, "LOCKER_TECHNICIAN"));
        assertEquals("CONTROLLER_OFFLINE", ex.getCode());
    }

    @Test
    void technicianOutsideTheLockerScopeCannotBind() {
        doThrow(new BusinessException("CABINET_FORBIDDEN", "no")).when(control)
                .requireLockerInScope(LOCKER, TECH, "LOCKER_TECHNICIAN");
        assertThrows(BusinessException.class, () -> service.bind(LOCKER, MAC, TECH, "LOCKER_TECHNICIAN"));
        verify(gateways, never()).findByMacAddress(any());
    }

    @Test
    void candidatesAreOnlineControllersFreeOrAlreadyOnThisLocker() {
        GatewayDevice free = device(1L, null);
        GatewayDevice mine = device(2L, LOCKER);
        GatewayDevice other = device(3L, 99L);
        GatewayDevice offline = device(4L, null);
        when(gateways.findAllByOrderByIdAsc()).thenReturn(List.of(free, mine, other, offline));
        when(provisioning.toResponse(free)).thenReturn(view(1L, null, true));
        when(provisioning.toResponse(mine)).thenReturn(view(2L, LOCKER, true));
        when(provisioning.toResponse(other)).thenReturn(view(3L, 99L, true));
        when(provisioning.toResponse(offline)).thenReturn(view(4L, null, false));

        List<GatewayDeviceResponse> list = service.candidates(LOCKER, TECH, "LOCKER_TECHNICIAN");

        assertEquals(List.of(1L, 2L), list.stream().map(GatewayDeviceResponse::id).toList());
    }

    @Test
    void statusCarriesControllerHardwareApplyAndTestCommandsSinceTheStart() {
        GatewayDevice device = device(3L, LOCKER);
        when(gateways.findByLockerId(LOCKER)).thenReturn(Optional.of(device));
        when(provisioning.toResponse(device)).thenReturn(view(3L, LOCKER, true));
        CabinetHardwareConfig hw = new CabinetHardwareConfig();
        hw.setLockerId(LOCKER);
        hw.setApplyStatus("ACCEPTED");
        when(hardwareConfigs.findById(LOCKER)).thenReturn(Optional.of(hw));
        CabinetControlLog log = new CabinetControlLog();
        log.setAction("BOX_OPEN");
        log.setBoxId(11L);
        log.setBoxNumber(1);
        log.setStatus("ACCEPTED");
        LocalDateTime since = LocalDateTime.of(2026, 11, 9, 8, 0);
        when(controlLogs.findByLockerIdAndActionInAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(
                eq(LOCKER), eq(CabinetCommissioningService.TEST_ACTIONS), eq(since))).thenReturn(List.of(log));

        CommissioningStatusResponse status = service.status(LOCKER, since);

        assertEquals(3L, status.controller().id());
        assertEquals("ACCEPTED", status.hardwareApplyStatus());
        assertEquals(1, status.commands().size());
        assertEquals(11L, status.commands().get(0).boxId());
    }
}
