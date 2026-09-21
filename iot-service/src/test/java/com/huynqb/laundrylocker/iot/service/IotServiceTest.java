package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.client.LockerClient;
import com.huynqb.laundrylocker.iot.client.OrderClient;
import com.huynqb.laundrylocker.iot.dto.OrderLookupResponse;
import com.huynqb.laundrylocker.iot.dto.UnlockRequest;
import com.huynqb.laundrylocker.iot.dto.UnlockWithCodeRequest;
import com.huynqb.laundrylocker.iot.model.AccessAttempt;
import com.huynqb.laundrylocker.iot.repository.AccessAttemptRepository;
import com.huynqb.laundrylocker.iot.repository.BoxAccessLogRepository;
import com.huynqb.laundrylocker.iot.repository.BoxHardwareStatusRepository;
import com.huynqb.laundrylocker.iot.repository.DeviceStatusRepository;
import com.huynqb.laundrylocker.iot.settings.TestIotRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
                lockerMqttService,
                TestIotRules.of(Map.of("app.iot.lockout.max-attempts", 5, "app.iot.lockout.minutes", 15)));
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
    void lockoutAttemptsAndDurationFollowAdminSettings() {
        IotService strict = new IotService(
                repository, accessLogRepository, boxHardwareStatusRepository, accessAttemptRepository,
                rabbitTemplate, orderClient, lockerClient, lockerMqttService,
                TestIotRules.of(Map.of("app.iot.lockout.max-attempts", 2, "app.iot.lockout.minutes", 60)));
        AccessAttempt previous = new AccessAttempt();
        previous.setBoxId(9002L);
        previous.setFailedCount(1);
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.of(previous));
        when(orderClient.getByAccess("WRONG")).thenThrow(new RuntimeException("not found"));

        var result = strict.verifyAccess(9002L, "WRONG");

        assertFalse(Boolean.TRUE.equals(result.valid()));
        ArgumentCaptor<AccessAttempt> saved = ArgumentCaptor.forClass(AccessAttempt.class);
        verify(accessAttemptRepository).save(saved.capture());
        assertEquals(2, saved.getValue().getFailedCount());
        assertNotNull(saved.getValue().getLockedUntil());
        // Khoá theo cấu hình 60 phút (mặc định chỉ 15 phút và cần 5 lần sai).
        assertTrue(saved.getValue().getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(55)));
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

    // F2-G01: mở khoá LẤY đồ (đơn đang STORING) phải tự hoàn tất đơn — không còn để
    // khách tự bấm "hoàn tất" trên app hay đơn treo mãi ở STORING.

    @Test
    void unlockWithCodeCompletesOrderWhenPickingUpFromStoring() {
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 7L, null, 9002L, "STORING", "PIN-123", null)));
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(lockerMqttService.sendUnlockCommandAsync(7L, 9002L))
                .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));

        Map<String, Object> result = iotService.unlockWithCode(new UnlockWithCodeRequest(7L, "PIN-123"));

        assertTrue(Boolean.TRUE.equals(result.get("accepted")));
        verify(lockerClient).openBox(9002L);
        verify(orderClient).complete(51L, 44L);
    }

    @Test
    void unlockWithCodeDoesNotCompleteOrderWhenDroppingOffAtInitialized() {
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 7L, 9002L, null, "INITIALIZED", "PIN-123", null)));
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(lockerMqttService.sendUnlockCommandAsync(7L, 9002L))
                .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));

        Map<String, Object> result = iotService.unlockWithCode(new UnlockWithCodeRequest(7L, "PIN-123"));

        assertTrue(Boolean.TRUE.equals(result.get("accepted")));
        verify(lockerClient).openBox(9002L);
        verify(orderClient, never()).complete(anyLong(), anyLong());
    }

    @Test
    void unlockCompletesOrderWhenPickingUpFromReturned() {
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 7L, null, 9002L, "RETURNED", "PIN-123", null)));
        when(lockerMqttService.sendUnlockCommandAsync(7L, 9002L))
                .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));

        Map<String, Object> result = iotService.unlock(new UnlockRequest(7L, 9002L, "PIN-123"), 44L);

        assertTrue(Boolean.TRUE.equals(result.get("accepted")));
        verify(orderClient).complete(51L, 44L);
    }

    @Test
    void unlockKeepsDoorOpenResultEvenWhenAutoCompleteFails() {
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 7L, null, 9002L, "STORING", "PIN-123", null)));
        when(lockerMqttService.sendUnlockCommandAsync(7L, 9002L))
                .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));
        when(orderClient.complete(eq(51L), eq(44L))).thenThrow(new RuntimeException("ORDER_PAYMENT_REQUIRED"));

        Map<String, Object> result = iotService.unlock(new UnlockRequest(7L, 9002L, "PIN-123"), 44L);

        // Cửa đã mở thật — một lỗi hoàn tất không được biến phản hồi thành "thất bại".
        assertTrue(Boolean.TRUE.equals(result.get("accepted")));
        verify(lockerClient).openBox(9002L);
    }

    // Thuê ô dùng PIN nhiều lần tới hạn: mở lại ô không được kết thúc lượt thuê.

    @Test
    void unlockWithCodeDoesNotCompleteRentalOnReopen() {
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 7L, 9002L, null, "STORING", "PIN-123", null, "RENTAL")));
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(lockerMqttService.sendUnlockCommandAsync(7L, 9002L))
                .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));

        Map<String, Object> result = iotService.unlockWithCode(new UnlockWithCodeRequest(7L, "PIN-123"));

        assertTrue(Boolean.TRUE.equals(result.get("accepted")));
        verify(lockerClient).openBox(9002L);
        verify(orderClient, never()).complete(anyLong(), anyLong());
    }

    @Test
    void unlockDoesNotCompleteRentalOnReopen() {
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 7L, 9002L, null, "STORING", "PIN-123", null, "RENTAL")));
        when(lockerMqttService.sendUnlockCommandAsync(7L, 9002L))
                .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));

        Map<String, Object> result = iotService.unlock(new UnlockRequest(7L, 9002L, "PIN-123"), 44L);

        assertTrue(Boolean.TRUE.equals(result.get("accepted")));
        verify(lockerClient).openBox(9002L);
        verify(orderClient, never()).complete(anyLong(), anyLong());
    }

    @Test
    void unlockWithCodeStillCompletesSendPickup() {
        when(orderClient.getByAccess("PIN-123")).thenReturn(ApiResponse.ok(
                new OrderLookupResponse(51L, 44L, 7L, null, 9002L, "STORING", "PIN-123", null, "SEND")));
        when(accessAttemptRepository.findById(9002L)).thenReturn(Optional.empty());
        when(lockerMqttService.sendUnlockCommandAsync(7L, 9002L))
                .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));

        Map<String, Object> result = iotService.unlockWithCode(new UnlockWithCodeRequest(7L, "PIN-123"));

        assertTrue(Boolean.TRUE.equals(result.get("accepted")));
        verify(orderClient).complete(51L, 44L);
    }
}
