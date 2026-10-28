package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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

    @Test
    void acceptsOpenMeteoUtcTimestampsWithoutOffset() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DroneWeatherService service = new DroneWeatherService(builder.build(), 30, 40, 1500);
        OffsetDateTime firstForecast = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2)
                .truncatedTo(ChronoUnit.MINUTES);
        List<String> times = List.of(
                firstForecast.toLocalDateTime().toString(),
                firstForecast.plusMinutes(15).toLocalDateTime().toString(),
                firstForecast.plusMinutes(30).toLocalDateTime().toString());
        Map<String, Object> safePoint = point(times, List.of(0, 0, 0), List.of(0, 0, 0),
                List.of(8, 9, 8), List.of(12, 13, 12), List.of(10000, 10000, 10000));
        server.expect(requestTo(containsString("minutely_15=")))
                .andExpect(method(GET))
                .andRespond(withSuccess(JSON.writeValueAsString(safePoint), MediaType.APPLICATION_JSON));

        var result = service.assessRoute(locker(3L, 10.70, 106.70), locker(5L, 10.71, 106.71));

        assertTrue(result.safe());
        assertEquals("SAFE", result.status());
        server.verify();
    }

    @Test
    void journeyStartsAtTheSendersPickupPointAndRainThereBlocksTheOrder() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DroneWeatherService service = new DroneWeatherService(builder.build(), 30, 40, 1500);
        OffsetDateTime first = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2).truncatedTo(ChronoUnit.MINUTES);
        List<String> times = List.of(first.toString(), first.plusMinutes(15).toString(), first.plusMinutes(30).toString());
        Map<String, Object> rainyAtHome = point(times, List.of(0.6, 0, 0), List.of(63, 3, 3),
                List.of(8, 9, 8), List.of(12, 13, 12), List.of(10000, 10000, 10000));
        Map<String, Object> dry = point(times, List.of(0, 0, 0), List.of(2, 2, 2),
                List.of(8, 9, 8), List.of(12, 13, 12), List.of(10000, 10000, 10000));
        // Điểm đầu là nhà người gửi, sau đó tủ bay và tủ nhận.
        server.expect(requestTo(containsString("latitude=10.68,10.7,10.71")))
                .andExpect(requestTo(containsString("longitude=106.69,106.7,106.71")))
                .andRespond(withSuccess(JSON.writeValueAsString(List.of(rainyAtHome, dry, dry)), MediaType.APPLICATION_JSON));

        var result = service.assessJourney(10.68, 106.69, locker(3L, 10.70, 106.70), locker(5L, 10.71, 106.71));

        assertFalse(result.safe());
        assertEquals(3, result.sampledRoutePoints());
        assertTrue(result.message().contains("mưa hoặc dông"));
        server.verify();
    }

    @Test
    void fallsBackToMetNorwayWhenOpenMeteoFailsAndReportsTheProvider() throws Exception {
        RestClient.Builder openBuilder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer open = MockRestServiceServer.bindTo(openBuilder).build();
        RestClient.Builder metBuilder = RestClient.builder().baseUrl("https://met.test/complete");
        MockRestServiceServer met = MockRestServiceServer.bindTo(metBuilder).build();
        DroneWeatherService service = new DroneWeatherService(
                openBuilder.build(), metBuilder.build(), 30, 40, 1500, null, java.time.Clock.systemUTC());
        open.expect(requestTo(containsString("minutely_15="))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        OffsetDateTime hour = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        // Tuyến ngắn: 2 điểm ⇒ 2 request tới MET. Gió 5 m/s = 18 km/h, gió giật ước tính 27 km/h.
        String body = JSON.writeValueAsString(metForecast(hour, 5.0, 0.0, "partlycloudy_day", 27.5));
        met.expect(requestTo(containsString("lat=10.7000"))).andExpect(requestTo(containsString("lon=106.7000")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        met.expect(requestTo(containsString("lat=10.7100")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = service.assessRoute(locker(3L, 10.70, 106.70), locker(5L, 10.71, 106.71));

        assertTrue(result.safe(), result.message());
        assertEquals("SAFE", result.status());
        assertEquals(DroneWeatherService.MET_NORWAY, result.provider());
        assertEquals(18.0, result.maxWindSpeedKmh(), 0.01);
        assertEquals(27.0, result.maxWindGustKmh(), 0.01);
        assertEquals(27.5, result.temperatureC());
        assertEquals("Có mây", result.condition());
        open.verify();
        met.verify();

        // Cùng tuyến trong 5 phút: dùng kết quả đã nhớ, không gọi lại API nào.
        var again = service.assessRoute(locker(3L, 10.70, 106.70), locker(5L, 10.71, 106.71));
        assertEquals(result, again);
    }

    @Test
    void metNorwayRainShowersMakeTheRouteUnsafe() throws Exception {
        RestClient.Builder openBuilder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer open = MockRestServiceServer.bindTo(openBuilder).build();
        RestClient.Builder metBuilder = RestClient.builder().baseUrl("https://met.test/complete");
        MockRestServiceServer met = MockRestServiceServer.bindTo(metBuilder).build();
        DroneWeatherService service = new DroneWeatherService(
                openBuilder.build(), metBuilder.build(), 30, 40, 1500, null, java.time.Clock.systemUTC());
        open.expect(requestTo(containsString("minutely_15="))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        OffsetDateTime hour = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        met.expect(requestTo(containsString("lat=10.7000"))).andRespond(withSuccess(
                JSON.writeValueAsString(metForecast(hour, 2.0, 1.2, "lightrainshowers_day", 26.0)),
                MediaType.APPLICATION_JSON));

        // Thời tiết tại một tủ: một điểm, một request.
        var result = service.assessLocker(locker(3L, 10.70, 106.70));

        assertFalse(result.safe());
        assertEquals("UNSAFE", result.status());
        assertEquals("Mưa rào", result.condition());
        assertTrue(result.message().contains("mưa hoặc dông"));
        met.verify();
    }

    @Test
    void bothProvidersDownMeansUnavailableAndIsNotCached() {
        RestClient.Builder openBuilder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer open = MockRestServiceServer.bindTo(openBuilder).build();
        RestClient.Builder metBuilder = RestClient.builder().baseUrl("https://met.test/complete");
        MockRestServiceServer met = MockRestServiceServer.bindTo(metBuilder).build();
        DroneWeatherService service = new DroneWeatherService(
                openBuilder.build(), metBuilder.build(), 30, 40, 1500, null, java.time.Clock.systemUTC());
        open.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo(containsString("minutely_15=")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        met.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo(containsString("lat=")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertEquals("UNAVAILABLE", service.assessLocker(locker(3L, 10.70, 106.70)).status());
        // Lỗi không được nhớ: lần sau thử lại cả hai nguồn.
        assertEquals("UNAVAILABLE", service.assessLocker(locker(3L, 10.70, 106.70)).status());
        open.verify();
        met.verify();
    }

    private static Map<String, Object> metForecast(
            OffsetDateTime hour, double windMs, double rainMm, String symbol, double temperature) {
        Map<String, Object> entry = Map.of(
                "time", hour.toString(),
                "data", Map.of(
                        "instant", Map.of("details", Map.of(
                                "wind_speed", windMs, "air_temperature", temperature, "fog_area_fraction", 0.0)),
                        "next_1_hours", Map.of(
                                "summary", Map.of("symbol_code", symbol),
                                "details", Map.of("precipitation_amount", rainMm))));
        Map<String, Object> past = Map.of(
                "time", hour.minusHours(3).toString(),
                "data", Map.of("instant", Map.of("details", Map.of("wind_speed", 40.0))));
        return Map.of("properties", Map.of("timeseries", List.of(past, entry)));
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

    /// Ngưỡng do admin sửa có hiệu lực ngay: lần kiểm tra sau dùng ngưỡng mới, không trả kết quả đã nhớ theo ngưỡng cũ.
    @Test
    void adminWeatherRulesApplyWithoutRestart() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var settings = com.huynqb.laundrylocker.order.settings.TestOrderRules.settings(Map.of());
        var rules = new com.huynqb.laundrylocker.order.settings.OrderRules(settings);
        DroneWeatherService service = new DroneWeatherService(
                builder.build(), null, rules::weatherLimits, null, java.time.Clock.systemUTC());
        OffsetDateTime first = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2).truncatedTo(ChronoUnit.MINUTES);
        List<String> times = List.of(first.toString(), first.plusMinutes(15).toString(), first.plusMinutes(30).toString(),
                first.plusMinutes(45).toString());
        // Mưa nhỏ 0,2 mm (mã 61) suốt tuyến, gió nhẹ.
        Map<String, Object> drizzlePoint = point(times, List.of(0.2, 0.2, 0.2, 0.2), List.of(61, 61, 61, 61),
                List.of(8, 8, 8, 8), List.of(12, 12, 12, 12), List.of(10000, 10000, 10000, 10000));
        // Tuyến ngắn vẫn lấy mẫu hai đầu: 2 điểm.
        String drizzle = JSON.writeValueAsString(List.of(drizzlePoint, drizzlePoint));
        server.expect(requestTo(containsString("forecast_minutely_15=3")))
                .andRespond(withSuccess(drizzle, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("forecast_minutely_15=4")))
                .andRespond(withSuccess(drizzle, MediaType.APPLICATION_JSON));

        var strict = service.assessRoute(locker(3L, 10.70, 106.70), locker(5L, 10.70, 106.70));
        assertEquals("UNSAFE", strict.status(), "mặc định: có mưa là không bay");
        assertEquals(30, strict.windowMinutes());

        settings.update(Map.of(
                "app.drone.weather.max-precipitation-mm", "0.5",
                "app.drone.weather.block-precipitation-codes", false,
                "app.drone.weather.forecast-window-minutes", 45), null);
        var relaxed = service.assessRoute(locker(3L, 10.70, 106.70), locker(5L, 10.70, 106.70));

        assertTrue(relaxed.safe(), relaxed.message());
        assertEquals(45, relaxed.windowMinutes());
        assertTrue(relaxed.message().contains("45 phút"));
        server.verify();
    }

    /// Admin cho bay khi không có dữ liệu: vẫn báo UNAVAILABLE để đội bay biết, nhưng không chặn.
    @Test
    void missingForecastOnlyBlocksWhenAdminRequiresIt() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://weather.test/v1/forecast");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var settings = com.huynqb.laundrylocker.order.settings.TestOrderRules.settings(
                Map.of("app.drone.weather.block-when-unavailable", false));
        var rules = new com.huynqb.laundrylocker.order.settings.OrderRules(settings);
        DroneWeatherService service = new DroneWeatherService(
                builder.build(), null, rules::weatherLimits, null, java.time.Clock.systemUTC());
        server.expect(requestTo(containsString("minutely_15="))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        var result = service.assessRoute(locker(3L, 10.70, 106.70), locker(5L, 10.71, 106.71));

        assertEquals("UNAVAILABLE", result.status());
        assertTrue(result.safe());
        assertTrue(service.unavailable(OffsetDateTime.now(ZoneOffset.UTC)).safe());
        settings.update(Map.of("app.drone.weather.block-when-unavailable", true), null);
        assertFalse(service.unavailable(OffsetDateTime.now(ZoneOffset.UTC)).safe());
    }
}
