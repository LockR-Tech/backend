package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;

public record DroneDeliveryQuoteResponse(
        /// Tủ có bãi đáp gần điểm lấy hàng nhất mà còn phục vụ được — nơi drone cất cánh.
        DroneLockerPointResponse sourceLocker,
        DroneLockerPointResponse destinationLocker,
        /// Điểm lấy hàng → tủ gửi, mét.
        int pickupDistanceMeters,
        /// Tủ gửi → tủ nhận, mét.
        int routeDistanceMeters,
        BigDecimal fee,
        DroneWeatherAssessment weather) {
}
