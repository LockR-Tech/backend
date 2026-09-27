package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.iot.dto.BoxStatusUpdateRequest;
import com.huynqb.laundrylocker.iot.dto.DeviceStatusRequest;
import com.huynqb.laundrylocker.iot.settings.TestIotRules;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Contract test phía backend cho hợp đồng MQTT (ADR-0008, docs/01-overview/mqtt-contract.md).
/// Payload của tủ dưới đây giống hệt payload Pi sinh ra trong `iot/tests/test_mqtt_contract.py`.
class LockerMqttServiceTest {

    static final String MAC = "2C:CF:67:DB:C5:C3";
    // ─── Payload Pi gửi lên (mqtt-contract.md § 2.2) ───
    static final String PI_OPEN_RESULT = """
            {"commandId": "%s", "boxId": 12, "slotIndex": 3, "status": "SUCCESS", "hwState": "OPEN",
             "errorCode": null, "errorMessage": "Door opened", "timestamp": "2026-09-27T10:00:03+00:00"}""";
    static final String PI_STATUS = """
            {"slotIndex": 3, "boxId": 12, "hwState": "OPEN", "doorOpen": true, "timestamp": "2026-09-27T10:00:04+00:00"}""";
    static final String PI_STATUS_NO_BOX = """
            {"slotIndex": 3, "hwState": "CLOSED", "doorOpen": false, "timestamp": "2026-09-27T10:00:09+00:00"}""";
    static final String PI_HEARTBEAT = """
            {"cabinetId": "1", "timestamp": "2026-09-27T10:01:00+00:00", "status": "online",
             "lockers": [{"slotIndex": 3, "hwState": "OPEN", "boxId": 12}],
             "macAddress": "2C:CF:67:DB:C5:C3", "firmwareVersion": "v1.0.0", "uptime": 60}""";
    static final String PI_DISCOVERY = """
            {"macAddress": "2C:CF:67:DB:C5:C3", "firmwareVersion": "v1.0.0", "hardware": "gpio", "lockerId": 1,
             "slaves": [{"slaveId": 1, "availableSlots": 7}], "timestamp": "2026-09-27T10:00:00+00:00"}""";

    final ObjectMapper objectMapper = new ObjectMapper();
    ApplicationContext context;
    IotService iotService;
    GatewayProvisioningService provisioning;
    CabinetLayoutLookup layoutLookup;
    MqttClient client;
    LockerMqttService service;

    @BeforeEach
    void setUp() {
        context = mock(ApplicationContext.class);
        iotService = mock(IotService.class);
        provisioning = mock(GatewayProvisioningService.class);
        layoutLookup = mock(CabinetLayoutLookup.class);
        when(context.getBean(IotService.class)).thenReturn(iotService);
        when(context.getBean(GatewayProvisioningService.class)).thenReturn(provisioning);
        service = new LockerMqttService(objectMapper, context,
                TestIotRules.of(Map.of("app.iot.door-open-timeout-seconds", 45)), layoutLookup);
        client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        ReflectionTestUtils.setField(service, "client", client);
    }

    JsonNode published(String topic) throws Exception {
        ArgumentCaptor<MqttMessage> message = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(eq(topic), message.capture());
        return objectMapper.readTree(new String(message.getValue().getPayload(), StandardCharsets.UTF_8));
    }

    // ─── Backend → tủ ───

    @Test
    void openCommandMatchesContract() throws Exception {
        when(layoutLookup.slotIndexOf(1L, 12L)).thenReturn(Optional.of(3));

        service.sendUnlockCommandAsync(1L, 12L);

        JsonNode payload = published("cabinet/1/command/open");
        assertEquals(Set.of("commandId", "boxId", "box_id", "slotIndex", "action", "timeout"),
                Set.copyOf(iterableToList(payload.fieldNames())));
        assertEquals(12, payload.get("boxId").asLong());
        assertEquals(12, payload.get("box_id").asLong());
        assertEquals(3, payload.get("slotIndex").asInt());
        assertEquals("OPEN", payload.get("action").asText());
        assertEquals(45, payload.get("timeout").asInt(), "door timeout comes from admin settings");
        assertFalse(payload.get("commandId").asText().isBlank());
    }

