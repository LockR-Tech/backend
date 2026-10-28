package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DroneWeatherQueryServiceTest {

    private final LockerOrderRepository orderRepository = mock(LockerOrderRepository.class);
    private final LockerLookupClient lockerLookupClient = mock(LockerLookupClient.class);
    private final DroneWeatherService weatherService = mock(DroneWeatherService.class);
    private final DroneWeatherQueryService queryService =
            new DroneWeatherQueryService(orderRepository, lockerLookupClient, weatherService, mock(DroneFlightControl.class));

    @Test
    void fetches30MinWeatherWhileOrderIsInActiveFlight() {
        LockerOrder order = new LockerOrder();
        order.setId(42L);
        order.setType("DRONE_DELIVERY");
        order.setStatus("ACTIVE");
        order.setDeliveryStage("EN_ROUTE");
        order.setSourceLockerId(1L);
        order.setDestinationLockerId(2L);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        LockerInfo source = new LockerInfo(1L, 1L, "LK-1", "Tủ 1", "ACTIVE", "", 10.77, 106.70, true, "OK", 10, 5);
        LockerInfo dest = new LockerInfo(2L, 1L, "LK-2", "Tủ 2", "ACTIVE", "", 10.80, 106.72, true, "OK", 10, 5);
        when(lockerLookupClient.getLockers(anyList())).thenReturn(ApiResponse.ok(List.of(source, dest)));

        DroneWeatherAssessment safe = new DroneWeatherAssessment(
                true, "SAFE", "Thời tiết thuận lợi", OffsetDateTime.now(ZoneOffset.UTC),
                2, 3, 10.0, 15.0, 0.0, 10000, "OPEN_METEO", 29.0, "Trời quang");
        when(weatherService.assessRoute(source, dest)).thenReturn(safe);

        var result = queryService.forOrder(42L);

        assertNotNull(result.weather());
        assertEquals("SAFE", result.weather().status());
        verify(weatherService).assessRoute(source, dest);
    }

    @ParameterizedTest
    @CsvSource({
            "STORING, READY_FOR_PICKUP",
            "COMPLETED, COMPLETED",
            "CANCELED, CANCELED",
            "EXPIRED, EXPIRED",
            "ACTIVE, FAILED",
            "ACTIVE, DROP_REPORTED"
    })
    void skipsWeatherLookupAfterFlightHasEnded(String orderStatus, String deliveryStage) {
        LockerOrder order = new LockerOrder();
        order.setId(99L);
        order.setType("DRONE_DELIVERY");
        order.setStatus(orderStatus);
        order.setDeliveryStage(deliveryStage);
        order.setSourceLockerId(1L);
        order.setDestinationLockerId(2L);
        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));

        LockerInfo source = new LockerInfo(1L, 1L, "LK-1", "Tủ 1", "ACTIVE", "", 10.77, 106.70, true, "OK", 10, 5);
        LockerInfo dest = new LockerInfo(2L, 1L, "LK-2", "Tủ 2", "ACTIVE", "", 10.80, 106.72, true, "OK", 10, 5);
        when(lockerLookupClient.getLockers(anyList())).thenReturn(ApiResponse.ok(List.of(source, dest)));

        var result = queryService.forOrder(99L);

        assertEquals(99L, result.orderId());
        assertEquals("Tủ 1", result.sourceLockerName());
        assertEquals("Tủ 2", result.destinationLockerName());
        assertNull(result.weather());
        verify(weatherService, never()).assessRoute(any(), any());
        verify(weatherService, never()).assessJourney(any(), any(), any(), any());
    }
}
