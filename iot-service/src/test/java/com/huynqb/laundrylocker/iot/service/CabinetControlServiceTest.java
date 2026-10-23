package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.dto.LockerView;
import com.huynqb.laundrylocker.iot.settings.TestIotRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Điều khiển tủ từ admin web: phân quyền ADMIN/KTV theo tủ phụ trách, hợp lệ hoá lệnh, ánh xạ kết quả.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CabinetControlServiceTest {

    static final long LOCKER = 7L, TECH = 42L, OTHER_TECH = 43L;

    @Mock LockerMqttService mqtt;
    @Mock LockerClient lockerClient;
    CabinetControlService service;

    @BeforeEach
    void setUp() {
        service = new CabinetControlService(mqtt, lockerClient, TestIotRules.of(Map.of()));
        when(lockerClient.lockers(List.of(LOCKER))).thenReturn(
                ApiResponse.ok(List.of(new LockerView(LOCKER, "CAB-TU01", "Tủ thật", TECH))));
    }

    private CompletableFuture<com.fasterxml.jackson.databind.JsonNode> accepted() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("status", "ACCEPTED");
        return CompletableFuture.completedFuture(n);
    }

    @Test
    void adminControlsAnyLocker() {
        when(mqtt.sendLidCommandAsync(LOCKER, 1, "open")).thenReturn(accepted());
        Map<String, Object> r = service.lid(LOCKER, 1, "open", 999L, "ADMIN");
        assertEquals(true, r.get("accepted"));
        assertEquals("ACCEPTED", r.get("status"));
        verify(mqtt).sendLidCommandAsync(LOCKER, 1, "open");
    }

    @Test
    void assignedTechnicianControlsOwnLocker() {
        when(mqtt.sendLidCommandAsync(LOCKER, 1, "close")).thenReturn(accepted());
        Map<String, Object> r = service.lid(LOCKER, 1, "close", TECH, "LOCKER_TECHNICIAN");
        assertEquals(true, r.get("accepted"));
    }

    @Test
    void otherTechnicianIsForbidden() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.lid(LOCKER, 1, "open", OTHER_TECH, "LOCKER_TECHNICIAN"));
        assertEquals("CABINET_FORBIDDEN", ex.getCode());
        verify(mqtt, never()).sendLidCommandAsync(anyLong(), anyInt(), any());
    }

    @Test
    void unknownLockerIsNotFound() {
        when(lockerClient.lockers(List.of(99L))).thenReturn(ApiResponse.ok(List.of()));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.panel(99L, 999L, "ADMIN"));
        assertEquals("LOCKER_NOT_FOUND", ex.getCode());
    }

    @Test
    void invalidLidActionRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.lid(LOCKER, 1, "fly", 999L, "ADMIN"));
        assertEquals("LID_ACTION_INVALID", ex.getCode());
        verify(mqtt, never()).sendLidCommandAsync(anyLong(), anyInt(), any());
    }

    @Test
    void jogRejectsBadDirectionAndRevs() {
        assertEquals("JOG_DIRECTION_INVALID", assertThrows(BusinessException.class,
                () -> service.lidJog(LOCKER, 1, 2, "sideways", 999L, "ADMIN")).getCode());
        assertEquals("JOG_REVS_INVALID", assertThrows(BusinessException.class,
                () -> service.lidJog(LOCKER, 1, 0, "open", 999L, "ADMIN")).getCode());
        verify(mqtt, never()).sendLidJogAsync(anyLong(), anyInt(), anyDouble(), any());
    }

    @Test
    void failedResultMapsToNotAccepted() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("status", "FAILED");
        n.put("errorMessage", "Trục 2 chưa bật trên Pi");
        when(mqtt.sendLidCommandAsync(LOCKER, 2, "open")).thenReturn(CompletableFuture.completedFuture(n));
        Map<String, Object> r = service.lid(LOCKER, 2, "open", 999L, "ADMIN");
        assertEquals(false, r.get("accepted"));
        assertEquals("FAILED", r.get("status"));
        assertEquals("Trục 2 chưa bật trên Pi", r.get("message"));
    }
}
