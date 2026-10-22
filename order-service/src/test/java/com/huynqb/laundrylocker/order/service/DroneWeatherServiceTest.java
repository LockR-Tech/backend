package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.hamcrest.Matchers.containsString;

class DroneWeatherServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void reportsUnsafeWhenRainIsForecastAtAnIntermediateRoutePoint() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DroneWeatherService service = new DroneWeatherService(builder.build(), 30, 40, 1500);
        OffsetDateTime firstForecast = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2).truncatedTo(ChronoUnit.MINUTES);
        List<String> times = List.of(firstForecast.toString(), firstForecast.plusMinutes(15).toString(),
                firstForecast.plusMinutes(30).toString());
        Map<String, Object> safePoint = point(times, List.of(0, 0, 0), List.of(0, 0, 0),
                List.of(8, 9, 8), List.of(12, 13, 12), List.of(10000, 10000, 10000));
        Map<String, Object> rainyPoint = point(times, List.of(0, 0.4, 0), List.of(0, 61, 0),
                List.of(9, 10, 8), List.of(13, 15, 12), List.of(10000, 9000, 10000));
        server.expect(requestTo(containsString("minutely_15=")))
                .andExpect(requestTo(containsString("forecast_minutely_15=3")))
                .andExpect(method(GET))
                .andRespond(withSuccess(JSON.writeValueAsString(List.of(safePoint, rainyPoint)), MediaType.APPLICATION_JSON));

        var result = service.assessRoute(locker(3L, 10.70, 106.70), locker(5L, 10.71, 106.71));

        assertFalse(result.safe());
        assertEquals("UNSAFE", result.status());
        assertEquals(2, result.sampledRoutePoints());
        assertEquals(0.4, result.maxPrecipitationMm());
        assertTrue(result.message().contains("mưa hoặc dông"));
        server.verify();
    }

    private static Map<String, Object> point(
            List<String> times, List<? extends Number> precipitation, List<Integer> weatherCode,
            List<Integer> wind, List<Integer> gust, List<Integer> visibility) {
        return Map.of("minutely_15", Map.of(
                "time", times,
                "precipitation", precipitation,
                "weather_code", weatherCode,
                "wind_speed_10m", wind,
                "wind_gusts_10m", gust,
                "visibility", visibility));
    }

    private static LockerInfo locker(Long id, double latitude, double longitude) {
        return new LockerInfo(id, 1L, "LK-" + id, "Locker " + id, "ACTIVE", "", latitude, longitude,
                true, null, 10, 5);
    }
}
