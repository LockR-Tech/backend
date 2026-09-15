package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.iot.settings.TestIotRules;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LockerMqttServiceTest {

    @Test
    void openCommandCarriesDoorTimeoutFromAdminSettings() throws Exception {
        LockerMqttService service = new LockerMqttService(
                new ObjectMapper(), mock(ApplicationContext.class),
                TestIotRules.of(Map.of("app.iot.door-open-timeout-seconds", 45)));
        MqttClient client = mock(MqttClient.class);
        ReflectionTestUtils.setField(service, "client", client);

        service.sendUnlockCommandAsync(7L, 3L);

        ArgumentCaptor<MqttMessage> message = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(eq("cabinet/7/command/open"), message.capture());
        String payload = new String(message.getValue().getPayload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"timeout\":45"), payload);
        assertTrue(payload.contains("\"box_id\":3"), payload);
    }
}
