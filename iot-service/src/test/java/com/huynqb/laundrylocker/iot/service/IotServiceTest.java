package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.client.OrderClient;
import com.huynqb.laundrylocker.iot.dto.OrderLookupResponse;
import com.huynqb.laundrylocker.iot.dto.UnlockWithCodeRequest;
import com.huynqb.laundrylocker.iot.model.AccessAttempt;
import com.huynqb.laundrylocker.iot.repository.AccessAttemptRepository;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.BoxHardwareStatusRepository;
import com.huynqb.laundrylocker.iot.repository.DeviceStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IotServiceTest {

    @Mock
    DeviceStatusRepository repository;
    @Mock
    BoxAccessLogRepository accessLogRepository;
    @Mock
    BoxHardwareStatusRepository boxHardwareStatusRepository;
    @Mock
    AccessAttemptRepository accessAttemptRepository;
    @Mock
    org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @Mock
    OrderClient orderClient;
    @Mock
    LockerClient lockerClient;
    @Mock
    LockerMqttService lockerMqttService;

    IotService iotService;

    @BeforeEach
    void setUp() {
        iotService = new IotService(
                repository,
                accessLogRepository,
                boxHardwareStatusRepository,
                accessAttemptRepository,
                rabbitTemplate,
                orderClient,
                lockerClient,
                lockerMqttService);
        ReflectionTestUtils.setField(iotService, "lockoutMaxAttempts", 5);
        ReflectionTestUtils.setField(iotService, "lockoutMinutes", 15);
    }

    @Test
    void unlockWithCodeRejectsCodeAtWrongLocker() {
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 9L, 9002L, null, "STORING", "112233", null)));

        Map<String, Object> result = iotService.unlockWithCode(new UnlockWithCodeRequest(7L, "PIN-123"));

        assertFalse(Boolean.TRUE.equals(result.get("accepted")));
        assertEquals("Mã không hợp lệ hoặc đã hết hạn", result.get("message"));
        assertFalse(result.containsKey("boxId"));
        verify(accessAttemptRepository, never()).save(any(AccessAttempt.class));
        verify(lockerMqttService, never()).sendUnlockCommandAsync(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(lockerClient, never()).openBox(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void verifyAccessRejectsCanceledCredential() {
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 9L, 9002L, null, "CANCELED", "112233", LocalDateTime.now())));

        var result = iotService.verifyAccess(9002L, "PIN-123");

        assertFalse(Boolean.TRUE.equals(result.valid()));
        assertEquals("Access code is no longer active", result.message());
        verify(accessAttemptRepository, never()).save(org.mockito.ArgumentMatchers.any(AccessAttempt.class));
    }
}