    @Test
    void openCommandWithoutLayoutStillCarriesBoxId() throws Exception {
        when(layoutLookup.slotIndexOf(anyLong(), anyLong())).thenReturn(Optional.empty());

        service.sendUnlockCommandAsync(7L, 3L);

        JsonNode payload = published("cabinet/7/command/open");
        assertFalse(payload.has("slotIndex"), "cabinet maps boxId through its setup layout");
        assertEquals(3, payload.get("boxId").asLong());
    }

    @Test
    void boxStateSyncMatchesContract() throws Exception {
        assertTrue(service.publishBoxStateSync(1L, 12L, "OCCUPIED", 99L));

        JsonNode payload = published("cabinet/1/command/sync");
        assertEquals(objectMapper.readTree("{\"boxId\":12,\"state\":\"OCCUPIED\",\"orderId\":99}"), payload);
    }

    // ─── Tủ → backend ───

    @Test
    void cabinetResultCompletesPendingCommand() throws Exception {
        when(layoutLookup.slotIndexOf(1L, 12L)).thenReturn(Optional.of(3));
        CompletableFuture<JsonNode> future = service.sendUnlockCommandAsync(1L, 12L);
        String commandId = published("cabinet/1/command/open").get("commandId").asText();

        service.handleMessage("cabinet/1/command/open/result", PI_OPEN_RESULT.formatted(commandId));

        JsonNode result = future.get(1, TimeUnit.SECONDS);
        assertEquals("SUCCESS", result.get("status").asText());
    }

    @Test
    void doorStatusWithBoxIdUpdatesThatBox() throws Exception {
        service.route("cabinet/1/locker/3/status", objectMapper.readTree(PI_STATUS));

        verify(iotService).updateBoxStatus(new BoxStatusUpdateRequest(12L, "OPEN", 1L));
        verify(layoutLookup, never()).boxIdAt(anyLong(), anyInt());
    }

    @Test
    void doorStatusWithoutBoxIdIsResolvedBySlot() throws Exception {
        when(layoutLookup.boxIdAt(1L, 3)).thenReturn(Optional.of(12L));

        service.route("cabinet/1/locker/3/status", objectMapper.readTree(PI_STATUS_NO_BOX));

        verify(iotService).updateBoxStatus(new BoxStatusUpdateRequest(12L, "CLOSED", 1L));
    }

    @Test
    void doorStatusForUnknownSlotIsDropped() throws Exception {
        when(layoutLookup.boxIdAt(anyLong(), anyInt())).thenReturn(Optional.empty());

        service.route("cabinet/1/locker/3/status", objectMapper.readTree(PI_STATUS_NO_BOX));

        verify(iotService, never()).updateBoxStatus(any());
    }

    @Test
    void heartbeatIdentifiesDeviceByMac() throws Exception {
        service.route("cabinet/1/heartbeat", objectMapper.readTree(PI_HEARTBEAT));

        verify(iotService).updateStatus(new DeviceStatusRequest(MAC, 1L, "online"));
        verify(provisioning).touch(MAC);
    }

    @Test
    void heartbeatWithoutMacFallsBackToTopic() throws Exception {
        service.route("cabinet/2/heartbeat", objectMapper.readTree("{\"status\":\"ONLINE\"}"));

        verify(iotService).updateStatus(new DeviceStatusRequest("2", 2L, "ONLINE"));
        verify(provisioning, never()).touch(any());
    }

    @Test
    void provisioningMessagesAreRouted() throws Exception {
        JsonNode discovery = objectMapper.readTree(PI_DISCOVERY);
        service.route("iot/" + MAC + "/discovery/result", discovery);
        verify(provisioning).onDiscovery(MAC, discovery);

        JsonNode result = objectMapper.readTree("{\"commandId\":\"s-1\",\"status\":\"COMPLETED\"}");
        service.route("iot/" + MAC + "/setup/result", result);
        verify(provisioning).onSetupResult(MAC, result);
    }

    private static <T> java.util.List<T> iterableToList(java.util.Iterator<T> iterator) {
        java.util.List<T> list = new java.util.ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list;
    }
}
