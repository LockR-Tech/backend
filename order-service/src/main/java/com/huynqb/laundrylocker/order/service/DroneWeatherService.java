package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dự báo thời tiết 30 phút tới dọc tuyến bay (tủ bay → tủ nhận) để quyết định có được bay không.
 *
 * <p>Hai nguồn dữ liệu mở, không cần khoá:
 * <ol>
 *   <li><b>Open-Meteo</b> — dự báo 15 phút (mưa, mã thời tiết, gió, gió giật, tầm nhìn);</li>
 *   <li><b>MET Norway</b> (api.met.no) — dự phòng khi Open-Meteo lỗi / quá hạn / bị giới hạn lượt gọi.
 *       Dự báo theo giờ; vùng Việt Nam không có gió giật nên ước tính bằng 1,5 × tốc độ gió (thận trọng),
 *       tầm nhìn suy từ tỉ lệ sương mù.</li>
 * </ol>
 * Kết quả có dữ liệu được nhớ 5 phút theo tuyến: khách, KTV và admin xem cùng một tuyến không gọi API lặp lại.
 * Không nguồn nào trả lời ⇒ UNAVAILABLE; mặc định vẫn chặn đặt đơn và phóng drone (không bay khi không biết trời),
 * trừ khi admin tắt "Chặn bay khi không có dữ liệu thời tiết".
 *
 * <p>Ngưỡng (gió, gió giật, tầm nhìn, lượng mưa, mã mưa/dông, cửa sổ dự báo) đọc từ {@link OrderRules} mỗi lần
 * đánh giá: admin sửa trên trang Thời tiết & bay là có hiệu lực, không cần khởi động lại.
 */
@Slf4j
@Service
public class DroneWeatherService {

    public static final String OPEN_METEO = "OPEN_METEO";
    public static final String MET_NORWAY = "MET_NORWAY";

    private static final double MAX_SAMPLE_SPACING_METERS = 5_000;
    private static final int MAX_ROUTE_POINTS = 12;
    /// MET Norway: mỗi điểm một request — chỉ lấy đầu, giữa, cuối tuyến.
    private static final int MET_MAX_POINTS = 3;
    private static final double MET_GUST_FACTOR = 1.5;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient openMeteo;
    private final RestClient metNorway;
    private final Supplier<OrderRules.WeatherLimits> limits;
    private final String apiKey;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public DroneWeatherService(
            RestClient.Builder builder,
            @Value("${app.drone.weather.api-url:https://api.open-meteo.com/v1/forecast}") String apiUrl,
            @Value("${app.drone.weather.api-key:}") String apiKey,
            @Value("${app.drone.weather.fallback-url:https://api.met.no/weatherapi/locationforecast/2.0/complete}")
            String fallbackUrl,
            @Value("${app.drone.weather.user-agent:LockR-DroneDelivery/1.0 (+https://github.com/LockR-Tech)}")
            String userAgent,
            OrderRules rules) {
        this(createClient(builder.clone(), apiUrl, null),
                fallbackUrl == null || fallbackUrl.isBlank() ? null : createClient(builder.clone(), fallbackUrl, userAgent),
                rules::weatherLimits, apiKey, Clock.systemUTC());
    }

    /// Chỉ Open-Meteo, không dự phòng, ngưỡng cố định (test).
    DroneWeatherService(RestClient restClient, double maxWindSpeedKmh, double maxWindGustKmh, double minVisibilityMeters) {
        this(restClient, null, maxWindSpeedKmh, maxWindGustKmh, minVisibilityMeters, null, Clock.systemUTC());
    }

    DroneWeatherService(
            RestClient openMeteo,
            RestClient metNorway,
            double maxWindSpeedKmh,
            double maxWindGustKmh,
            double minVisibilityMeters,
            String apiKey,
            Clock clock) {
        this(openMeteo, metNorway, fixedLimits(maxWindSpeedKmh, maxWindGustKmh, minVisibilityMeters), apiKey, clock);
    }

    DroneWeatherService(
            RestClient openMeteo,
            RestClient metNorway,
            Supplier<OrderRules.WeatherLimits> limits,
            String apiKey,
            Clock clock) {
        this.openMeteo = openMeteo;
        this.metNorway = metNorway;
        this.limits = limits;
        this.apiKey = apiKey;
        this.clock = clock;
    }

