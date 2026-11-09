package com.huynqb.laundrylocker.order.dto;

import java.time.OffsetDateTime;

/**
 * Weather safety result for the next 30 minutes along a drone route.
 *
 * @param provider     nguồn dự báo đã trả lời: OPEN_METEO, MET_NORWAY; null khi không nguồn nào trả lời
 * @param temperatureC nhiệt độ hiện tại ở điểm đầu tuyến (null khi nguồn không trả)
 * @param condition    mô tả ngắn tiếng Việt của thời tiết hiện tại ("Nhiều mây", "Mưa rào"…)
 * @param checkDisabled admin đang tắt kiểm tra thời tiết (thử nghiệm): `status`, số liệu là dự báo thật nhưng
 *                      `safe` luôn true — không chặn đặt đơn, phóng drone, không dừng trạm
 */
public record DroneWeatherAssessment(
        boolean safe,
        String status,
        String message,
        OffsetDateTime checkedAt,
        int windowMinutes,
        int sampledRoutePoints,
        double maxWindSpeedKmh,
        double maxWindGustKmh,
        double maxPrecipitationMm,
        int worstWeatherCode,
        String provider,
        Double temperatureC,
        String condition,
        boolean checkDisabled) {

    public static final String CHECK_DISABLED_MESSAGE =
            "Admin đang tắt kiểm tra thời tiết để thử nghiệm: dự báo chỉ để tham khảo, không chặn đặt đơn và phóng drone.";

    public DroneWeatherAssessment(
            boolean safe,
            String status,
            String message,
            OffsetDateTime checkedAt,
            int windowMinutes,
            int sampledRoutePoints,
            double maxWindSpeedKmh,
            double maxWindGustKmh,
            double maxPrecipitationMm,
            int worstWeatherCode,
            String provider,
            Double temperatureC,
            String condition) {
        this(safe, status, message, checkedAt, windowMinutes, sampledRoutePoints, maxWindSpeedKmh, maxWindGustKmh,
                maxPrecipitationMm, worstWeatherCode, provider, temperatureC, condition, false);
    }

    public DroneWeatherAssessment(
            boolean safe,
            String status,
            String message,
            OffsetDateTime checkedAt,
            int windowMinutes,
            int sampledRoutePoints,
            double maxWindSpeedKmh,
            double maxWindGustKmh,
            double maxPrecipitationMm,
            int worstWeatherCode) {
        this(safe, status, message, checkedAt, windowMinutes, sampledRoutePoints, maxWindSpeedKmh, maxWindGustKmh,
                maxPrecipitationMm, worstWeatherCode, null, null, null, false);
    }

    public static DroneWeatherAssessment unavailable(OffsetDateTime checkedAt) {
        return unavailable(checkedAt, 30, true);
    }

    /// `blocks` = false khi admin cho bay lúc không có dữ liệu thời tiết: vẫn báo UNAVAILABLE nhưng `safe`.
    public static DroneWeatherAssessment unavailable(OffsetDateTime checkedAt, int windowMinutes, boolean blocks) {
        return new DroneWeatherAssessment(!blocks, "UNAVAILABLE",
                blocks
                        ? "Không thể xác nhận dự báo thời tiết tuyến bay. Vui lòng thử lại sau."
                        : "Chưa lấy được dự báo thời tiết. Đội bay tự quan sát thời tiết trước khi phóng.",
                checkedAt, windowMinutes, 0, 0, 0, 0, -1);
    }

    public boolean unavailable() {
        return "UNAVAILABLE".equals(status);
    }

    public DroneWeatherAssessment withSource(String provider, Double temperatureC, String condition) {
        return new DroneWeatherAssessment(safe, status, message, checkedAt, windowMinutes, sampledRoutePoints,
                maxWindSpeedKmh, maxWindGustKmh, maxPrecipitationMm, worstWeatherCode, provider, temperatureC,
                condition, checkDisabled);
    }

    /// Admin tắt kiểm tra thời tiết: giữ dự báo thật để hiển thị (status, gió, mưa…) nhưng cho bay.
    public DroneWeatherAssessment withCheckDisabled() {
        return new DroneWeatherAssessment(true, status, CHECK_DISABLED_MESSAGE, checkedAt, windowMinutes,
                sampledRoutePoints, maxWindSpeedKmh, maxWindGustKmh, maxPrecipitationMm, worstWeatherCode, provider,
                temperatureC, condition, true);
    }
}
