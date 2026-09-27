package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.iot.dto.AssignGatewayRequest;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import com.huynqb.laundrylocker.iot.model.GatewayDevice;
import com.huynqb.laundrylocker.iot.repository.GatewayDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Cấp phát bộ điều khiển tủ (ADR-0008). `SETUP_CMD` giống hệt payload Pi nhận trong
/// `iot/tests/test_mqtt_contract.py` — đổi một bên thì đổi cả bên kia.
class GatewayProvisioningServiceTest {

    static final String MAC = "2C:CF:67:DB:C5:C3";
    static final String SETUP_CMD = """
            {"action": "SETUP_LOCKERS", "commandId": "s-1", "macAddress": "2C:CF:67:DB:C5:C3",
             "lockerId": 5, "cabinetId": "5", "cabinetCode": "CAB-TU01", "slaveId": 1,
             "totalRows": 3, "totalColumns": 1, "testDoors": true, "testTimeout": 10,
             "lockerLayout": [
                {"boxId": 40, "slotIndex": 0, "row": 1, "column": 0, "label": "1"},
                {"boxId": 41, "slotIndex": 1, "row": 2, "column": 0, "label": "2"},
                {"boxId": 42, "slotIndex": 2, "row": 3, "column": 0, "label": "3"}]}""";
    static final LockerLayoutView LAYOUT = new LockerLayoutView(5L, "CAB-TU01", "Tu Nam Viet", List.of(
            new LockerLayoutView.Cell(42L, 3, 3, 0),
            new LockerLayoutView.Cell(40L, 1, 1, 0),
            new LockerLayoutView.Cell(41L, 2, 2, 0)));

    final ObjectMapper objectMapper = new ObjectMapper();
    GatewayDeviceRepository repository;
    LockerMqttService mqtt;
    CabinetLayoutLookup layoutLookup;
    GatewayProvisioningService service;

    @BeforeEach
    void setUp() {
        repository = mock(GatewayDeviceRepository.class);
        mqtt = mock(LockerMqttService.class);
        layoutLookup = mock(CabinetLayoutLookup.class);
        service = new GatewayProvisioningService(repository, mqtt, layoutLookup, objectMapper);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mqtt.isConnected()).thenReturn(true);
    }

    GatewayDevice device(Integer availableSlots) {
        GatewayDevice device = new GatewayDevice();
        device.setId(3L);
        device.setMacAddress(MAC);
        device.setAvailableSlots(availableSlots);
        when(repository.findById(3L)).thenReturn(Optional.of(device));
        return device;
    }

    @Test
    void discoveryCreatesDeviceFromPiReport() throws Exception {
        when(repository.findByMacAddress(MAC)).thenReturn(Optional.empty());

        service.onDiscovery(MAC, objectMapper.readTree(LockerMqttServiceTest.PI_DISCOVERY));

        ArgumentCaptor<GatewayDevice> saved = ArgumentCaptor.forClass(GatewayDevice.class);
        verify(repository).save(saved.capture());
        GatewayDevice device = saved.getValue();
        assertEquals(MAC, device.getMacAddress());
        assertEquals("gpio", device.getHardware());
        assertEquals(7, device.getAvailableSlots());
        assertEquals(1, device.getSlaveId());
        assertEquals(1L, device.getReportedLockerId());
        assertNotNull(device.getLastSeenAt());
    }

    @Test
    void discoveryWithMismatchedMacIsIgnored() throws Exception {
        service.onDiscovery("AA:BB:CC:DD:EE:FF", objectMapper.readTree(LockerMqttServiceTest.PI_DISCOVERY));

        verify(repository, never()).save(any());
    }

    @Test
    void assignSendsSetupMatchingContract() throws Exception {
        GatewayDevice device = device(7);
        when(repository.findByLockerId(5L)).thenReturn(Optional.empty());
        when(layoutLookup.layout(5L)).thenReturn(Optional.of(LAYOUT));

        service.assign(3L, new AssignGatewayRequest(5L, null));

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(mqtt).publish(eq("iot/" + MAC + "/command/setup"), payload.capture());
        ObjectNode expected = (ObjectNode) objectMapper.readTree(SETUP_CMD);
        expected.put("commandId", payload.getValue().get("commandId").asText());
        // So trên bản đã tuần tự hoá — đúng thứ Pi nhận (Long/Integer cùng thành số JSON).
        assertEquals(expected, objectMapper.readTree(payload.getValue().toString()));

        assertEquals(5L, device.getLockerId());
        assertEquals("PENDING", device.getSetupStatus());
        assertEquals(payload.getValue().get("commandId").asText(), device.getSetupCommandId());
    }

    @Test
    void assignRejectsLayoutBeyondHardware() {
        device(2);
        when(repository.findByLockerId(5L)).thenReturn(Optional.empty());
        when(layoutLookup.layout(5L)).thenReturn(Optional.of(LAYOUT));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assign(3L, new AssignGatewayRequest(5L, true)));

        assertEquals("LAYOUT_EXCEEDS_HARDWARE", error.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void assignRejectsLockerTakenByAnotherDevice() {
        device(7);
        GatewayDevice other = new GatewayDevice();
        other.setId(9L);
        other.setMacAddress("AA:BB:CC:DD:EE:FF");
        when(repository.findByLockerId(5L)).thenReturn(Optional.of(other));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assign(3L, new AssignGatewayRequest(5L, true)));

        assertEquals("LOCKER_ALREADY_ASSIGNED", error.getCode());
    }

    @Test
    void assignFailsFastWhenBrokerIsDown() throws Exception {
        device(7);
        when(mqtt.isConnected()).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assign(3L, new AssignGatewayRequest(5L, true)));

        assertEquals("MQTT_UNAVAILABLE", error.getCode());
        verify(mqtt, never()).publish(anyString(), any());
    }

    @Test
    void setupResultOnlyCountsForLatestCommand() throws Exception {
        GatewayDevice device = device(7);
        device.setSetupCommandId("s-1");
        device.setSetupStatus("PENDING");
        when(repository.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        String result = """
                {"commandId": "%s", "cabinetId": "5", "lockerId": 5, "status": "COMPLETED",
                 "summary": {"total": 3, "totalOk": 3, "totalFail": 0, "duration": 9}, "lockers": []}""";

        service.onSetupResult(MAC, objectMapper.readTree(result.formatted("old-command")));
        assertEquals("PENDING", device.getSetupStatus());
        assertNull(device.getSetupResult());

        service.onSetupResult(MAC, objectMapper.readTree(result.formatted("s-1")));
        assertEquals("COMPLETED", device.getSetupStatus());
        assertEquals("3/3", device.getSetupProgress());
        assertNotNull(device.getSetupFinishedAt());
    }

    @Test
    void unassignSendsClearSetup() throws Exception {
        GatewayDevice device = device(7);
        device.setLockerId(5L);

        service.unassign(3L);

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(mqtt).publish(eq("iot/" + MAC + "/command/clear-setup"), payload.capture());
        assertEquals("CLEAR_SETUP", payload.getValue().get("action").asText());
        assertNull(device.getLockerId());
        assertEquals("CLEARED", device.getSetupStatus());
    }
}
