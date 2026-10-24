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
    @Mock CabinetControlLogService logs;
    @Mock CabinetLayoutLookup layoutLookup;
    CabinetControlService service;

    @BeforeEach
    void setUp() {
        service = new CabinetControlService(mqtt, lockerClient, TestIotRules.of(Map.of()), logs, layoutLookup);
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
        verifyNoInteractions(logs);
    }

    @Test
    void webCommandIsLoggedWithItsResult() {
        when(mqtt.sendLidCommandAsync(LOCKER, 1, "open")).thenReturn(accepted());
        service.lid(LOCKER, 1, "open", 999L, "ADMIN");
        verify(logs).recordWeb(LOCKER, 999L, "LID_OPEN", 1, null, null, "ACCEPTED", "Tủ đã nhận lệnh nắp open");
    }

    @Test
    void timeoutIsStillLoggedWhenCabinetIsOffline() {
        when(mqtt.sendUnlockCommandAsync(LOCKER, 44L))
                .thenReturn(CompletableFuture.failedFuture(new java.util.concurrent.TimeoutException()));
        Map<String, Object> r = service.openBox(LOCKER, 44L, TECH, "LOCKER_TECHNICIAN");
        assertEquals("TIMEOUT", r.get("status"));
        verify(logs).recordWeb(eq(LOCKER), eq(TECH), eq("BOX_OPEN"), isNull(), eq(44L), isNull(),
                eq("TIMEOUT"), contains("không phản hồi"));
    }

    @Test
    void jogLogsRevolutionsAndDirection() {
        when(mqtt.sendLidJogAsync(LOCKER, 1, 2.5, "close")).thenReturn(accepted());
        service.lidJog(LOCKER, 1, 2.5, "close", 999L, "ADMIN");
        verify(logs).recordWeb(eq(LOCKER), eq(999L), eq("LID_JOG"), eq(1), isNull(), eq(2.5),
                eq("ACCEPTED"), startsWith("Về phía đóng"));
    }

    @Test
    void panelPollingIsNotLogged() {
        when(mqtt.sendPanelQueryAsync(LOCKER)).thenReturn(accepted());
        service.panel(LOCKER, 999L, "ADMIN");
        verifyNoInteractions(logs);
    }

    @Test
    void logsRespectLockerScope() {
        assertEquals("CABINET_FORBIDDEN", assertThrows(BusinessException.class,
                () -> service.logs(LOCKER, 50, OTHER_TECH, "LOCKER_TECHNICIAN")).getCode());
        service.logs(LOCKER, 50, TECH, "LOCKER_TECHNICIAN");
        verify(logs).recent(LOCKER, 50);
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

    // ---- Mở thử ô bằng QR, không cần đơn ----

    static final long CUSTOMER = 501L, BOX = 39L, FULL_BOX = 40L;

    private CabinetControlService testOpenService(boolean enabled, String userIds, String lockerIds) {
        when(layoutLookup.layout(LOCKER)).thenReturn(java.util.Optional.of(new com.huynqb.laundrylocker.iot.dto.LockerLayoutView(
                LOCKER, "CAB-TU01", "Tủ thật", List.of(
                        new com.huynqb.laundrylocker.iot.dto.LockerLayoutView.Cell(BOX, 2, 0, 1, "AVAILABLE"),
                        new com.huynqb.laundrylocker.iot.dto.LockerLayoutView.Cell(FULL_BOX, 3, 0, 2, "OCCUPIED")))));
        return new CabinetControlService(mqtt, lockerClient, TestIotRules.of(Map.of(
                com.huynqb.laundrylocker.iot.settings.IotSettingsCatalog.TEST_OPEN_ENABLED, enabled,
                com.huynqb.laundrylocker.iot.settings.IotSettingsCatalog.TEST_OPEN_USER_IDS, userIds,
                com.huynqb.laundrylocker.iot.settings.IotSettingsCatalog.TEST_OPEN_LOCKER_IDS, lockerIds)), logs, layoutLookup);
    }

    private String testOpenError(CabinetControlService s, long lockerId, long boxId, Long userId) {
        return assertThrows(BusinessException.class, () -> s.testOpenBox(lockerId, boxId, userId)).getCode();
    }

    @Test
    void testOpenIsOffByDefault() {
        assertEquals("TEST_OPEN_DISABLED", testOpenError(service, LOCKER, BOX, CUSTOMER));
        verify(mqtt, never()).sendUnlockCommandAsync(anyLong(), anyLong());
    }

    @Test
    void testOpenNeedsBothTheAccountAndTheLockerOnTheList() {
        assertEquals("TEST_OPEN_USER_NOT_ALLOWED", testOpenError(testOpenService(true, "0", "7"), LOCKER, BOX, CUSTOMER));
        assertEquals("TEST_OPEN_USER_NOT_ALLOWED", testOpenError(testOpenService(true, "501", "7"), LOCKER, BOX, null));
        assertEquals("TEST_OPEN_LOCKER_NOT_ALLOWED", testOpenError(testOpenService(true, "501", "0"), LOCKER, BOX, CUSTOMER));
        verify(mqtt, never()).sendUnlockCommandAsync(anyLong(), anyLong());
        verifyNoInteractions(logs);
    }

    @Test
    void allowedTesterOpensAnyFreeBoxOfTheLockerAndItIsLogged() {
        when(mqtt.sendUnlockCommandAsync(LOCKER, BOX)).thenReturn(accepted());
        Map<String, Object> r = testOpenService(true, "12,501", "7").testOpenBox(LOCKER, BOX, CUSTOMER);
        assertEquals(true, r.get("accepted"));
        verify(mqtt).sendUnlockCommandAsync(LOCKER, BOX);
        verify(logs).recordWeb(eq(LOCKER), eq(CUSTOMER), eq("BOX_OPEN_TEST"), isNull(), eq(BOX), isNull(),
                eq("ACCEPTED"), startsWith("Mở thử bằng QR"));
    }

    @Test
    void testOpenNeverOpensABoxHoldingAnOrderOrABoxOfAnotherLocker() {
        CabinetControlService s = testOpenService(true, "501", "7");
        assertEquals("BOX_IN_USE", testOpenError(s, LOCKER, FULL_BOX, CUSTOMER));
        assertEquals("BOX_NOT_IN_LOCKER", testOpenError(s, LOCKER, 999L, CUSTOMER));
        when(layoutLookup.layout(LOCKER)).thenReturn(java.util.Optional.empty());
        assertEquals("LOCKER_LOOKUP_FAILED", testOpenError(s, LOCKER, BOX, CUSTOMER));
        verify(mqtt, never()).sendUnlockCommandAsync(anyLong(), anyLong());
    }
}
