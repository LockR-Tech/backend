package com.huynqb.laundrylocker.order.dto;

import java.time.OffsetDateTime;

/** Weather safety result for the next 30 minutes along a drone route. */
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
        int worstWeatherCode) {

    public static DroneWeatherAssessment unavailable(OffsetDateTime checkedAt) {
        return new DroneWeatherAssessment(false, "UNAVAILABLE",
                "Không thể xác nhận dự báo thời tiết tuyến bay. Vui lòng thử lại sau.",
                checkedAt, 30, 0, 0, 0, 0, -1);
    }
}
