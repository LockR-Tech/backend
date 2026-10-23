package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Checks weather along the source-locker to destination-locker route using Open-Meteo. */
@Slf4j
@Service
public class DroneWeatherService {

    private static final int WINDOW_MINUTES = 30;
    private static final double MAX_SAMPLE_SPACING_METERS = 5_000;
    private static final int MAX_ROUTE_POINTS = 12;

    private final RestClient restClient;
    private final double maxWindSpeedKmh;
    private final double maxWindGustKmh;
    private final double minVisibilityMeters;
    private final String apiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public DroneWeatherService(
            RestClient.Builder builder,
            @Value("${app.drone.weather.api-url:https://api.open-meteo.com/v1/forecast}") String apiUrl,
            @Value("${app.drone.weather.api-key:}") String apiKey,
            @Value("${app.drone.weather.max-wind-speed-kmh:30}") double maxWindSpeedKmh,
            @Value("${app.drone.weather.max-wind-gust-kmh:40}") double maxWindGustKmh,
            @Value("${app.drone.weather.min-visibility-meters:1500}") double minVisibilityMeters) {
        this(createClient(builder, apiUrl), maxWindSpeedKmh, maxWindGustKmh, minVisibilityMeters, apiKey);
    }

    DroneWeatherService(
            RestClient restClient,
            double maxWindSpeedKmh,
            double maxWindGustKmh,
            double minVisibilityMeters) {
        this(restClient, maxWindSpeedKmh, maxWindGustKmh, minVisibilityMeters, null);
    }

    private DroneWeatherService(
            RestClient restClient,
            double maxWindSpeedKmh,
            double maxWindGustKmh,
            double minVisibilityMeters,
            String apiKey) {
        this.restClient = restClient;
        this.maxWindSpeedKmh = maxWindSpeedKmh;
        this.maxWindGustKmh = maxWindGustKmh;
        this.minVisibilityMeters = minVisibilityMeters;
        this.apiKey = apiKey;
    }

