package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.client.OrderClient;
import com.huynqb.laundrylocker.iot.dto.BleOpenTokenResponse;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import com.huynqb.laundrylocker.iot.dto.OrderBleAccessView;
import com.huynqb.laundrylocker.iot.model.BleOrderToken;
import com.huynqb.laundrylocker.iot.model.BleSigningKey;
import com.huynqb.laundrylocker.iot.repository.BleOrderTokenRepository;
import com.huynqb.laundrylocker.iot.repository.BleSigningKeyRepository;
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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Mở ô theo đơn qua Bluetooth (F3-G11): cấp mã theo quyết định của order-service, lưu nonce → đơn; Pi báo
/// `BOX_OPEN_BLE` kèm nonce thì mới ghi nhật ký mở ô và chuyển trạng thái đơn — đúng một lần.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BleOrderServiceTest {

    static final long LOCKER = 7L, ORDER = 21L, OWNER = 10L, RECEIVER = 55L;
    static final ObjectMapper JSON = new ObjectMapper();

    @Mock OrderClient orderClient;
    @Mock BleSigningKeyRepository keys;
    @Mock CabinetLayoutLookup layoutLookup;
    @Mock CabinetControlLogService controlLogs;
    @Mock BleOrderTokenRepository tokens;
    @Mock IotService iotService;
    final Map<String, BleOrderToken> stored = new HashMap<>();
    BleOrderService service;

    @BeforeEach
    void setUp() {
        AtomicReference<BleSigningKey> key = new AtomicReference<>();
        when(keys.findById(BleSigningKey.SINGLETON_ID)).thenAnswer(i -> Optional.ofNullable(key.get()));
        when(keys.saveAndFlush(any())).thenAnswer(i -> {
            key.set(i.getArgument(0));
            return i.getArgument(0);
        });
        // Bảng ble_order_tokens giả trong bộ nhớ, `claim` chỉ thành công một lần như câu UPDATE thật.
        when(tokens.save(any())).thenAnswer(i -> {
            BleOrderToken row = i.getArgument(0);
            stored.put(row.getNonce(), row);
            return row;
        });
        when(tokens.findByNonceAndLockerId(anyString(), anyLong())).thenAnswer(i -> Optional
                .ofNullable(stored.get(i.<String>getArgument(0)))
                .filter(t -> t.getLockerId().equals(i.getArgument(1))));
        when(tokens.claim(anyString(), anyLong(), anyString(), any())).thenAnswer(i -> {
            BleOrderToken row = stored.get(i.<String>getArgument(0));
            if (row == null || !row.getLockerId().equals(i.getArgument(1)) || row.getProcessedAt() != null) {
                return 0;
            }
            row.setResult(i.getArgument(2));
            row.setProcessedAt(i.getArgument(3));
            return 1;
        });
        when(layoutLookup.layout(LOCKER)).thenReturn(Optional.of(new LockerLayoutView(LOCKER, "CAB-TU01", "Tủ thật",
                List.of(new LockerLayoutView.Cell(40L, 3, 1, 2, "RESERVED"),
                        new LockerLayoutView.Cell(41L, 4, 2, 1, "OCCUPIED")))));
        service = new BleOrderService(orderClient, new BleTokenSigner(keys), layoutLookup, controlLogs, tokens,
                iotService, TestIotRules.defaults(), Runnable::run);
    }

    // ─── cấp mã ───

    @Test
    void ownerGetsASignedDropTokenForTheBoxOfTheOrder() {
        allow(OWNER, "DROP", 40L);

        BleOpenTokenResponse r = service.issueToken(ORDER, OWNER);

        assertEquals(ORDER, r.orderId());
        assertEquals("DROP", r.action());
        assertEquals(3, r.boxNumber());
        assertEquals(40L, r.boxId());
        assertEquals("LOCKR_CAB-TU01", r.deviceName());
        assertEquals(BleTestService.OPEN_CHAR_UUID, r.openCharUuid());
        ByteBuffer b = ByteBuffer.wrap(Base64.getUrlDecoder().decode(r.token()));
        assertEquals(1, b.get());
        assertEquals(LOCKER, Integer.toUnsignedLong(b.getInt()));
        assertEquals(3, Short.toUnsignedInt(b.getShort()));
        assertEquals(OWNER, b.getLong());

        BleOrderToken row = stored.get(r.nonce());
        assertNotNull(row);
        assertEquals(ORDER, row.getOrderId());
        assertEquals(OWNER, row.getUserId());
        assertEquals(40L, row.getBoxId());
        assertEquals(3, row.getBoxNumber());
        assertEquals("DROP", row.getAction());
        assertNull(row.getProcessedAt());
        verify(controlLogs).recordWeb(eq(LOCKER), eq(OWNER), eq("BLE_TOKEN"), isNull(), eq(40L), isNull(),
                eq("ISSUED"), contains("đơn #21"));
    }

    @Test
    void rentalAndPickupTokensCarryTheirAction() {
        allow(OWNER, "RENTAL_ACCESS", 41L);
        BleOpenTokenResponse rental = service.issueToken(ORDER, OWNER);
        assertEquals("RENTAL_ACCESS", rental.action());
        assertEquals(4, rental.boxNumber());

        allow(RECEIVER, "PICKUP", 40L);
        BleOpenTokenResponse pickup = service.issueToken(ORDER, RECEIVER);
        assertEquals("PICKUP", pickup.action());
        assertEquals(RECEIVER, stored.get(pickup.nonce()).getUserId());
    }

    @Test
    void refusalFromOrderServiceKeepsItsCodeAndStatusAndIssuesNothing() {
        deny("BLE_ORDER_FORBIDDEN", "Tài khoản này không được mở ô của đơn lúc này.", 403);
        BusinessException forbidden = assertThrows(BusinessException.class, () -> service.issueToken(ORDER, RECEIVER));
        assertEquals("BLE_ORDER_FORBIDDEN", forbidden.getCode());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());

        deny("BLE_ORDER_NOT_OPENABLE", "Đơn này không còn mở ô được.", 409);
        BusinessException wrongStatus = assertThrows(BusinessException.class, () -> service.issueToken(ORDER, OWNER));
        assertEquals("BLE_ORDER_NOT_OPENABLE", wrongStatus.getCode());
        assertEquals(HttpStatus.CONFLICT, wrongStatus.getStatus());

        deny("ORDER_NOT_FOUND", "Không tìm thấy đơn", 404);
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.issueToken(ORDER, OWNER)).getStatus());

        assertTrue(stored.isEmpty());
        verifyNoInteractions(controlLogs);
    }

    @Test
    void unpaidOrderGetsTheKioskBlockMessage() {
        deny("ORDER_UNPAID", "Vui lòng thanh toán phụ phí quá hạn trước khi nhận hàng.", 400);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issueToken(ORDER, OWNER));
        assertEquals("ORDER_UNPAID", ex.getCode());
        assertEquals(IotService.blockMessage("ORDER_UNPAID"), ex.getMessage());

        deny("RENTAL_EXPIRED", null, 409);
        assertEquals("RENTAL_EXPIRED",
                assertThrows(BusinessException.class, () -> service.issueToken(ORDER, OWNER)).getCode());
        assertTrue(stored.isEmpty());
    }

    @Test
    void orderServiceOutageIsNotAWrongCode() {
        when(orderClient.bleAccess(ORDER, OWNER)).thenThrow(new RuntimeException("connection refused"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.issueToken(ORDER, OWNER));
        assertEquals(IotService.ORDER_LOOKUP_UNAVAILABLE, ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void anonymousCallerIsRejected() {
        assertEquals("UNAUTHORIZED",
                assertThrows(BusinessException.class, () -> service.issueToken(ORDER, null)).getCode());
        verifyNoInteractions(orderClient);
    }

    // ─── Pi báo đã mở ───

    @Test
    void openedDropMovesTheOrderOnceEvenWhenThePiResendsTheEvent() throws Exception {
        allow(OWNER, "DROP", 40L);
        String nonce = service.issueToken(ORDER, OWNER).nonce();
        JsonNode event = event("e-1", nonce, "OK", null);

        assertTrue(service.onCabinetEvent(LOCKER, event));
        assertFalse(service.onCabinetEvent(LOCKER, event));
        assertFalse(service.onCabinetEvent(LOCKER, event("e-2", nonce, "OK", null)));

        verify(iotService, times(1)).logExternalAccess(eq(40L), eq(LOCKER), eq(ORDER), eq(OWNER), eq("BLE"),
                eq("SUCCESS"), contains("ô 3"));
        verify(iotService, times(1)).afterBleOrderOpened(40L, ORDER, "INITIALIZED", null, null);
        assertEquals(BleOrderToken.RESULT_SUCCESS, stored.get(nonce).getResult());
        assertNotNull(stored.get(nonce).getProcessedAt());
    }

    @Test
    void openedPickupCompletesWithTheCurrentOrderState() throws Exception {
        allow(RECEIVER, "PICKUP", 40L);
        String nonce = service.issueToken(ORDER, RECEIVER).nonce();
        when(orderClient.summary(ORDER)).thenReturn(ApiResponse.ok(
                new OrderSummary(ORDER, OWNER, "STORING", BigDecimal.TEN, "SEND")));

        assertTrue(service.onCabinetEvent(LOCKER, event("e-1", nonce.toUpperCase(), "OK", null)));

        verify(iotService).afterBleOrderOpened(40L, ORDER, "STORING", "SEND", OWNER);
    }

    @Test
    void pickupOfAnOrderAlreadyClosedOnlyReportsTheOpenedBox() throws Exception {
        allow(RECEIVER, "PICKUP", 40L);
        String nonce = service.issueToken(ORDER, RECEIVER).nonce();
        when(orderClient.summary(ORDER)).thenReturn(ApiResponse.ok(
                new OrderSummary(ORDER, OWNER, "COMPLETED", BigDecimal.TEN, "SEND")));

        assertTrue(service.onCabinetEvent(LOCKER, event("e-1", nonce, "OK", null)));

        verify(iotService).afterBleOrderOpened(40L, ORDER, null, "SEND", null);
    }

    @Test
    void openedRentalLeavesTheOrderAlone() throws Exception {
        allow(OWNER, "RENTAL_ACCESS", 41L);
        String nonce = service.issueToken(ORDER, OWNER).nonce();

        assertTrue(service.onCabinetEvent(LOCKER, event("e-1", nonce, "OK", null)));

        verify(iotService).afterBleOrderOpened(41L, ORDER, "STORING", "RENTAL", null);
        verify(orderClient, never()).summary(anyLong());
    }

    @Test
    void jammedBoxIsLoggedAndReportedButTheOrderDoesNotMove() throws Exception {
        allow(OWNER, "DROP", 40L);
        String nonce = service.issueToken(ORDER, OWNER).nonce();

        assertTrue(service.onCabinetEvent(LOCKER, event("e-1", nonce, "FAILED", "JAMMED")));
        assertFalse(service.onCabinetEvent(LOCKER, event("e-1", nonce, "FAILED", "JAMMED")));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(iotService).logExternalAccess(eq(40L), eq(LOCKER), eq(ORDER), eq(OWNER), eq("BLE"), eq("JAMMED"),
                message.capture());
        assertTrue(message.getValue().contains("[JAMMED]"));
        verify(iotService, times(1)).reportBoxFault(eq(40L), eq("JAMMED"), anyString());
        verify(iotService, never()).afterBleOrderOpened(any(), any(), any(), any(), any());
        assertEquals(BleOrderToken.RESULT_JAMMED, stored.get(nonce).getResult());
    }

    @Test
    void otherFailureIsLoggedAsFailed() throws Exception {
        allow(OWNER, "DROP", 40L);
        String nonce = service.issueToken(ORDER, OWNER).nonce();

        assertTrue(service.onCabinetEvent(LOCKER, event("e-1", nonce, "FAILED", null)));

        verify(iotService).logExternalAccess(eq(40L), eq(LOCKER), eq(ORDER), eq(OWNER), eq("BLE"), eq("FAILED"),
                contains("OPEN_FAILED"));
        verify(iotService, never()).afterBleOrderOpened(any(), any(), any(), any(), any());
    }

    @Test
    void adminTestTokensOtherLockersAndOtherActionsAreIgnored() throws Exception {
        allow(OWNER, "DROP", 40L);
        String nonce = service.issueToken(ORDER, OWNER).nonce();

        assertFalse(service.onCabinetEvent(LOCKER, event("e-1", "0011223344556677", "OK", null)));
        assertFalse(service.onCabinetEvent(8L, event("e-2", nonce, "OK", null)));
        assertFalse(service.onCabinetEvent(LOCKER, JSON.readTree(
                "{\"eventId\":\"e-3\",\"action\":\"BOX_OPEN\",\"status\":\"OK\",\"nonce\":\"" + nonce + "\"}")));
        assertFalse(service.onCabinetEvent(LOCKER, JSON.readTree(
                "{\"eventId\":\"e-4\",\"action\":\"BOX_OPEN_BLE\",\"status\":\"OK\"}")));

        verifyNoInteractions(iotService);
        assertNull(stored.get(nonce).getProcessedAt());
    }

    private void allow(Long userId, String action, Long boxId) {
        when(orderClient.bleAccess(ORDER, userId)).thenReturn(ApiResponse.ok(new OrderBleAccessView(
                ORDER, OWNER, LOCKER, boxId, "INITIALIZED", "SEND", action, null, null, null)));
    }

    private void deny(String code, String message, int status) {
        when(orderClient.bleAccess(eq(ORDER), anyLong())).thenReturn(ApiResponse.ok(new OrderBleAccessView(
                ORDER, null, null, null, null, null, null, code, message, status)));
    }

    /// Payload Pi gửi (iot `services/ble_gatt_service.py` → `control_log.record`).
    private static JsonNode event(String eventId, String nonce, String status, String errorCode) throws Exception {
        Map<String, Object> e = new HashMap<>();
        e.put("eventId", eventId);
        e.put("lockerId", "7");
        e.put("action", "BOX_OPEN_BLE");
        e.put("status", status);
        e.put("slotIndex", 2);
        e.put("nonce", nonce);
        e.put("userId", 10);
        e.put("boxNumber", 3);
        e.put("message", "Mở ô 3 qua Bluetooth (tài khoản #10)" + (errorCode == null ? "" : " — lỗi " + errorCode));
        if (errorCode != null) {
            e.put("errorCode", errorCode);
        }
        e.put("occurredAt", LocalDateTime.now().toString());
        return JSON.valueToTree(e);
    }
}
