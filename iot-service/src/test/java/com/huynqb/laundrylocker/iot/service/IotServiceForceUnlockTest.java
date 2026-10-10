package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.client.OrderClient;
import com.huynqb.laundrylocker.iot.dto.BoxAccessLogResponse;
import com.huynqb.laundrylocker.iot.dto.ForceUnlockRequest;
import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import com.huynqb.laundrylocker.iot.model.GatewayDevice;
import com.huynqb.laundrylocker.iot.repository.AccessAttemptRepository;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.BoxHardwareStatusRepository;
import com.huynqb.laundrylocker.iot.repository.DeviceStatusRepository;
import com.huynqb.laundrylocker.iot.repository.GatewayDeviceRepository;
import com.huynqb.laundrylocker.iot.settings.TestIotRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Mở ô khẩn cấp không được báo "đã mở" khi tủ không xác nhận; GET nhật ký không ghi DB.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IotServiceForceUnlockTest {

    @Mock DeviceStatusRepository repository;
    @Mock BoxAccessLogRepository accessLogRepository;
    @Mock BoxHardwareStatusRepository boxHardwareStatusRepository;
    @Mock AccessAttemptRepository accessAttemptRepository;
    @Mock org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @Mock OrderClient orderClient;
    @Mock LockerClient lockerClient;
    @Mock LockerMqttService lockerMqttService;
    @Mock GatewayDeviceRepository gatewayDeviceRepository;

    IotService iotService;
    final ForceUnlockRequest request = new ForceUnlockRequest(7L, 70L, 3L);

    @BeforeEach
    void setUp() {
        iotService = new IotService(
                repository, accessLogRepository, boxHardwareStatusRepository, accessAttemptRepository,
                rabbitTemplate, orderClient, lockerClient, lockerMqttService,
                TestIotRules.of(Map.of()), gatewayDeviceRepository);
    }

    @Test
    void timeoutIsNotReportedAsOpened() {
        when(lockerMqttService.sendUnlockCommandAsync(7L, 70L))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException()));

        Map<String, Object> result = iotService.forceUnlock(request);

        assertEquals(false, result.get("accepted"));
        assertEquals("TIMEOUT", result.get("status"));
        verify(lockerClient, never()).openBox(anyLong());
        assertEquals("TIMEOUT", savedLog().getResult());
    }

    @Test
    void publishFailureIsReportedAsFailed() {
        when(lockerMqttService.sendUnlockCommandAsync(7L, 70L))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        Map<String, Object> result = iotService.forceUnlock(request);

        assertEquals(false, result.get("accepted"));
        assertEquals("FAILED", result.get("status"));
        verify(lockerClient, never()).openBox(anyLong());
    }

    @Test
    void jammedDoorIsReportedWithHardwareMessage() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("status", "FAILED");
        node.put("errorCode", "JAMMED");
        node.put("errorMessage", "Chốt ô bị kẹt");
        when(lockerMqttService.sendUnlockCommandAsync(7L, 70L)).thenReturn(CompletableFuture.completedFuture(node));

        Map<String, Object> result = iotService.forceUnlock(request);

        assertEquals(false, result.get("accepted"));
        assertEquals("JAMMED", result.get("status"));
        assertEquals("Chốt ô bị kẹt", result.get("message"));
    }

    @Test
    void confirmedOpenIsAcceptedEvenIfLockerNotificationFails() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("status", "SUCCESS");
        when(lockerMqttService.sendUnlockCommandAsync(7L, 70L)).thenReturn(CompletableFuture.completedFuture(node));
        doThrow(new RuntimeException("locker-service down")).when(lockerClient).openBox(70L);

        Map<String, Object> result = iotService.forceUnlock(request);

        assertEquals(true, result.get("accepted"));
        assertEquals("OPENED", result.get("status"));
        assertEquals("SUCCESS", savedLog().getResult());
    }

    @Test
    void readingLogsNeverWritesRows() {
        BoxAccessLog online = new BoxAccessLog();
        online.setId(1L);
        online.setLockerId(7L);
        online.setCredentialType("DISCOVERY");
        online.setResult("ONLINE");
        when(accessLogRepository.findByLockerIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(online));
        GatewayDevice gateway = new GatewayDevice();
        gateway.setLockerId(7L);
        // Đã mất kết nối từ lâu: trước đây GET sẽ chèn thêm DISCOVERY/DISPLAY OFFLINE.
        gateway.setLastSeenAt(LocalDateTime.now().minusHours(2));
        when(gatewayDeviceRepository.findByLockerId(7L)).thenReturn(Optional.of(gateway));

        List<BoxAccessLogResponse> logs = iotService.getLockerLogs(7L);

        assertEquals(1, logs.size());
        verify(accessLogRepository, never()).save(any());
    }

    private BoxAccessLog savedLog() {
        ArgumentCaptor<BoxAccessLog> saved = ArgumentCaptor.forClass(BoxAccessLog.class);
        verify(accessLogRepository).save(saved.capture());
        return saved.getValue();
    }
}
