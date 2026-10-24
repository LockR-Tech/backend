package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.iot.model.CabinetControlLog;
import com.huynqb.laundrylocker.iot.repository.CabinetControlLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/// Nhật ký điều khiển tủ: sự kiện từ tủ (`cabinet/{id}/event/control`) có thể tới muộn hoặc lặp khi
/// Pi gửi lại sau lúc mất mạng — giữ giờ xảy ra thật, chống trùng theo `eventId`, bỏ dữ liệu sai.
class CabinetControlLogServiceTest {

    private final CabinetControlLogRepository repository = mock(CabinetControlLogRepository.class);
    private final ObjectMapper json = new ObjectMapper();
    private CabinetControlLogService service;

    @BeforeEach
    void setUp() {
        service = new CabinetControlLogService(repository);
    }

    private JsonNode event(String body) throws Exception {
        return json.readTree(body);
    }

    private CabinetControlLog saved() {
        ArgumentCaptor<CabinetControlLog> captor = ArgumentCaptor.forClass(CabinetControlLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void lidEventKeepsRevolutionsAndOriginalTime() throws Exception {
        OffsetDateTime twoHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0);
        assertTrue(service.recordFromCabinet(7L, event("""
                {"eventId":"e-1","action":"lid_close","axis":1,"revolutions":42.5,"status":"stopped",
                 "message":"Dừng sau 17000 bước","occurredAt":"%s"}""".formatted(twoHoursAgo))));
        CabinetControlLog log = saved();
        assertEquals(7L, log.getLockerId());
        assertEquals(CabinetControlLog.SOURCE_CABINET, log.getSource());
        assertEquals("LID_CLOSE", log.getAction());
        assertEquals("STOPPED", log.getStatus());
        assertEquals(1, log.getAxis());
        assertEquals(42.5, log.getRevolutions());
        assertEquals(twoHoursAgo.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(), log.getOccurredAt());
    }

    @Test
    void boxEventMapsSlotToBoxNumber() throws Exception {
        service.recordFromCabinet(7L, event(
                "{\"eventId\":\"e-2\",\"action\":\"BOX_OPEN\",\"slotIndex\":3,\"boxId\":41,\"status\":\"OK\"}"));
        CabinetControlLog log = saved();
        assertEquals(4, log.getBoxNumber());
        assertEquals(41L, log.getBoxId());
    }

    @Test
    void duplicateEventIsIgnored() throws Exception {
        when(repository.existsByEventId("e-3")).thenReturn(true);
        assertFalse(service.recordFromCabinet(7L, event("{\"eventId\":\"e-3\",\"action\":\"BOX_OPEN\",\"status\":\"OK\"}")));
        verify(repository, never()).save(any());
    }

    @Test
    void invalidEventsAreIgnored() throws Exception {
        assertFalse(service.recordFromCabinet(7L, event("{\"action\":\"BOX_OPEN\",\"status\":\"OK\"}")));
        assertFalse(service.recordFromCabinet(7L, event("{\"eventId\":\"e-4\",\"action\":\"drop table\",\"status\":\"OK\"}")));
        assertFalse(service.recordFromCabinet(null, event("{\"eventId\":\"e-5\",\"action\":\"BOX_OPEN\",\"status\":\"OK\"}")));
        verify(repository, never()).save(any());
    }

    @Test
    void futureClockIsReplacedByReceiveTime() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        service.recordFromCabinet(7L, event("""
                {"eventId":"e-6","action":"BOX_OPEN","status":"OK","occurredAt":"%s"}"""
                .formatted(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))));
        LocalDateTime at = saved().getOccurredAt();
        assertFalse(at.isBefore(before));
        assertTrue(at.isBefore(LocalDateTime.now().plusMinutes(1)));
    }

    @Test
    void longMessageIsTruncated() throws Exception {
        service.recordFromCabinet(7L, event("{\"eventId\":\"e-7\",\"action\":\"BOX_OPEN\",\"status\":\"OK\",\"message\":\""
                + "x".repeat(900) + "\"}"));
        assertEquals(500, saved().getMessage().length());
    }

    @Test
    void webLogFailureDoesNotThrow() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.recordWeb(7L, 1L, "BOX_OPEN", null, 41L, null, "TIMEOUT", "Tủ không phản hồi"));
    }
}
