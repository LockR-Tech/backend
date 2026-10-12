package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.dto.DeviceStatusResponse;
import com.huynqb.laundrylocker.iot.dto.LockerView;
import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import com.huynqb.laundrylocker.iot.model.DeviceStatus;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.DeviceStatusRepository;
import com.huynqb.laundrylocker.iot.settings.TestIotRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Thiết bị IoT của KTV: lọc theo tủ phụ trách, trạng thái theo cửa sổ online, lệnh khởi động lại.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceManagementServiceTest {

    static final long TECH = 42L;
    static final long OTHER_TECH = 43L;

    @Mock DeviceStatusRepository deviceStatusRepository;
    @Mock BoxAccessLogRepository boxAccessLogRepository;
    @Mock LockerMqttService lockerMqttService;
    @Mock LockerClient lockerClient;

    DeviceManagementService service;
    DeviceStatus mine;
    DeviceStatus others;

    @BeforeEach
    void setUp() {
        service = new DeviceManagementService(
                deviceStatusRepository, boxAccessLogRepository, lockerMqttService, lockerClient,
                TestIotRules.of(Map.of()));
        mine = device(1L, "2C:CF:67:DB:C5:C3", 7L, "ONLINE", LocalDateTime.now().minusSeconds(30));
        others = device(2L, "AA:BB:CC:DD:EE:FF", 8L, "ONLINE", LocalDateTime.now().minusSeconds(30));
        when(deviceStatusRepository.findAll()).thenReturn(List.of(mine, others));
        when(deviceStatusRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(deviceStatusRepository.findById(2L)).thenReturn(Optional.of(others));
        when(deviceStatusRepository.save(any(DeviceStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerClient.lockers(null)).thenReturn(ApiResponse.ok(List.of(
                new LockerView(7L, "CAB-TU01", "Tủ thư viện", TECH),
                new LockerView(8L, "CAB-TU02", "Tủ ký túc xá", OTHER_TECH))));
    }

    @Test
    void technicianSeesOnlyDevicesOfOwnLockersWithLockerNames() {
        List<DeviceStatusResponse> devices = service.listDevices(TECH, "LOCKER_TECHNICIAN");

        assertEquals(List.of(1L), devices.stream().map(DeviceStatusResponse::id).toList());
        assertEquals("CAB-TU01", devices.getFirst().lockerCode());
        assertEquals("Tủ thư viện", devices.getFirst().lockerName());
        assertEquals("ONLINE", devices.getFirst().status());
        assertTrue(devices.getFirst().online());
    }

    @Test
    void adminSeesAllDevicesEvenWhenLockerNamesAreUnavailable() {
        when(lockerClient.lockers(null)).thenThrow(new RuntimeException("locker-service down"));

        List<DeviceStatusResponse> devices = service.listDevices(1L, "ADMIN");

        assertEquals(2, devices.size());
        assertNull(devices.getFirst().lockerCode());
    }

    @Test
    void technicianListFailsWhenAssignmentsCannotBeLoaded() {
        when(lockerClient.lockers(null)).thenThrow(new RuntimeException("locker-service down"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listDevices(TECH, "LOCKER_TECHNICIAN"));

        assertEquals("LOCKER_SERVICE_UNAVAILABLE", ex.getCode());
    }

    @Test
    void reportedOnlineWithoutRecentSignalIsShownOffline() {
        mine.setLastSeenAt(LocalDateTime.now().minusMinutes(10));

        DeviceStatusResponse device = service.getDevice(1L, TECH, "LOCKER_TECHNICIAN");

        assertEquals("OFFLINE", device.status());
        assertFalse(device.online());
    }

    @Test
    void technicianCannotOpenDeviceOfAnotherLocker() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDevice(2L, TECH, "LOCKER_TECHNICIAN"));

        assertEquals("DEVICE_NOT_IN_SCOPE", ex.getCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void manualStatusCannotBeOnlineAndKeepsLastSeen() {
        LocalDateTime lastSeen = LocalDateTime.now().minusMinutes(1);
        mine.setLastSeenAt(lastSeen);

        BusinessException online = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, "online", TECH, "LOCKER_TECHNICIAN"));
        assertEquals("DEVICE_STATUS_ONLINE_NOT_ALLOWED", online.getCode());
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, "REBOOTING", TECH, "LOCKER_TECHNICIAN"));
        assertEquals("DEVICE_STATUS_INVALID", unknown.getCode());

        DeviceStatusResponse marked = service.updateStatus(1L, "offline", TECH, "LOCKER_TECHNICIAN");

        assertEquals("OFFLINE", marked.status());
        assertEquals(lastSeen, mine.getLastSeenAt());
    }

    @Test
    void restartPublishesRestartCommandAndAuditsAcceptance() {
        ObjectNode reply = JsonNodeFactory.instance.objectNode();
        reply.put("status", "ACCEPTED");
        when(lockerMqttService.sendRestartCommandAsync(7L)).thenReturn(CompletableFuture.completedFuture(reply));

        Map<String, Object> result = service.restart(1L, TECH, "LOCKER_TECHNICIAN");

        assertEquals(true, result.get("accepted"));
        assertEquals("ACCEPTED", result.get("status"));
        verify(lockerMqttService, never()).sendUnlockCommandAsync(anyLong(), anyLong());
        BoxAccessLog audit = savedAudit();
        assertEquals("RESTART_ACCEPTED", audit.getResult());
        assertEquals(7L, audit.getLockerId());
        assertEquals(TECH, audit.getActorUserId());
    }

    @Test
    void restartWithoutReplyIsTimeout() {
        when(lockerMqttService.sendRestartCommandAsync(7L))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException()));

        Map<String, Object> result = service.restart(1L, TECH, "LOCKER_TECHNICIAN");

        assertEquals(false, result.get("accepted"));
        assertEquals("TIMEOUT", result.get("status"));
        assertEquals("RESTART_TIMEOUT", savedAudit().getResult());
    }

    @Test
    void restartRefusedByCabinetIsFailedWithItsMessage() {
        ObjectNode reply = JsonNodeFactory.instance.objectNode();
        reply.put("status", "FAILED");
        reply.put("errorMessage", "Đang có lệnh mở ô chưa xong");
        when(lockerMqttService.sendRestartCommandAsync(7L)).thenReturn(CompletableFuture.completedFuture(reply));

        Map<String, Object> result = service.restart(1L, TECH, "LOCKER_TECHNICIAN");

        assertEquals("FAILED", result.get("status"));
        assertEquals("Đang có lệnh mở ô chưa xong", result.get("message"));
    }

    @Test
    void restartOfUnpairedDeviceIsRejected() {
        mine.setLockerId(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restart(1L, 1L, "ADMIN"));

        assertEquals("DEVICE_NOT_PAIRED", ex.getCode());
        verify(lockerMqttService, never()).sendRestartCommandAsync(any());
    }

    private BoxAccessLog savedAudit() {
        ArgumentCaptor<BoxAccessLog> saved = ArgumentCaptor.forClass(BoxAccessLog.class);
        verify(boxAccessLogRepository).save(saved.capture());
        return saved.getValue();
    }

    private static DeviceStatus device(Long id, String deviceId, Long lockerId, String status, LocalDateTime lastSeen) {
        DeviceStatus device = new DeviceStatus();
        device.setId(id);
        device.setDeviceId(deviceId);
        device.setLockerId(lockerId);
        device.setStatus(status);
        device.setLastSeenAt(lastSeen);
        return device;
    }
}
