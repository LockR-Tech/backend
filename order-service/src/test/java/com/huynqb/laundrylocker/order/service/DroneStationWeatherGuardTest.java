package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.DroneFlightControlViews;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.dto.DroneWeatherViews;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/// Trạm tự dừng khi dự báo tại trạm xấu và tự mở lại khi trời tốt — chỉ với lệnh dừng do hệ thống tạo.
class DroneStationWeatherGuardTest {

    private final DroneWeatherQueryService weatherQuery = mock(DroneWeatherQueryService.class);
    private final DroneFlightControl flightControl = mock(DroneFlightControl.class);
    private final DroneStationWeatherGuard guard = new DroneStationWeatherGuard(
            TestOrderRules.defaults(), weatherQuery, flightControl, mock(DroneDispatchSweeper.class));

    private static DroneWeatherAssessment weather(boolean safe, String status) {
        return new DroneWeatherAssessment(safe, status,
                safe ? "Đủ điều kiện bay." : "Thời tiết không cho phép bay: gió giật 52 km/h.",
                OffsetDateTime.now(), 30, 2, 30, 52, 0, 3);
    }

    private static DroneFlightControlViews.StationSuspension stop(Long byUser) {
        return new DroneFlightControlViews.StationSuspension(5L, "WEATHER", "Thời tiết tại trạm", null, byUser,
                LocalDateTime.now().minusMinutes(10), null);
    }

    private void stations(DroneWeatherAssessment weather, DroneFlightControlViews.StationSuspension suspension) {
        when(weatherQuery.droneLockers()).thenReturn(List.of(new DroneWeatherViews.LockerWeather(
                5L, "D", "Tủ nhận", "", 10.87, 106.80, weather, suspension)));
    }

    @Test
    void unsafeForecastSuspendsTheStationAsTheSystem() {
        stations(weather(false, "UNSAFE"), null);

        assertEquals(1, guard.guard());

        verify(flightControl).suspend(eq(5L), argThat(request -> "WEATHER".equals(request.reason())
                && request.note().startsWith(DroneStationWeatherGuard.AUTO_NOTE_PREFIX)), isNull());
    }

    @Test
    void clearSkyResumesOnlyASystemWeatherStop() {
        stations(weather(true, "SAFE"), stop(null));
        assertEquals(1, guard.guard());
        verify(flightControl).resume(5L, null);

        reset(flightControl);
        stations(weather(true, "SAFE"), stop(9L));
        assertEquals(0, guard.guard());
        verify(flightControl, never()).resume(anyLong(), any());
    }

    @Test
    void missingForecastChangesNothing() {
        stations(weather(false, "UNAVAILABLE"), null);
        assertEquals(0, guard.guard());

        stations(weather(false, "UNAVAILABLE"), stop(null));
        assertEquals(0, guard.guard());

        verifyNoInteractions(flightControl);
    }
}