    private static RestClient createClient(RestClient.Builder builder, String apiUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(8));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        return builder.requestFactory(requestFactory).baseUrl(apiUrl).build();
    }

    public DroneWeatherAssessment assessRoute(LockerInfo source, LockerInfo destination) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            List<RoutePoint> points = routePoints(source, destination);
            String latitudes = points.stream().map(point -> Double.toString(point.latitude())).reduce((a, b) -> a + "," + b).orElseThrow();
            String longitudes = points.stream().map(point -> Double.toString(point.longitude())).reduce((a, b) -> a + "," + b).orElseThrow();

            JsonNode response = restClient.get()
                    .uri(uri -> uri
                            .queryParam("latitude", latitudes)
                            .queryParam("longitude", longitudes)
                            .queryParam("minutely_15", "precipitation,weather_code,wind_speed_10m,wind_gusts_10m,visibility")
                            .queryParam("forecast_minutely_15", 3)
                            .queryParam("timezone", "UTC")
                            .queryParamIfPresent("apikey", Optional.ofNullable(apiKey).filter(key -> !key.isBlank()))
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return assessResponse(response, points.size(), now);
        } catch (Exception exception) {
            if (exception instanceof RestClientResponseException responseException) {
                log.warn("Open-Meteo weather check returned HTTP {}", responseException.getStatusCode().value());
            } else {
                Throwable cause = exception.getCause();
                log.warn("Open-Meteo weather check failed ({}{})",
                        exception.getClass().getSimpleName(),
                        cause == null ? "" : ", cause=" + cause.getClass().getSimpleName());
            }
            return DroneWeatherAssessment.unavailable(now);
        }
    }

    private DroneWeatherAssessment assessResponse(JsonNode response, int expectedPoints, OffsetDateTime checkedAt) {
        if (response == null || (response.isArray() && response.size() != expectedPoints)) {
            log.warn("Open-Meteo returned an unexpected response shape (expected route points={}, actual={})",
                    expectedPoints, response == null ? "null" : response.isArray() ? response.size() : "object");
            return DroneWeatherAssessment.unavailable(checkedAt);
        }
        double maximumWind = 0;
        double maximumGust = 0;
        double maximumPrecipitation = 0;
        double minimumVisibility = Double.MAX_VALUE;
        int worstCode = 0;
        boolean unsafe = false;
        boolean sawForecast = false;

        for (int pointIndex = 0; pointIndex < expectedPoints; pointIndex++) {
            JsonNode point = response.isArray() ? response.get(pointIndex) : response;
            JsonNode forecast = point.path("minutely_15");
            JsonNode times = forecast.path("time");
            JsonNode precipitation = forecast.path("precipitation");
            JsonNode weatherCodes = forecast.path("weather_code");
            JsonNode winds = forecast.path("wind_speed_10m");
            JsonNode gusts = forecast.path("wind_gusts_10m");
            JsonNode visibility = forecast.path("visibility");
            if (!times.isArray() || !precipitation.isArray() || !weatherCodes.isArray()
                    || !winds.isArray() || !gusts.isArray() || !visibility.isArray()) {
                log.warn("Open-Meteo response is missing minutely_15 fields at route point {}", pointIndex);
                return DroneWeatherAssessment.unavailable(checkedAt);
            }
            for (int i = 0; i < times.size(); i++) {
                OffsetDateTime forecastAt = parseForecastTime(times.get(i).asText());
                if (forecastAt.isBefore(checkedAt.minusMinutes(1))
                        || forecastAt.isAfter(checkedAt.plusMinutes(WINDOW_MINUTES))) {
                    continue;
                }
                if (i >= precipitation.size() || i >= weatherCodes.size() || i >= winds.size()
                        || i >= gusts.size() || i >= visibility.size()
                        || anyMissing(precipitation.get(i), weatherCodes.get(i), winds.get(i), gusts.get(i), visibility.get(i))) {
                    log.warn("Open-Meteo response has missing weather values at route point {}, sample {}", pointIndex, i);
                    return DroneWeatherAssessment.unavailable(checkedAt);
                }
                sawForecast = true;
                double rain = precipitation.get(i).asDouble();
                int code = weatherCodes.get(i).asInt();
                double wind = winds.get(i).asDouble();
                double gust = gusts.get(i).asDouble();
                double visible = visibility.get(i).asDouble();
                maximumPrecipitation = Math.max(maximumPrecipitation, rain);
                maximumWind = Math.max(maximumWind, wind);
                maximumGust = Math.max(maximumGust, gust);
                minimumVisibility = Math.min(minimumVisibility, visible);
                if (isRainOrStorm(code) || rain > 0 || wind > maxWindSpeedKmh || gust > maxWindGustKmh
                        || visible < minVisibilityMeters) {
                    unsafe = true;
                    worstCode = code;
                }
            }
        }
        if (!sawForecast) {
            log.warn("Open-Meteo returned no samples in the 30-minute route window (points={}, checkedAt={})",
                    expectedPoints, checkedAt);
            return DroneWeatherAssessment.unavailable(checkedAt);
        }
        String message = unsafe
                ? unsafeMessage(maximumPrecipitation, maximumWind, maximumGust, minimumVisibility, worstCode,
                        maxWindSpeedKmh, maxWindGustKmh, minVisibilityMeters)
                : "Dự báo tuyến bay an toàn trong 30 phút tới.";
        return new DroneWeatherAssessment(!unsafe, unsafe ? "UNSAFE" : "SAFE", message, checkedAt,
                WINDOW_MINUTES, expectedPoints, maximumWind, maximumGust, maximumPrecipitation, worstCode);
    }

    private List<RoutePoint> routePoints(LockerInfo source, LockerInfo destination) {
        if (source.latitude() == null || source.longitude() == null
                || destination.latitude() == null || destination.longitude() == null) {
            throw new IllegalArgumentException("Route locker coordinates are missing");
        }
        double distance = distanceMeters(source.latitude(), source.longitude(), destination.latitude(), destination.longitude());
        int segments = Math.max(1, Math.min(MAX_ROUTE_POINTS - 1, (int) Math.ceil(distance / MAX_SAMPLE_SPACING_METERS)));
        List<RoutePoint> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double progress = (double) i / segments;
            points.add(new RoutePoint(
                    source.latitude() + (destination.latitude() - source.latitude()) * progress,
                    source.longitude() + (destination.longitude() - source.longitude()) * progress));
        }
        return points;
    }

    private static boolean anyMissing(JsonNode... values) {
        for (JsonNode value : values) {
            if (value == null || value.isNull() || !value.isNumber()) return true;
        }
        return false;
    }

    /** Open-Meteo returns UTC ISO timestamps without an offset when timezone=UTC. */
    private static OffsetDateTime parseForecastTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        }
    }

    private static boolean isRainOrStorm(int weatherCode) {
        return (weatherCode >= 51 && weatherCode <= 99);
    }

    private static String unsafeMessage(
            double precipitation, double wind, double gust, double visibility, int code,
            double windLimit, double gustLimit, double visibilityLimit) {
        List<String> reasons = new ArrayList<>();
        if (precipitation > 0 || isRainOrStorm(code)) reasons.add("có mưa hoặc dông");
        if (wind > windLimit) reasons.add(String.format(Locale.ROOT, "gió %.0f km/h", wind));
        if (gust > gustLimit) reasons.add(String.format(Locale.ROOT, "gió giật %.0f km/h", gust));
        if (visibility < visibilityLimit) reasons.add(String.format(Locale.ROOT, "tầm nhìn %.0f m", visibility));
        if (reasons.isEmpty()) reasons.add("điều kiện khí tượng vượt ngưỡng vận hành");
        return "Không thể đặt chuyến bay: " + String.join(", ", reasons) + " trên tuyến trong 30 phút tới.";
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record RoutePoint(double latitude, double longitude) {}
}
