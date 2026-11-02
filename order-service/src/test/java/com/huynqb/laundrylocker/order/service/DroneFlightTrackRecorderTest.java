package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneFlightTrackPoint;
import com.huynqb.laundrylocker.order.repository.DroneFlightTrackPointRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DroneFlightTrackRecorderTest {

    private static final long T0 = Instant.parse("2026-11-02T03:00:00Z").toEpochMilli();

    private final DroneFlightTrackPointRepository repository = mock(DroneFlightTrackPointRepository.class);
    private final DroneFlightTrackRecorder recorder = new DroneFlightTrackRecorder(repository);

    private static DroneFlightTrackRecorder.Point point(long orderId, long atMs, Double lat) {
        return new DroneFlightTrackRecorder.Point(
                orderId, 3L, "DRONE-01", "en_route", lat, 106.81, 30.0, 90.0, 8.5, 72,
                DroneFlightTrackRecorder.DRONE, atMs);
    }

    @Test
    void savesAllFieldsOfAPoint() {
        recorder.record(point(21L, T0, 10.845));

        ArgumentCaptor<DroneFlightTrackPoint> row = ArgumentCaptor.forClass(DroneFlightTrackPoint.class);
        verify(repository).save(row.capture());
        assertEquals(21L, row.getValue().getOrderId());
        assertEquals("EN_ROUTE", row.getValue().getStage());
        assertEquals(10.845, row.getValue().getLatitude());
        assertEquals(30.0, row.getValue().getAltitudeM());
        assertEquals(72, row.getValue().getBatteryPercent());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(T0), ZoneId.systemDefault()), row.getValue().getRecordedAt());
    }

    @Test
    void keepsAtMostOnePointEveryTwoSecondsPerOrder() {
        recorder.record(point(21L, T0, 10.840));
        recorder.record(point(21L, T0 + 1_000, 10.841));
        recorder.record(point(22L, T0 + 1_000, 10.900));
        recorder.record(point(21L, T0 + 2_000, 10.842));

        verify(repository, times(3)).save(any());
    }

    @Test
    void skipsPointsWithoutCoordinates() {
        recorder.record(point(21L, T0, null));

        verify(repository, never()).save(any());
    }

    @Test
    void aFailedSaveDoesNotBreakTheCallerAndIsRetriedNextTime() {
        when(repository.save(any())).thenThrow(new IllegalStateException("db down")).thenReturn(null);

        assertDoesNotThrow(() -> recorder.record(point(21L, T0, 10.840)));
        recorder.record(point(21L, T0 + 500, 10.841));

        verify(repository, times(2)).save(any());
    }
}
