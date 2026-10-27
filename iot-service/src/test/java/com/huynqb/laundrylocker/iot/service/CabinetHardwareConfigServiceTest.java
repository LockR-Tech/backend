package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.dto.HardwareConfigRequest;
import com.huynqb.laundrylocker.iot.dto.HardwareConfigView;
import com.huynqb.laundrylocker.iot.model.CabinetHardwareConfig;
import com.huynqb.laundrylocker.iot.repository.CabinetHardwareConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Tab "Cấu hình phần cứng": mẫu TU01 hợp lệ, luật kiểm tra giống Pi, chỉ ADMIN lưu/áp dụng,
/// làm sạch sơ đồ đấu dây, áp dụng ghi lại kết quả + cảnh báo của Pi.
class CabinetHardwareConfigServiceTest {

    static final long LOCKER = 7L;
    final ObjectMapper json = new ObjectMapper();
    final CabinetHardwareConfigRepository repository = mock(CabinetHardwareConfigRepository.class);
    final CabinetControlService control = mock(CabinetControlService.class);
    final LockerMqttService mqtt = mock(LockerMqttService.class);
    final CabinetControlLogService logs = mock(CabinetControlLogService.class);
    CabinetHardwareConfigService service;

    @BeforeEach
    void setUp() {
        service = new CabinetHardwareConfigService(repository, control, mqtt, logs, json);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    ObjectNode tu01() {
        return (ObjectNode) service.template().get("config").deepCopy();
    }

    @Test
    void defaultTemplateIsValidAndShownWhenNothingSaved() {
        assertEquals(List.of(), HardwareConfigValidator.validate(service.template().get("config")));
        when(repository.findById(LOCKER)).thenReturn(Optional.empty());
        HardwareConfigView view = service.get(LOCKER, 42L, "LOCKER_TECHNICIAN");
        assertTrue(view.defaultTemplate());
        assertEquals(7, view.config().get("relayPins").size());
        assertTrue(view.wiring().size() > 20);
        verify(control).requireLockerInScope(LOCKER, 42L, "LOCKER_TECHNICIAN");
    }

    @Test
    void validatorMatchesPiRules() {
        ObjectNode dup = tu01();
        dup.putArray("doorPins").add(4).add(12).add(13).add(20).add(21).add(24).add(25);   // 13 = relay ô 3
        assertTrue(HardwareConfigValidator.validate(dup).stream().anyMatch(e -> e.contains("GPIO13 bị dùng hai lần")));

        ObjectNode lidConflict = tu01();
        ((ObjectNode) lidConflict.get("lids").get(0)).put("dirPin", 21);                    // cửa ô 5
        assertFalse(HardwareConfigValidator.validate(lidConflict).isEmpty());

        ObjectNode disabledLid = tu01();
        ((ObjectNode) disabledLid.get("lids").get(1)).put("pulPin", 5);                     // trục 2 tắt
        assertEquals(List.of(), HardwareConfigValidator.validate(disabledLid));

        ObjectNode badPin = tu01();
        badPin.putArray("relayPins").add(5).add(6).add(30).add(19).add(26).add(22).add(23);
        assertFalse(HardwareConfigValidator.validate(badPin).isEmpty());

        ObjectNode overlap = tu01();
        ((ObjectNode) overlap.get("lids").get(1)).putArray("boxes").add(3);                 // ô 3 đã thuộc trục 1
        assertFalse(HardwareConfigValidator.validate(overlap).isEmpty());

        ObjectNode unlock = tu01();
        unlock.put("unlockMs", 50);
        assertFalse(HardwareConfigValidator.validate(unlock).isEmpty());
    }

    @Test
    void technicianCannotSaveOrApply() {
        BusinessException save = assertThrows(BusinessException.class,
                () -> service.save(LOCKER, new HardwareConfigRequest(tu01(), null), 42L, "LOCKER_TECHNICIAN"));
        assertEquals("CABINET_CONFIG_ADMIN_ONLY", save.getCode());
        assertEquals("CABINET_CONFIG_ADMIN_ONLY", assertThrows(BusinessException.class,
                () -> service.apply(LOCKER, 42L, "LOCKER_TECHNICIAN")).getCode());
        verify(repository, never()).save(any());
        verifyNoInteractions(mqtt);
    }

    @Test
    void invalidConfigIsRejectedOnSave() {
        ObjectNode bad = tu01();
        bad.put("unlockMs", 1);
        assertEquals("HW_CONFIG_INVALID", assertThrows(BusinessException.class,
                () -> service.save(LOCKER, new HardwareConfigRequest(bad, null), 1L, "ADMIN")).getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void saveKeepsKnownWiringColumnsOnly() throws Exception {
        when(repository.findById(LOCKER)).thenReturn(Optional.empty());
        JsonNode wiring = json.readTree("""
                [{"from":"Pi chân 6 (GND)","to":"Relay DC−","note":"x","hack":"<script>"},
                 {"from":"","to":"","note":""},
                 {"from":"%s"}]""".formatted("a".repeat(400)));
        HardwareConfigView view = service.save(LOCKER, new HardwareConfigRequest(tu01(), wiring), 1L, "ADMIN");
        assertFalse(view.defaultTemplate());
        assertEquals(2, view.wiring().size());
        assertFalse(view.wiring().get(0).has("hack"));
        assertEquals("Relay DC−", view.wiring().get(0).get("to").asText());
        assertEquals(CabinetHardwareConfigService.MAX_WIRING_TEXT, view.wiring().get(1).get("from").asText().length());
    }

    @Test
    void applyNeedsSavedConfig() {
        when(repository.findById(LOCKER)).thenReturn(Optional.empty());
        assertEquals("HW_CONFIG_NOT_SAVED", assertThrows(BusinessException.class,
                () -> service.apply(LOCKER, 1L, "ADMIN")).getCode());
    }

    @Test
    void applySendsSavedConfigAndRecordsResult() {
        CabinetHardwareConfig saved = new CabinetHardwareConfig();
        saved.setLockerId(LOCKER);
        saved.setConfig(tu01().toString());
        saved.setWiring("[]");
        when(repository.findById(LOCKER)).thenReturn(Optional.of(saved));
        ObjectNode piResult = json.createObjectNode().put("status", "ACCEPTED");
        piResult.putArray("warnings").add("Chưa cập nhật được dòng giữ relay");
        Map<String, Object> awaited = new LinkedHashMap<>();
        awaited.put("accepted", true);
        awaited.put("status", "ACCEPTED");
        awaited.put("message", "Tủ đã nhận lệnh cấu hình phần cứng");
        awaited.put("result", piResult);
        when(control.await(anyString(), any())).thenReturn(awaited);

        Map<String, Object> r = service.apply(LOCKER, 1L, "ADMIN");

        assertEquals("ACCEPTED", r.get("status"));
        assertEquals(List.of("Chưa cập nhật được dòng giữ relay"), r.get("warnings"));
        ArgumentCaptor<CabinetHardwareConfig> stored = ArgumentCaptor.forClass(CabinetHardwareConfig.class);
        verify(repository).save(stored.capture());
        assertEquals("ACCEPTED", stored.getValue().getApplyStatus());
        assertEquals(1L, stored.getValue().getAppliedBy());
        assertTrue(stored.getValue().getApplyMessage().contains("dòng giữ relay"));
        verify(logs).recordWeb(eq(LOCKER), eq(1L), eq("HW_CONFIG"), isNull(), isNull(), isNull(), eq("ACCEPTED"), anyString());
    }
}
