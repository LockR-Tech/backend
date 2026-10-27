package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.DroneFlightControlViews;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.model.DroneFlightBlock;
import com.huynqb.laundrylocker.order.model.DroneStationSuspension;
import com.huynqb.laundrylocker.order.model.DroneStationSuspensionLog;
import com.huynqb.laundrylocker.order.repository.DroneFlightBlockRepository;
import com.huynqb.laundrylocker.order.repository.DroneStationSuspensionLogRepository;
import com.huynqb.laundrylocker.order.repository.DroneStationSuspensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DroneFlightControlTest {

    private final DroneStationSuspensionRepository suspensions = mock(DroneStationSuspensionRepository.class);
    private final DroneStationSuspensionLogRepository logs = mock(DroneStationSuspensionLogRepository.class);
    private final DroneFlightBlockRepository blocks = mock(DroneFlightBlockRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private DroneFlightControl control;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        control = new DroneFlightControl(suspensions, logs, blocks, transactions);
    }

    @Test
    void suspendingAStationStoresTheStopAndLogsWhoDidIt() {
        when(suspensions.findById(7L)).thenReturn(Optional.empty());
        LocalDateTime resumeAt = LocalDateTime.now().plusHours(2);

        var view = control.suspend(7L, new DroneFlightControlViews.SuspendStationRequest(
                "weather", "  Mưa giông cục bộ  ", resumeAt), 42L);

        assertEquals("WEATHER", view.reason());
        assertEquals("Thời tiết tại trạm", view.reasonLabel());
        assertEquals("Mưa giông cục bộ", view.note());
        assertEquals(resumeAt, view.resumeAt());
        ArgumentCaptor<DroneStationSuspensionLog> logged = ArgumentCaptor.forClass(DroneStationSuspensionLog.class);
        verify(logs).save(logged.capture());
        assertEquals(DroneStationSuspensionLog.SUSPEND, logged.getValue().getAction());
        assertEquals(42L, logged.getValue().getActorUserId());
    }

    @Test
    void rejectsUnknownReasonAndPastResumeTime() {
        assertEquals("DRONE_STATION_SUSPEND_REASON_INVALID", assertThrows(BusinessException.class,
                () -> control.suspend(7L, new DroneFlightControlViews.SuspendStationRequest("RAIN", null, null), 1L))
                .getCode());
        assertEquals("DRONE_STATION_RESUME_AT_INVALID", assertThrows(BusinessException.class,
                () -> control.suspend(7L, new DroneFlightControlViews.SuspendStationRequest(
                        "WEATHER", null, LocalDateTime.now().minusMinutes(1)), 1L))
                .getCode());
        verify(suspensions, never()).save(any());
    }

    @Test
    void suspendedStationBlocksAndIsRecordedEvenWhenTheCallerRollsBack() {
        when(suspensions.findById(5L)).thenReturn(Optional.of(stop(5L, null)));
        var context = new DroneFlightControl.Context(21L, "ORD-1", 9L, 3L, 5L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> control.assertStationsOpen(DroneFlightBlock.LAUNCH, context, 3L, 5L));

        assertEquals("DRONE_STATION_SUSPENDED", error.getCode());
        assertTrue(error.getMessage().startsWith("Tủ nhận đang tạm dừng bay"), error.getMessage());
        ArgumentCaptor<DroneFlightBlock> saved = ArgumentCaptor.forClass(DroneFlightBlock.class);
        verify(blocks).save(saved.capture());
        assertEquals(DroneFlightBlock.STATION_SUSPENDED, saved.getValue().getCause());
        assertEquals(DroneFlightBlock.LAUNCH, saved.getValue().getAction());
        assertEquals(5L, saved.getValue().getBlockedLockerId());
        assertEquals("ORD-1", saved.getValue().getOrderCode());
        // Ghi bằng giao dịch riêng (REQUIRES_NEW), không theo giao dịch của lời gọi sắp rollback.
        verify(transactions).commit(any());
    }

    @Test
    void expiredStopAndQuotePreviewDoNotRecord() {
        when(suspensions.findById(3L)).thenReturn(Optional.of(stop(3L, LocalDateTime.now().minusMinutes(5))));
        control.assertStationsOpen(DroneFlightBlock.BOOKING, null, 3L);

        when(suspensions.findById(5L)).thenReturn(Optional.of(stop(5L, null)));
        assertThrows(BusinessException.class, () -> control.assertStationsOpen(null, null, 5L));
        verify(blocks, never()).save(any());
    }

    @Test
    void weatherBlockKeepsTheForecastThatCausedIt() {
        var weather = new DroneWeatherAssessment(false, "UNSAFE", "Không thể đặt chuyến bay: gió 41 km/h",
                OffsetDateTime.now(), 30, 2, 41, 55, 0, 3);

        control.recordWeather(DroneFlightBlock.BOOKING, new DroneFlightControl.Context(null, null, 9L, 3L, 5L), weather);

        ArgumentCaptor<DroneFlightBlock> saved = ArgumentCaptor.forClass(DroneFlightBlock.class);
        verify(blocks).save(saved.capture());
        assertEquals(DroneFlightBlock.WEATHER_UNSAFE, saved.getValue().getCause());
        assertEquals(41.0, saved.getValue().getMaxWindSpeedKmh());
        assertEquals(55.0, saved.getValue().getMaxWindGustKmh());
        assertNull(saved.getValue().getOrderId());
        assertEquals(9L, saved.getValue().getActorUserId());
    }

    @Test
    void failingToRecordNeverHidesTheBusinessError() {
        when(blocks.save(any())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> control.recordSuspended(DroneFlightBlock.ACCEPT, null));
    }

    private static DroneStationSuspension stop(Long lockerId, LocalDateTime resumeAt) {
        DroneStationSuspension suspension = new DroneStationSuspension();
        suspension.setLockerId(lockerId);
        suspension.setReason("WEATHER");
        suspension.setSuspendedAt(LocalDateTime.now().minusHours(1));
        suspension.setResumeAt(resumeAt);
        return suspension;
    }
}
