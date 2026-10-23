package com.huynqb.laundrylocker.order.dto;

/// Thời tiết hiển thị cho khách, KTV và admin — cùng một nguồn với kiểm tra lúc đặt đơn / phóng drone.
public final class DroneWeatherViews {

    private DroneWeatherViews() {
    }

    /// Thời tiết dọc tuyến bay của một đơn (tủ bay → tủ nhận).
    public record RouteWeather(
            Long orderId,
            Long sourceLockerId,
            String sourceLockerName,
            Long destinationLockerId,
            String destinationLockerName,
            DroneWeatherAssessment weather) {
    }

    /// Thời tiết tại một tủ có bãi đáp drone.
    public record LockerWeather(
            Long lockerId,
            String code,
            String name,
            String address,
            Double latitude,
            Double longitude,
            DroneWeatherAssessment weather) {
    }
}