    /// Ngưỡng mặc định của catalog với gió, gió giật, tầm nhìn cho trước.
    static Supplier<OrderRules.WeatherLimits> fixedLimits(double maxWind, double maxGust, double minVisibility) {
        OrderRules.WeatherLimits fixed = new OrderRules.WeatherLimits(maxWind, maxGust, minVisibility, 0, true, 30, true);
        return () -> fixed;
    }

    /// Không nguồn nào trả lời: chặn hay không theo cấu hình admin.
    public DroneWeatherAssessment unavailable(OffsetDateTime checkedAt) {
        OrderRules.WeatherLimits current = limits.get();
        return DroneWeatherAssessment.unavailable(checkedAt, current.windowMinutes(), current.blockWhenUnavailable());
    }

    private static RestClient createClient(RestClient.Builder builder, String url, String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(6));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder configured = builder.requestFactory(requestFactory).baseUrl(url);
        if (userAgent != null && !userAgent.isBlank()) {
            // api.met.no từ chối request không có User-Agent định danh ứng dụng (403).
            configured = configured.defaultHeader("User-Agent", userAgent);
        }
        return configured.build();
    }

    public DroneWeatherAssessment assessRoute(LockerInfo source, LockerInfo destination) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        try {
            return assess(routePoints(source, destination), now);
        } catch (RuntimeException missingCoordinates) {
            log.warn("Drone weather check skipped: {}", missingCoordinates.getMessage());
            return unavailable(now);
        }
    }

    /**
     * Cả hành trình của một đơn trong 30 phút giao dự kiến: từ nơi gửi (điểm KTV lấy hàng) tới tủ bay,
     * rồi tủ bay tới tủ nhận. Không có toạ độ nơi gửi thì chỉ xét tuyến bay.
     */
    public DroneWeatherAssessment assessJourney(
            Double pickupLatitude, Double pickupLongitude, LockerInfo source, LockerInfo destination) {
        if (pickupLatitude == null || pickupLongitude == null) {
            return assessRoute(source, destination);
        }
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        try {
            List<RoutePoint> points = new ArrayList<>();
            points.add(new RoutePoint(pickupLatitude, pickupLongitude));
            points.addAll(routePoints(source, destination));
            return assess(points, now);
        } catch (RuntimeException missingCoordinates) {
            log.warn("Drone weather check skipped: {}", missingCoordinates.getMessage());
            return unavailable(now);
        }
    }

    private DroneWeatherAssessment assess(List<RoutePoint> points, OffsetDateTime now) {
        OrderRules.WeatherLimits current = limits.get();
        // Ngưỡng nằm trong khoá: admin vừa sửa ngưỡng thì không trả lại kết quả tính theo ngưỡng cũ.
        String key = String.join(">", points.stream().map(RoutePoint::key).distinct().toList()) + "|" + current;
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.assessment();
        }

        DroneWeatherAssessment result = fromOpenMeteo(points, now, current);
        if (result.unavailable() && metNorway != null) {
            result = fromMetNorway(points, now, current);
        }
        if (!result.unavailable()) {
            cache.put(key, new Cached(result, now.plus(CACHE_TTL)));
        }
        return result;
    }

    /// Thời tiết tại một tủ (bãi đáp): tuyến một điểm.
    public DroneWeatherAssessment assessLocker(LockerInfo locker) {
        return assessRoute(locker, locker);
    }

    // ------------------------------------------------------------------ Open-Meteo

    private DroneWeatherAssessment fromOpenMeteo(List<RoutePoint> points, OffsetDateTime now, OrderRules.WeatherLimits limits) {
        try {
            String latitudes = join(points.stream().map(RoutePoint::latitude).toList());
            String longitudes = join(points.stream().map(RoutePoint::longitude).toList());
            // Mẫu 15 phút: mẫu đang diễn ra + đủ mẫu phủ cửa sổ dự báo.
            int samples = (limits.windowMinutes() + 14) / 15 + 1;
            JsonNode response = openMeteo.get()
                    .uri(uri -> uri
                            .queryParam("latitude", latitudes)
                            .queryParam("longitude", longitudes)
                            .queryParam("minutely_15",
                                    "precipitation,weather_code,wind_speed_10m,wind_gusts_10m,visibility,temperature_2m")
                            .queryParam("forecast_minutely_15", samples)
                            .queryParam("timezone", "UTC")
                            .queryParamIfPresent("apikey", Optional.ofNullable(apiKey).filter(key -> !key.isBlank()))
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return assessOpenMeteo(response, points.size(), now, limits);
        } catch (Exception exception) {
            logFailure("Open-Meteo", exception);
            return unavailable(now);
        }
    }

    private DroneWeatherAssessment assessOpenMeteo(
            JsonNode response, int expectedPoints, OffsetDateTime checkedAt, OrderRules.WeatherLimits limits) {
        if (response == null || (response.isArray() && response.size() != expectedPoints)) {
            log.warn("Open-Meteo returned an unexpected response shape (expected route points={}, actual={})",
                    expectedPoints, response == null ? "null" : response.isArray() ? response.size() : "object");
            return unavailable(checkedAt);
        }
        Accumulator acc = new Accumulator(limits);
        for (int pointIndex = 0; pointIndex < expectedPoints; pointIndex++) {
            JsonNode point = response.isArray() ? response.get(pointIndex) : response;
            JsonNode forecast = point.path("minutely_15");
            JsonNode times = forecast.path("time");
            JsonNode precipitation = forecast.path("precipitation");
            JsonNode weatherCodes = forecast.path("weather_code");
            JsonNode winds = forecast.path("wind_speed_10m");
            JsonNode gusts = forecast.path("wind_gusts_10m");
            JsonNode visibility = forecast.path("visibility");
            JsonNode temperatures = forecast.path("temperature_2m");
            if (!times.isArray() || !precipitation.isArray() || !weatherCodes.isArray()
                    || !winds.isArray() || !gusts.isArray() || !visibility.isArray()) {
                log.warn("Open-Meteo response is missing minutely_15 fields at route point {}", pointIndex);
                return unavailable(checkedAt);
            }
            for (int i = 0; i < times.size(); i++) {
                OffsetDateTime forecastAt = parseForecastTime(times.get(i).asText());
                if (!inWindow(forecastAt, checkedAt, 1, limits.windowMinutes())) {
                    continue;
                }
                if (i >= precipitation.size() || i >= weatherCodes.size() || i >= winds.size()
                        || i >= gusts.size() || i >= visibility.size()
                        || anyMissing(precipitation.get(i), weatherCodes.get(i), winds.get(i), gusts.get(i), visibility.get(i))) {
                    log.warn("Open-Meteo response has missing weather values at route point {}, sample {}", pointIndex, i);
                    return unavailable(checkedAt);
                }
                Double temperature = temperatures.isArray() && i < temperatures.size() && temperatures.get(i).isNumber()
                        ? temperatures.get(i).asDouble() : null;
                int code = weatherCodes.get(i).asInt();
                acc.add(precipitation.get(i).asDouble(), code, winds.get(i).asDouble(), gusts.get(i).asDouble(),
                        visibility.get(i).asDouble(), temperature, conditionFromWmo(code));
            }
        }
        if (!acc.sawForecast) {
            log.warn("Open-Meteo returned no samples in the 30-minute route window (points={}, checkedAt={})",
                    expectedPoints, checkedAt);
            return unavailable(checkedAt);
        }
        return acc.result(checkedAt, expectedPoints, OPEN_METEO);
    }

    // ------------------------------------------------------------------ MET Norway (dự phòng)

    private DroneWeatherAssessment fromMetNorway(
            List<RoutePoint> points, OffsetDateTime now, OrderRules.WeatherLimits limits) {
        List<RoutePoint> sampled = sampleForMet(points);
        Accumulator acc = new Accumulator(limits);
        try {
            for (RoutePoint point : sampled) {
                JsonNode response = metNorway.get()
                        .uri(uri -> uri
                                // api.met.no yêu cầu tối đa 4 chữ số thập phân.
                                .queryParam("lat", String.format(Locale.ROOT, "%.4f", point.latitude()))
                                .queryParam("lon", String.format(Locale.ROOT, "%.4f", point.longitude()))
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode series = response == null ? null : response.path("properties").path("timeseries");
                if (series == null || !series.isArray() || series.isEmpty()) {
                    log.warn("MET Norway returned no timeseries");
                    return unavailable(now);
                }
                boolean sawPoint = false;
                for (JsonNode entry : series) {
                    OffsetDateTime at = parseForecastTime(entry.path("time").asText());
                    // Bản ghi theo giờ: lấy giờ đang diễn ra (bắt đầu trong 60 phút trước) và giờ kế trong cửa sổ.
                    if (at.isBefore(now.minusMinutes(60)) || at.isAfter(now.plusMinutes(limits.windowMinutes()))) {
                        continue;
                    }
                    JsonNode details = entry.path("data").path("instant").path("details");
                    JsonNode next = entry.path("data").path("next_1_hours");
                    if (!details.path("wind_speed").isNumber()) {
                        continue;
                    }
                    double wind = details.path("wind_speed").asDouble() * 3.6;
                    double gust = details.path("wind_speed_of_gust").isNumber()
                            ? details.path("wind_speed_of_gust").asDouble() * 3.6
                            : wind * MET_GUST_FACTOR;
                    double fog = details.path("fog_area_fraction").asDouble(0);
                    double visibility = fog >= 50 ? 1_000 : 10_000;
                    double rain = next.path("details").path("precipitation_amount").asDouble(0);
                    String symbol = next.path("summary").path("symbol_code").asText("");
                    int code = wmoFromMetSymbol(symbol);
                    Double temperature = details.path("air_temperature").isNumber()
                            ? details.path("air_temperature").asDouble() : null;
                    acc.add(rain, code, wind, gust, visibility, temperature, conditionFromWmo(code));
                    sawPoint = true;
                }
                if (!sawPoint) {
                    log.warn("MET Norway returned no sample in the current window");
                    return unavailable(now);
                }
            }
        } catch (Exception exception) {
            logFailure("MET Norway", exception);
            return unavailable(now);
        }
        return acc.result(now, sampled.size(), MET_NORWAY);
    }

    private static List<RoutePoint> sampleForMet(List<RoutePoint> points) {
        List<RoutePoint> distinct = points.stream().distinct().toList();
        if (distinct.size() <= MET_MAX_POINTS) {
            return distinct;
        }
        return List.of(distinct.getFirst(), distinct.get(distinct.size() / 2), distinct.getLast());
    }

    /// Mã biểu tượng MET ("lightrainshowers_day", "rainandthunder"…) → mã WMO tương đương.
    static int wmoFromMetSymbol(String symbol) {
        String s = symbol == null ? "" : symbol.toLowerCase(Locale.ROOT);
        if (s.contains("thunder")) return 95;
        if (s.contains("sleet") || s.contains("snow")) return 71;
        if (s.contains("rainshowers")) return s.contains("heavy") ? 82 : 80;
        if (s.contains("rain")) return s.contains("heavy") ? 65 : s.contains("light") ? 61 : 63;
        if (s.startsWith("fog")) return 45;
        if (s.startsWith("cloudy")) return 3;
        if (s.startsWith("partlycloudy")) return 2;
        if (s.startsWith("fair")) return 1;
        if (s.startsWith("clearsky")) return 0;
        return 0;
    }

    // ------------------------------------------------------------------ chung

    /// Gộp mẫu dự báo dọc tuyến: lấy giá trị xấu nhất; nhiệt độ/mô tả lấy theo mẫu đầu tiên (đầu tuyến, gần nhất).
    private static final class Accumulator {
        final OrderRules.WeatherLimits limits;
        double maximumWind;
        double maximumGust;
        double maximumPrecipitation;
        double minimumVisibility = Double.MAX_VALUE;
        int worstCode;
        boolean unsafe;
        boolean sawForecast;
        Double temperature;
        String condition;

        Accumulator(OrderRules.WeatherLimits limits) {
            this.limits = limits;
        }

        void add(double rain, int code, double wind, double gust, double visible, Double temp, String label) {
            if (!sawForecast) {
                temperature = temp;
                condition = label;
            }
            sawForecast = true;
            maximumPrecipitation = Math.max(maximumPrecipitation, rain);
            maximumWind = Math.max(maximumWind, wind);
            maximumGust = Math.max(maximumGust, gust);
            minimumVisibility = Math.min(minimumVisibility, visible);
            // Mã WMO lớn hơn = thời tiết xấu hơn: giữ mã xấu nhất gặp trên tuyến, kể cả khi vẫn bay được.
            worstCode = Math.max(worstCode, code);
            if (rainBlocks(rain, code, limits) || wind > limits.maxWindSpeedKmh() || gust > limits.maxWindGustKmh()
                    || visibilityBlocks(visible, limits)) {
                unsafe = true;
            }
        }

        DroneWeatherAssessment result(OffsetDateTime checkedAt, int sampledPoints, String provider) {
            String message = unsafe
                    ? unsafeMessage(maximumPrecipitation, maximumWind, maximumGust, minimumVisibility, worstCode, limits)
                    : "Dự báo tuyến bay an toàn trong " + limits.windowMinutes() + " phút tới.";
            return new DroneWeatherAssessment(!unsafe, unsafe ? "UNSAFE" : "SAFE", message, checkedAt,
                    limits.windowMinutes(), sampledPoints, round1(maximumWind), round1(maximumGust),
                    round1(maximumPrecipitation), worstCode)
                    .withSource(provider, temperature, condition);
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static boolean inWindow(OffsetDateTime forecastAt, OffsetDateTime now, int graceMinutes, int windowMinutes) {
        return !forecastAt.isBefore(now.minusMinutes(graceMinutes)) && !forecastAt.isAfter(now.plusMinutes(windowMinutes));
    }

    private static void logFailure(String provider, Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            log.warn("{} weather check returned HTTP {}", provider, responseException.getStatusCode().value());
        } else {
            Throwable cause = exception.getCause();
            log.warn("{} weather check failed ({}{}): {}", provider, exception.getClass().getSimpleName(),
                    cause == null ? "" : ", cause=" + cause.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private static String join(List<Double> values) {
        return String.join(",", values.stream().map(value -> Double.toString(value)).toList());
    }

    private List<RoutePoint> routePoints(LockerInfo source, LockerInfo destination) {
        if (source == null || destination == null
                || source.latitude() == null || source.longitude() == null
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

    /** Open-Meteo returns UTC ISO timestamps without an offset when timezone=UTC; MET Norway adds "Z". */
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

    private static boolean rainBlocks(double precipitation, int code, OrderRules.WeatherLimits limits) {
        return precipitation > limits.maxPrecipitationMm() || (limits.blockPrecipitationCodes() && isRainOrStorm(code));
    }

    /// Ngưỡng tầm nhìn 0 = không xét tầm nhìn.
    private static boolean visibilityBlocks(double visibility, OrderRules.WeatherLimits limits) {
        return limits.minVisibilityMeters() > 0 && visibility < limits.minVisibilityMeters();
    }

    /// Mã WMO → mô tả tiếng Việt cho khách, KTV, admin.
    static String conditionFromWmo(int code) {
        if (code == 0) return "Trời quang";
        if (code == 1) return "Ít mây";
        if (code == 2) return "Có mây";
        if (code == 3) return "Nhiều mây";
        if (code == 45 || code == 48) return "Sương mù";
        if (code >= 51 && code <= 57) return "Mưa phùn";
        if (code >= 61 && code <= 67) return code >= 65 ? "Mưa to" : "Mưa";
        if (code >= 71 && code <= 77) return "Mưa tuyết";
        if (code >= 80 && code <= 82) return "Mưa rào";
        if (code >= 95) return "Dông";
        return "Không rõ";
    }

    private static String unsafeMessage(
            double precipitation, double wind, double gust, double visibility, int code, OrderRules.WeatherLimits limits) {
        List<String> reasons = new ArrayList<>();
        if (rainBlocks(precipitation, code, limits)) reasons.add("có mưa hoặc dông");
        if (wind > limits.maxWindSpeedKmh()) reasons.add(String.format(Locale.ROOT, "gió %.0f km/h", wind));
        if (gust > limits.maxWindGustKmh()) reasons.add(String.format(Locale.ROOT, "gió giật %.0f km/h", gust));
        if (visibilityBlocks(visibility, limits)) reasons.add(String.format(Locale.ROOT, "tầm nhìn %.0f m", visibility));
        if (reasons.isEmpty()) reasons.add("điều kiện khí tượng vượt ngưỡng vận hành");
        return "Thời tiết không cho phép bay: " + String.join(", ", reasons) + " trên tuyến trong "
                + limits.windowMinutes() + " phút tới.";
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

    private record RoutePoint(double latitude, double longitude) {
        String key() {
            return String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude);
        }
    }

    private record Cached(DroneWeatherAssessment assessment, OffsetDateTime expiresAt) {}
}
